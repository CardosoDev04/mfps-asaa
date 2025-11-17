package com.group9.asaa.assembly.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.assembly.IAssemblyMetricsRepository
import com.group9.asaa.classes.assembly.*
import com.group9.asaa.communication.service.kafka.ReceiveStage
import com.group9.asaa.misc.Locations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@Service
class AssemblyService(
    private val metricsRepo: IAssemblyMetricsRepository,
    private val receiveStage: ReceiveStage,
    private val mapper: ObjectMapper
) : IAssemblyService, AutoCloseable {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private val log = LoggerFactory.getLogger(javaClass)

    private val _events = MutableSharedFlow<AssemblyEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    fun observeEvents(): SharedFlow<AssemblyEvent> = _events.asSharedFlow()

    private val confirmations = ConcurrentHashMap<String, MutableStateFlow<Boolean?>>()
    private val arrivals = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val validations = ConcurrentHashMap<String, MutableStateFlow<AssemblyValidationOutcome?>>()

    private val orderStates = ConcurrentHashMap<String, MutableStateFlow<AssemblySystemStates>>()

    private val assemblyLines = listOf(
        Locations.ASSEMBLY_LINE_A,
        Locations.ASSEMBLY_LINE_B,
        Locations.ASSEMBLY_LINE_C
    )

    private val lineBusy = ConcurrentHashMap<Locations, MutableStateFlow<Boolean>>()
    private fun markLineBusy(line: Locations, busy: Boolean) {
        lineBusy.computeIfAbsent(line) { MutableStateFlow(false) }.value = busy
    }

    private fun pickDeliveryLocation(): Locations {
        assemblyLines.forEach { line ->
            pendingPerLine.computeIfAbsent(line) { AtomicInteger(0) }
        }

        // Pick the line with the fewest pending orders
        return pendingPerLine.entries.minByOrNull { it.value.get() }?.key
            ?: assemblyLines.random()
    }

    private val pendingPerLine = ConcurrentHashMap<Locations, AtomicInteger>()

    private fun incPending(line: Locations) {
        pendingPerLine.computeIfAbsent(line) { AtomicInteger(0) }
            .incrementAndGet()
    }

    private fun decPending(line: Locations) {
        pendingPerLine[line]?.decrementAndGet()
    }

    private val assemblyGates = ConcurrentHashMap<Locations, Semaphore>()
    private val activeAssemblies = AtomicInteger(0)

    private fun gateFor(location: Locations): Semaphore =
        assemblyGates.computeIfAbsent(location) { Semaphore(1) }

    private val _overallSystemState = MutableStateFlow(AssemblySystemStates.IDLE)

    private data class Enqueued(
        val blueprint: Blueprint,
        val demo: Boolean,
        val reply: CompletableDeferred<AssemblyTransportOrder>,
        val testRunId: String? = null,
        val deliveryLocation: Locations = Locations.ASSEMBLY_LINE_A
    )

    private val queue = Channel<Enqueued>(capacity = 100)
    private val _queueSize = MutableStateFlow(0)
    override fun queueSize(): StateFlow<Int> = _queueSize

    override fun observeAssemblySystemState(): StateFlow<AssemblySystemStates> =
        _overallSystemState.asStateFlow()

    override fun getAssemblySystemState(): AssemblySystemStates =
        _overallSystemState.value

    init {
        scope.launch {
            for (req in queue) {
                try {
                    val created = runOne(req.blueprint, req.demo, req.testRunId, req.deliveryLocation)
                    req.reply.complete(created)
                } catch (t: Throwable) {
                    req.reply.completeExceptionally(t)
                } finally {
                    _queueSize.update { (it - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    override suspend fun createOrder(
        orderBlueprint: Blueprint,
        demo: Boolean,
        testRunId: String?
    ): AssemblyTransportOrder {
        val reply = CompletableDeferred<AssemblyTransportOrder>()

        val chosenLocation = pickDeliveryLocation()
        incPending(chosenLocation)

        log.info("Routing new order to $chosenLocation")

        val offered = queue.trySend(
            Enqueued(
                blueprint = orderBlueprint,
                demo = demo,
                reply = reply,
                testRunId = testRunId,
                deliveryLocation = chosenLocation
            )
        )

        if (offered.isFailure) {
            throw IllegalStateException("Order queue is full (100). Try again later.")
        }

        _queueSize.update { it + 1 }
        return reply.await()
    }

    private suspend fun runOne(orderBlueprint: Blueprint, demo: Boolean, testRunId: String?, deliveryLocation: Locations): AssemblyTransportOrder {
        val orderId = "order-${UUID.randomUUID()}"
        val myState = orderStates.computeIfAbsent(orderId) { MutableStateFlow(AssemblySystemStates.IDLE) }

        val order = AssemblyTransportOrder(
            orderId = orderId,
            components = orderBlueprint.components,
            deliveryLocation = deliveryLocation
        )

        val lineGate = gateFor(order.deliveryLocation)

        val confirmationFlow = MutableStateFlow<Boolean?>(null)
        val transportArrivedFlow = MutableStateFlow(false)
        val validationFlow = MutableStateFlow<AssemblyValidationOutcome?>(null)
        val orderCreated = CompletableDeferred<AssemblyTransportOrder>()

        val ports = AssemblyPorts(
            sendOrder = { _ ->

                confirmations[orderId] = confirmationFlow
                arrivals[orderId] = transportArrivedFlow
                validations[orderId] = validationFlow

                val payloadJson = mapper.writeValueAsString(order)

                receiveStage.accept(
                    fromSubsystem = "assembly",
                    toSubsystem = "transport",
                    type = "TRANSPORT_ORDER",
                    payload = payloadJson,
                    correlationId = orderId
                )

                orderCreated.complete(order)
            },
            awaitOrderConfirmation = { confirmationFlow.filterNotNull().first() },
            awaitTransportArrival = { transportArrivedFlow.filter { it }.first() },
            performAssemblyAndValidate = {
                delay((10..20).random() * 1_000L)
                AssemblyValidationOutcome.VALID
            },
            notifyStatus = { state ->
                _events.tryEmit(
                    AssemblyEvent(kind = "status", message = state.name, orderId = orderId)
                )
            },
            acquireAssemblyPermit = {
                lineGate.acquire()
                // mark this line as busy
                markLineBusy(order.deliveryLocation, true)

                val now = activeAssemblies.incrementAndGet()
                if (now > 0) {
                    _overallSystemState.value = AssemblySystemStates.ASSEMBLING
                }
            },
            releaseAssemblyPermit = {
                lineGate.release()
                // mark this line as free again
                markLineBusy(order.deliveryLocation, false)

                val now = activeAssemblies.decrementAndGet()
                if (now <= 0) {
                    _overallSystemState.value = AssemblySystemStates.IDLE
                }
            },

            log = { msg ->
                _events.tryEmit(
                    AssemblyEvent(kind = "log", message = msg, orderId = orderId)
                )
            },
            markOrderSent = { oid, sentAtMs ->
                try {
                    metricsRepo.markOrderSent(
                        oid,
                        java.time.Instant.ofEpochMilli(sentAtMs),
                        testRunId = testRunId
                    )
                } catch (e: Exception) {
                    log.error("markOrderSent failed for $oid: ${e.message}", e)
                    _events.tryEmit(
                        AssemblyEvent(
                            kind = "log",
                            message = "markOrderSent failed for $oid: ${e.message}",
                            orderId = orderId
                        )
                    )
                }
            },
            markOrderConfirmed = { oid, confAtMs, latencyMs ->
                try {
                    metricsRepo.markOrderConfirmed(
                        oid,
                        java.time.Instant.ofEpochMilli(confAtMs),
                        latencyMs
                    )
                } catch (e: Exception) {
                    log.error("markOrderConfirmed failed for $oid: ${e.message}", e)
                    _events.tryEmit(
                        AssemblyEvent(
                            kind = "log",
                            message = "markOrderConfirmed failed for $oid: ${e.message}",
                            orderId = orderId
                        )
                    )
                }
            },
            markOrderAccepted = { oid, accAtMs ->
                try {
                    metricsRepo.markOrderAccepted(
                        oid,
                        java.time.Instant.ofEpochMilli(accAtMs)
                    )
                } catch (e: Exception) {
                    log.error("markOrderAccepted failed for $oid: ${e.message}", e)
                    _events.tryEmit(
                        AssemblyEvent(
                            kind = "log",
                            message = "markOrderAccepted failed for $oid: ${e.message}",
                            orderId = orderId
                        )
                    )
                }
            },
            markAssemblingStarted = { oid, asmAtMs, durationMs ->
                try {
                    metricsRepo.markAssemblingStarted(
                        oid,
                        java.time.Instant.ofEpochMilli(asmAtMs),
                        durationMs
                    )
                } catch (e: Exception) {
                    log.error("markAssemblingStarted failed for $oid: ${e.message}", e)
                    _events.tryEmit(
                        AssemblyEvent(
                            kind = "log",
                            message = "markAssemblingStarted failed for $oid: ${e.message}",
                            orderId = orderId
                        )
                    )
                }
            },
            insertOrderWithState = { state ->
                metricsRepo.insertOrderWithState(order, state)
            }
        )

        val orderScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

        val machine = AssemblyStateMachine(
            scope = orderScope,
            ports = ports,
            timeouts = AssemblyTimeouts(
                confirmationTimeout = 10.seconds,
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
                validationFlow.value = AssemblyValidationOutcome.VALID
            }
        } else null

        val stateCollectorReady = CompletableDeferred<Unit>()
        orderScope.launch {
            machine.state
                .onSubscription { stateCollectorReady.complete(Unit) }
                .collect { st ->
                    myState.value = st
                    _events.tryEmit(
                        AssemblyEvent(
                            kind = "state",
                            state = st,
                            orderId = orderId
                        )
                    )
                }
        }

        stateCollectorReady.await()

        val machineJob = orderScope.launch {
            machine.run(order)
        }

        machineJob.invokeOnCompletion { t ->
            perRunAutopilot?.cancel()

            if (t != null) {
                log.error("State machine FAILED for orderId=$orderId: ${t.message}", t)
                _events.tryEmit(
                    AssemblyEvent(
                        kind = "log",
                        message = "State machine FAILED for $orderId: ${t.message}",
                        orderId = orderId
                    )
                )
            }

            val finalState = machine.state.value
            orderStates[orderId]?.value = finalState

            _events.tryEmit(
                AssemblyEvent(
                    kind = "state",
                    state = finalState,
                    orderId = orderId
                )
            )

            confirmations.remove(orderId)
            arrivals.remove(orderId)
            validations.remove(orderId)

            decPending(order.deliveryLocation)
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
        validations[orderId]?.let { it.value = if (valid) AssemblyValidationOutcome.VALID else AssemblyValidationOutcome.INVALID }
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
