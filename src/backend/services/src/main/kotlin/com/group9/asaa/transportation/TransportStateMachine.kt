package com.group9.asaa.transport.service

import com.group9.asaa.classes.transport.*
import com.group9.asaa.transport.AssemblyTransportOrder
import com.group9.asaa.transport.AssemblyTransportOrderStates
import com.group9.asaa.transport.TransportPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds
import com.group9.asaa.transport.*

enum class TransportSystemState {
    IDLE,
    CREATING_ORDER, ORDER_CREATED, SENDING_ORDER,
    AWAITING_CONFIRMATION, EVALUATING_CONFIRMATION,
    ORDER_ACCEPTED, ACQUIRING_AGV, AGV_ACQUIRED,
    PERFORMING_TRANSPORT, TRANSPORT_COMPLETED,
    ORDER_COMPLETED, ORDER_DENIED, ORDER_TIMED_OUT, AGV_UNAVAILABLE,
    FULFILLING_ORDER
}

data class TransportResult(
    val order: AssemblyTransportOrder,
    val finalSystemState: TransportSystemState,
    val reportedOrderState: AssemblyTransportOrderStates
)

// ---------------------------------------------------------------------
//  INTERNAL STATE MACHINE – YOUR NUMBERED STEPS
// ---------------------------------------------------------------------
private enum class ReceiverState {
    START,
    RECEIVING_ORDER,
    RECEIVED_ORDER,
    TIMED_OUT,
    CHECKING_AVAILABILITY,
    SENDING_CONFIRMATION,
    DENYING_ORDER,
    ACCEPTING_ORDER,
    ORDER_ACCEPTED,
    FULFILLING_ORDER,
    NOTIFYING_STATUS_FULFILLING_ORDER,
    ORDER_FULFILLED,
    NOTIFYING_STATUS_FULFILLED,
    SENT_CONFIRMATION,
    NOTIFYING_NOT_AVAILABLE,
    NOTIFYING_STATUS_NOT_AVAILABLE,
    DONE
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

    private fun safeInc(x: IntArray) {
        if (x[0] < MAX_ORDERS) x[0]++
    }

    private suspend fun log(msg: String) = ports.log(msg)
    private suspend fun transition(to: TransportSystemState) {
        _state.value = to
        log("→ $to")
    }

    // -----------------------------------------------------------------
    //  PUBLIC run() – uses while-loop to avoid 'return' in collect
    // -----------------------------------------------------------------
    suspend fun run(order: AssemblyTransportOrder): TransportResult = coroutineScope {
        var current = ReceiverState.START

        while (current != ReceiverState.DONE && coroutineContext.isActive) {
            println("→ $current")

            when (current) {
                // 1. Receiving send_transport_order
                ReceiverState.START -> {
                    transition(TransportSystemState.CREATING_ORDER)
                    current = ReceiverState.RECEIVING_ORDER
                }

                // 2. → ReceivedOrder (immediate)
                ReceiverState.RECEIVING_ORDER -> {
                    log("Received send_transport_order: ${order.orderId}")
                    current = ReceiverState.RECEIVED_ORDER
                }

                // 3. Check timeout via ports
                ReceiverState.RECEIVED_ORDER -> {
                    val confirmation = withTimeoutOrNull(1.seconds) { ports.awaitConfirmation() }
                    current = if (confirmation == null) {
                        ReceiverState.TIMED_OUT
                    } else {
                        ReceiverState.CHECKING_AVAILABILITY
                    }
                }

                // 4. → back to START and stop
                ReceiverState.TIMED_OUT -> {
                    log("Assembly order timed out")
                    current = ReceiverState.START
                    println("→ START (again, stopped)")
                    current = ReceiverState.DONE
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.DENIED, TransportSystemState.ORDER_TIMED_OUT)
                }

                // 5. Wait for AGV
                ReceiverState.CHECKING_AVAILABILITY -> {
                    var agv: AGV? = null
                    while (agv == null && coroutineContext.isActive) {
                        agv = ports.acquireAGV()
                        if (agv == null) delay(200)
                    }
                    if (agv != null) ports.releaseAGV(agv)
                    current = ReceiverState.SENDING_CONFIRMATION
                }

                // 6. Branch
                ReceiverState.SENDING_CONFIRMATION -> {
                    val hasAgv = AGVPool.checkAvailability().isNotEmpty()
                    current = if (!hasAgv) ReceiverState.DENYING_ORDER else ReceiverState.ACCEPTING_ORDER
                }

                // ========== DENY PATH ==========
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
                    current = ReceiverState.START
                    println("→ START (AGV was unavailable)")
                    current = ReceiverState.DONE
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.DENIED, TransportSystemState.AGV_UNAVAILABLE)
                }

                // ========== ACCEPT PATH ==========
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
                    val deliveryClock = 0
                    if (deliveryClock >= 300) log("Delivery clock >= 300 – still proceeding")
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
                    current = ReceiverState.START
                    println("→ START (order fulfilled)")
                    current = ReceiverState.DONE
                    return@coroutineScope finish(order, AssemblyTransportOrderStates.COMPLETED, TransportSystemState.ORDER_COMPLETED)
                }

                ReceiverState.DONE -> break
            }
        }

        // Fallback
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

    // -----------------------------------------------------------------
    //  Public counters
    // -----------------------------------------------------------------
    companion object {
        private val deniedCounter = IntArray(1) { 0 }
        private val processedCounter = IntArray(1) { 0 }

        internal fun incDenied() = if (deniedCounter[0] < 10) deniedCounter[0]++ else Unit
        internal fun incProcessed() = if (processedCounter[0] < 10) processedCounter[0]++ else Unit

        fun deniedCount() = deniedCounter[0]
        fun processedCount() = processedCounter[0]
    }
}