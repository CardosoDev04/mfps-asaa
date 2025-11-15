package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.*
import com.group9.asaa.misc.Locations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AssemblyPorts(
    val sendOrder: suspend (order: AssemblyTransportOrder) -> Unit,
    val awaitOrderConfirmation: suspend () -> Boolean?,
    val awaitTransportArrival: suspend () -> Boolean,
    val performAssemblyAndValidate: suspend () -> ValidationOutcome?,
    val notifyStatus: suspend (state: AssemblyTransportOrderStates) -> Unit,
    val acquireAssemblyPermit: suspend () -> Unit,
    val releaseAssemblyPermit: suspend () -> Unit,
    val log: suspend (String) -> Unit,
    val markOrderSent: suspend (orderId: String, sentAt: Long) -> Unit,
    val markOrderConfirmed: suspend (
        orderId: String,
        confirmationAt: Long,
        confirmationLatencyMs: Long
    ) -> Unit,
    val markOrderAccepted: suspend (orderId: String, acceptedAt: Long) -> Unit,
    val markAssemblingStarted: suspend (
        orderId: String,
        assemblingStartedAt: Long,
        acceptedToAssemblingMs: Long?
    ) -> Unit,
    val insertOrderWithState: suspend (order: AssemblyTransportOrder, state: AssemblyTransportOrderStates) -> Unit
)

enum class ValidationOutcome { VALID, INVALID }

data class AssemblyTimeouts(
    val confirmationTimeout: Duration = 30.seconds,
    val deliveryTimeout: Duration = 300.seconds,
    val validationTimeout: Duration = 30.seconds
)

data class AssemblyResult(
    val order: AssemblyTransportOrder,
    val finalSystemState: AssemblySystemStates,
    val reportedOrderState: AssemblyTransportOrderStates
)

class AssemblyStateMachine(
    private val scope: CoroutineScope,
    private val ports: AssemblyPorts,
    private val timeouts: AssemblyTimeouts = AssemblyTimeouts()
) {
    private val _state = MutableStateFlow(AssemblySystemStates.IDLE)
    val state: StateFlow<AssemblySystemStates> = _state

    private var sentAtMs: Long? = null
    private var confirmationAtMs: Long? = null
    private var acceptedAtMs: Long? = null

    private suspend fun log(msg: String) {
        ports.log(msg)
    }

    private suspend fun transition(to: AssemblySystemStates) {
        _state.value = to
        log("Transition → $to")
    }

    private suspend fun notifyStatus(s: AssemblyTransportOrderStates) {
        ports.notifyStatus(s)
    }

    private suspend fun finishAndReturn(
        order: AssemblyTransportOrder,
        reported: AssemblyTransportOrderStates,
        finalSystemState: AssemblySystemStates
    ): AssemblyResult {
        transition(finalSystemState)
        notifyStatus(reported)
        log("Finish order ${order.orderId}: finalState=$finalSystemState, reported=$reported")
        ports.insertOrderWithState(order, reported)
        return AssemblyResult(order, state.value, reported)
    }

    suspend fun run(
        blueprint: Blueprint,
        deliveryLocation: Locations = Locations.ASSEMBLY_LINE_A,
        orderId: String
    ): AssemblyResult {
        require(scope.isActive) { "State machine scope is not active." }

        transition(AssemblySystemStates.CREATING_ORDER)
        val order = AssemblyTransportOrder(
            orderId = orderId,
            components = blueprint.components,
            deliveryLocation = deliveryLocation
        )
        transition(AssemblySystemStates.ORDER_CREATED)
        ports.log("Created assembly transport order ${order.orderId} with ${order.components.size} components.")

        transition(AssemblySystemStates.SENDING_ORDER)
        ports.sendOrder(order)
        ports.log("Sent assembly transport order ${order.orderId} with ${order.components.size} components.")
        val sentNow = System.currentTimeMillis()
        sentAtMs = sentNow
        ports.markOrderSent(order.orderId, sentNow)

        transition(AssemblySystemStates.RECEIVING_CONFIRMATION)
        log("Awaiting confirmation for order ${order.orderId}...")
        val confirmation: Boolean? = withTimeoutOrNull(timeouts.confirmationTimeout) {
            ports.awaitOrderConfirmation()
        }

        transition(AssemblySystemStates.EVALUATING_CONFIRMATION)
        log("Evaluating confirmation for order ${order.orderId}...")

        val confirmationNow = System.currentTimeMillis()
        confirmationAtMs = confirmationNow
        ports.markOrderConfirmed(
            order.orderId,
            confirmationNow,
            confirmationNow - (sentAtMs ?: confirmationNow)
        )

        when (confirmation) {
            null -> {
                return finishAndReturn(order, AssemblyTransportOrderStates.DENIED, AssemblySystemStates.ORDER_TIMED_OUT)
            }
            false -> {
                return finishAndReturn(order, AssemblyTransportOrderStates.DENIED, AssemblySystemStates.ORDER_DENIED)
            }
            true -> {
                transition(AssemblySystemStates.ORDER_ACCEPTED)
                val acceptedNow = System.currentTimeMillis()
                acceptedAtMs = acceptedNow
                ports.markOrderAccepted(order.orderId, acceptedNow)
                notifyStatus(AssemblyTransportOrderStates.ACCEPTED)
                log("Order ${order.orderId} accepted for assembly.")
            }
        }

        transition(AssemblySystemStates.WAITING_FOR_TRANSPORT)
        log("Waiting for transport arrival for order ${order.orderId}...")
        val delivered = withTimeoutOrNull(timeouts.deliveryTimeout) {
            ports.awaitTransportArrival()
        } ?: false

        if (!delivered) {
            return finishAndReturn(order, AssemblyTransportOrderStates.DENIED, AssemblySystemStates.ORDER_TIMED_OUT)
        }

        ports.acquireAssemblyPermit()
        try {
            transition(AssemblySystemStates.ASSEMBLING)
            notifyStatus(AssemblyTransportOrderStates.IN_PROGRESS)
            ports.insertOrderWithState(order, AssemblyTransportOrderStates.IN_PROGRESS)
            log("Transport arrived for order ${order.orderId}. Starting assembly...")

            val assemblingNow = System.currentTimeMillis()
            val acceptedToAsm =
                acceptedAtMs?.let { acc -> assemblingNow - acc }

            ports.markAssemblingStarted(order.orderId, assemblingNow, acceptedToAsm)

            val validation = withTimeoutOrNull(timeouts.validationTimeout) {
                ports.performAssemblyAndValidate()
            }
            return when (validation) {
                null -> {
                    finishAndReturn(order, AssemblyTransportOrderStates.DENIED, AssemblySystemStates.ASSEMBLY_TIMED_OUT)
                }
                ValidationOutcome.INVALID -> {
                    finishAndReturn(order, AssemblyTransportOrderStates.DENIED, AssemblySystemStates.ASSEMBLY_INVALID)
                }
                ValidationOutcome.VALID -> {
                    finishAndReturn(order, AssemblyTransportOrderStates.COMPLETED, AssemblySystemStates.ASSEMBLY_COMPLETED)
                }
            }
        } finally {
            ports.releaseAssemblyPermit()
        }
    }
}
