package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.AssemblyEvent
import com.group9.asaa.classes.assembly.AssemblySystemStates
import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.Blueprint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

@Service
class AssemblyService : IAssemblyService, AutoCloseable {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val _systemState = MutableStateFlow(AssemblySystemStates.IDLE)
    override fun observeAssemblySystemState(): StateFlow<AssemblySystemStates> = _systemState
    override fun getAssemblySystemState(): AssemblySystemStates = _systemState.value

    private val _events = MutableSharedFlow<AssemblyEvent>(extraBufferCapacity = 256)
    fun observeEvents(): SharedFlow<AssemblyEvent> = _events.asSharedFlow()

    @Volatile private var currentConfirmation: MutableStateFlow<Boolean?>? = null
    @Volatile private var currentArrival: MutableStateFlow<Boolean>? = null
    @Volatile private var currentValidation: MutableStateFlow<ValidationOutcome?>? = null

    private val assemblyGate = Semaphore(1)

    private data class Enqueued(
        val blueprint: Blueprint,
        val demo: Boolean,
        val reply: CompletableDeferred<AssemblyTransportOrder>
    )

    private val queue = Channel<Enqueued>(capacity = 100)
    private val _queueSize = MutableStateFlow(0)
    override fun queueSize(): StateFlow<Int> = _queueSize

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
        val confirmationFlow = MutableStateFlow<Boolean?>(null).also { currentConfirmation = it }
        val transportArrivedFlow = MutableStateFlow(false).also { currentArrival = it }
        val validationFlow = MutableStateFlow<ValidationOutcome?>(null).also { currentValidation = it }

        val orderCreated = CompletableDeferred<AssemblyTransportOrder>()

        val ports = AssemblyPorts(
            sendOrder = { order -> orderCreated.complete(order) },
            awaitOrderConfirmation = { confirmationFlow.filterNotNull().first() },
            awaitTransportArrival = { transportArrivedFlow.filter { it }.first() },
            performAssemblyAndValidate = { validationFlow.filterNotNull().first() },
            notifyStatus = { state -> _events.tryEmit(AssemblyEvent(kind = "status", message = state.name)) },
            acquireAssemblyPermit = { assemblyGate.acquire() },
            releaseAssemblyPermit = { assemblyGate.release() }
        )

        val machine = AssemblyStateMachine(
            scope = scope,
            ports = ports,
            timeouts = AssemblyTimeouts(
                confirmationTimeout = 5.seconds,
                deliveryTimeout = 300.seconds,
                validationTimeout = 40.seconds
            )
        )

        val stateCollector = scope.launch {
            machine.state.collect { st ->
                _systemState.value = st
                _events.tryEmit(AssemblyEvent(kind = "state", state = st))
            }
        }
        val logCollector = scope.launch {
            machine.logs.collect { msg -> _events.tryEmit(AssemblyEvent(kind = "log", message = msg)) }
        }

        val perRunAutopilot = if (demo) {
            scope.launch {
                delay(2_000)
                confirmationFlow.value = true
                delay(20_000)
                transportArrivedFlow.value = true
                delay((10..20).random() * 1_000L)
                validationFlow.value = ValidationOutcome.VALID
            }
        } else null

        try {
            scope.launch { machine.run(orderBlueprint) }
            return orderCreated.await()
        } finally {
            perRunAutopilot?.cancel()
            stateCollector.cancel()
            logCollector.cancel()
            currentConfirmation = null
            currentArrival = null
            currentValidation = null
        }
    }

    override fun confirmCurrentOrder(accepted: Boolean) {
        currentConfirmation?.value = accepted
    }
    override fun signalTransportArrived() {
        currentArrival?.value = true
    }
    override fun validateAssembly(valid: Boolean) {
        currentValidation?.value = if (valid) ValidationOutcome.VALID else ValidationOutcome.INVALID
    }

    override fun demoAutopilot(
        acceptAfterMs: Long,
        deliverAfterMs: Long,
        validateAfterMs: Long,
        valid: Boolean
    ) { /* no-op: per-run autopilot handled inside runOne via demo flag */ }

    override fun close() {
        job.cancel()
    }
}
