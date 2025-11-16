package com.group9.asaa.transportation

import com.group9.asaa.classes.TransportSystemState
import com.group9.asaa.classes.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private enum class ReceiverState {
    START, RECEIVING_ORDER, RECEIVED_ORDER, TIMED_OUT,
    CHECKING_AVAILABILITY, SENDING_CONFIRMATION,
    DENYING_ORDER, ACCEPTING_ORDER, ORDER_ACCEPTED,
    FULFILLING_ORDER, NOTIFYING_STATUS_FULFILLING_ORDER,
    ORDER_FULFILLED, NOTIFYING_STATUS_FULFILLED,
    SENT_CONFIRMATION, NOTIFYING_NOT_AVAILABLE,
    NOTIFYING_STATUS_NOT_AVAILABLE, DONE
}

class TransportStateMachine(
    private val scope: CoroutineScope,
    private val ports: TransportPorts
) {
    private val _state = MutableStateFlow(TransportSystemState.IDLE)
    val state: StateFlow<TransportSystemState> = _state

    private val MAX_ORDERS = 10
    private val deniedCounter = IntArray(1) { 0 }
    private val processedCounter = IntArray(1) { 0 }

    private fun safeInc(counter: IntArray) {
        if (counter[0] < MAX_ORDERS) counter[0]++
    }

    private suspend fun log(msg: String) = ports.log(msg)
    private suspend fun transition(to: TransportSystemState) {
        _state.value = to
        log("→ $to")
    }

    suspend fun run(order: AssemblyTransportOrder): TransportResult = coroutineScope {
        var current = ReceiverState.START

        while (coroutineContext.isActive) {
            when (current) {
                ReceiverState.START -> {
                    transition(TransportSystemState.CREATING_ORDER)
                    current = ReceiverState.RECEIVING_ORDER
                }
                ReceiverState.RECEIVING_ORDER -> {
                    log("Received send_transport_order: ${order.orderId}")
                    current = ReceiverState.RECEIVED_ORDER
                }
                ReceiverState.RECEIVED_ORDER -> {
                    val confirmation = withTimeoutOrNull(1_000) { ports.awaitConfirmation() }
                    current = if (confirmation == null) ReceiverState.TIMED_OUT else ReceiverState.CHECKING_AVAILABILITY
                }
                ReceiverState.TIMED_OUT -> {
                    log("Assembly order timed out")
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.DENIED, TransportSystemState.ORDER_TIMED_OUT)
                }
                ReceiverState.CHECKING_AVAILABILITY -> {
                    var agv: AGV? = null
                    while (agv == null && coroutineContext.isActive) {
                        agv = ports.acquireAGV()
                        if (agv == null) delay(200)
                    }
                    agv?.let { ports.releaseAGV(it) }
                    current = ReceiverState.SENDING_CONFIRMATION
                }
                ReceiverState.SENDING_CONFIRMATION -> {
                    val hasAgv = AGVPool.checkAvailability().isNotEmpty()
                    current = if (!hasAgv) ReceiverState.DENYING_ORDER else ReceiverState.ACCEPTING_ORDER
                }

                // DENY PATH
                ReceiverState.DENYING_ORDER -> {
                    log("Order ${order.orderId} DENIED – no AGV")
                    safeInc(deniedCounter)
                    ports.denyOrder(order.orderId)
                    ports.notifyStatus(AssemblyTransportOrderStates.DENIED)
                    current = ReceiverState.SENT_CONFIRMATION
                }
                ReceiverState.SENT_CONFIRMATION -> current = ReceiverState.NOTIFYING_NOT_AVAILABLE
                ReceiverState.NOTIFYING_NOT_AVAILABLE -> current = ReceiverState.NOTIFYING_STATUS_NOT_AVAILABLE
                ReceiverState.NOTIFYING_STATUS_NOT_AVAILABLE -> {
                    log("AGV was unavailable")
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.DENIED, TransportSystemState.AGV_UNAVAILABLE)
                }

                // ACCEPT PATH
                ReceiverState.ACCEPTING_ORDER -> {
                    log("Order ${order.orderId} ACCEPTED")
                    safeInc(processedCounter)
                    ports.acceptOrder(order.orderId)
                    ports.notifyStatus(AssemblyTransportOrderStates.ACCEPTED)
                    current = ReceiverState.ORDER_ACCEPTED
                }
                ReceiverState.ORDER_ACCEPTED -> {
                    log("Fulfilling order ${order.orderId}")
                    current = ReceiverState.FULFILLING_ORDER
                }
                ReceiverState.FULFILLING_ORDER -> {
                    current = ReceiverState.NOTIFYING_STATUS_FULFILLING_ORDER
                }
                ReceiverState.NOTIFYING_STATUS_FULFILLING_ORDER -> {
                    ports.notifyStatus(AssemblyTransportOrderStates.IN_PROGRESS)
                    current = ReceiverState.ORDER_FULFILLED
                }
                ReceiverState.ORDER_FULFILLED -> {
                    log("Order ${order.orderId} FULFILLED")
                    ports.notifyStatus(AssemblyTransportOrderStates.COMPLETED)
                    current = ReceiverState.NOTIFYING_STATUS_FULFILLED
                }
                ReceiverState.NOTIFYING_STATUS_FULFILLED -> {
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.COMPLETED, TransportSystemState.ORDER_COMPLETED)
                }
                ReceiverState.DONE -> {
                    break
                }
            }
        }
        finish(order, AssemblyTransportOrderStates.DENIED, TransportSystemState.IDLE)
    }

    private suspend fun finish(
        order: AssemblyTransportOrder,
        reported: AssemblyTransportOrderStates,
        final: TransportSystemState
    ): TransportResult {
        transition(final)
        ports.notifyStatus(reported)
        log("Finished order ${order.orderId} → $reported (system=$final)")
        return TransportResult(order, _state.value, reported)
    }

    companion object {
        private val globalDenied = IntArray(1) { 0 }
        private val globalProcessed = IntArray(1) { 0 }

        internal fun incDenied() = if (globalDenied[0] < 10) globalDenied[0]++ else Unit
        internal fun incProcessed() = if (globalProcessed[0] < 10) globalProcessed[0]++ else Unit

        fun deniedCount() = globalDenied[0]
        fun processedCount() = globalProcessed[0]
    }
}
