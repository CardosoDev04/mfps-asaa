package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.AssemblyEvent
import com.group9.asaa.classes.assembly.AssemblySystemStates
import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.Blueprint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
class AssemblyService : IAssemblyService, AutoCloseable {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val _events = MutableSharedFlow<AssemblyEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun observeEvents(): SharedFlow<AssemblyEvent> = _events.asSharedFlow()

    private val confirmations = ConcurrentHashMap<String, MutableStateFlow<Boolean?>>()
    private val arrivals = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val validations = ConcurrentHashMap<String, MutableStateFlow<ValidationOutcome?>>()

    private val orderStates = ConcurrentHashMap<String, MutableStateFlow<AssemblySystemStates>>()

    private val assemblyGate = Semaphore(1)
    private val _overallSystemState = MutableStateFlow(AssemblySystemStates.IDLE)

    private data class Enqueued(
        val blueprint: Blueprint,
        val demo: Boolean,
        val reply: CompletableDeferred<AssemblyTransportOrder>
    )

    private val queue = Channel<Enqueued>(capacity = 100)
    private val _queueSize = MutableStateFlow(0)
    override fun queueSize(): StateFlow<Int> = _queueSize

    override fun observeAssemblySystemState(): StateFlow<AssemblySystemStates> =
        _overallSystemState.asStateFlow()

    override fun getAssemblySystemState(): AssemblySystemStates =
        _overallSystemState.value

    fun observeOrderSystemState(orderId: String): StateFlow<AssemblySystemStates> =
        orderStates.computeIfAbsent(orderId) { MutableStateFlow(AssemblySystemStates.IDLE) }

    fun getOrderSystemState(orderId: String): AssemblySystemStates =
        orderStates[orderId]?.value ?: AssemblySystemStates.IDLE

    init {
        scope.launch {
            for (req in queue) {
                try {
                    val created = runOne(req.blueprint, req.demo)
                    req.reply.complete(created)
                } catch (t: Throwable) {
                    req.reply.completeExceptionally(t)
                } finally {
                    _queueSize.update { (it - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    override suspend fun createOrder(orderBlueprint: Blueprint, demo: Boolean): AssemblyTransportOrder {
        val reply = CompletableDeferred<AssemblyTransportOrder>()
        val offered = queue.trySend(Enqueued(orderBlueprint, demo, reply))
        if (offered.isFailure) throw IllegalStateException("Order queue is full (100). Try again later.")
        _queueSize.update { it + 1 }
        return reply.await()
    }

    private suspend fun runOne(orderBlueprint: Blueprint, demo: Boolean): AssemblyTransportOrder {
        val orderId = "order-${UUID.randomUUID()}"
        val myState = orderStates.computeIfAbsent(orderId) { MutableStateFlow(AssemblySystemStates.IDLE) }

        val confirmationFlow = MutableStateFlow<Boolean?>(null)
        val transportArrivedFlow = MutableStateFlow(false)
        val validationFlow = MutableStateFlow<ValidationOutcome?>(null)

        val orderCreated = CompletableDeferred<AssemblyTransportOrder>()

        val ports = AssemblyPorts(
            sendOrder = { order ->
                val final = order.copy(orderId = orderId)
                confirmations[orderId] = confirmationFlow
                arrivals[orderId] = transportArrivedFlow
                validations[orderId] = validationFlow
                orderCreated.complete(final)
            },
            awaitOrderConfirmation = { confirmationFlow.filterNotNull().first() },
            awaitTransportArrival = { transportArrivedFlow.filter { it }.first() },
            performAssemblyAndValidate = { validationFlow.filterNotNull().first() },
            notifyStatus = { state ->
                _events.tryEmit(
                    AssemblyEvent(kind = "status", message = state.name, orderId = orderId)
                )
            },
            acquireAssemblyPermit = {
                assemblyGate.acquire()
                _overallSystemState.value = AssemblySystemStates.ASSEMBLING
            },
            releaseAssemblyPermit = {
                assemblyGate.release()
                _overallSystemState.value = AssemblySystemStates.IDLE
            }
        )

        val orderScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

        val machine = AssemblyStateMachine(
            scope = orderScope,
            ports = ports,
            timeouts = AssemblyTimeouts(
                confirmationTimeout = 5.seconds,
                deliveryTimeout = 300.seconds,
                validationTimeout = 40.seconds
            )
        )

        val perRunAutopilot = if (demo) {
            orderScope.launch {
                delay(2_000)
                confirmationFlow.value = true
                delay(20_000)
                transportArrivedFlow.value = true
                delay((10..20).random() * 1_000L)
                validationFlow.value = ValidationOutcome.VALID
            }
        } else null

        val machineJob = orderScope.launch {
            val stateCollectorReady = CompletableDeferred<Unit>()
            val logCollectorReady = CompletableDeferred<Unit>()

            val stateCollector = launch {
                machine.state
                    .onSubscription { stateCollectorReady.complete(Unit) }
                    .collect { st ->
                        myState.value = st
                        _events.tryEmit(AssemblyEvent(kind = "state", state = st, orderId = orderId))
                    }
            }

            val logCollector = launch {
                machine.logs
                    .onSubscription { logCollectorReady.complete(Unit) }
                    .collect { msg ->
                        _events.tryEmit(AssemblyEvent(kind = "log", message = msg, orderId = orderId))
                    }
            }

            stateCollectorReady.await()
            logCollectorReady.await()
            machine.run(orderBlueprint, orderId = orderId)
            stateCollector.cancelAndJoin()
            logCollector.cancelAndJoin()
        }

        machineJob.invokeOnCompletion {
            perRunAutopilot?.cancel()
            orderStates[orderId]?.value = machine.state.value
            confirmations.remove(orderId)
            arrivals.remove(orderId)
            validations.remove(orderId)
            orderScope.cancel()
        }

        return orderCreated.await()
    }

    override fun confirmOrder(orderId: String, accepted: Boolean) {
        confirmations[orderId]?.let { it.value = accepted }
    }

    override fun signalTransportArrived(orderId: String) {
        arrivals[orderId]?.let { it.value = true }
    }

    override fun validateAssembly(orderId: String, valid: Boolean) {
        validations[orderId]?.let { it.value = if (valid) ValidationOutcome.VALID else ValidationOutcome.INVALID }
    }

    override fun demoAutopilot(
        acceptAfterMs: Long,
        deliverAfterMs: Long,
        validateAfterMs: Long,
        valid: Boolean
    ) { }

    override fun close() {
        job.cancel()
    }
}
