package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.*
import com.group9.asaa.misc.Locations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import com.group9.asaa.classes.assembly.AssemblyValidationOutcome


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
            // transport never came
            return finishAndReturn(
                order,
                AssemblyTransportOrderStates.DENIED,
                AssemblySystemStates.ORDER_TIMED_OUT
            )
        }

        transition(AssemblySystemStates.RECEIVED_TRANSPORT)
        log("Transport arrived for order ${order.orderId}. Waiting for assembly slot...")

        ports.acquireAssemblyPermit()
        try {
            transition(AssemblySystemStates.ASSEMBLING)
            notifyStatus(AssemblyTransportOrderStates.IN_PROGRESS)
            ports.insertOrderWithState(order, AssemblyTransportOrderStates.IN_PROGRESS)
            log("Starting assembly for order ${order.orderId}...")

            val assemblingNow = System.currentTimeMillis()
            val acceptedToAsm = acceptedAtMs?.let { acc -> assemblingNow - acc }

            ports.markAssemblingStarted(order.orderId, assemblingNow, acceptedToAsm)

            val validation = withTimeoutOrNull(timeouts.validationTimeout) {
                ports.performAssemblyAndValidate()
            }
            return when (validation) {
                null -> {
                    finishAndReturn(
                        order,
                        AssemblyTransportOrderStates.DENIED,
                        AssemblySystemStates.ASSEMBLY_TIMED_OUT
                    )
                }
                AssemblyValidationOutcome.INVALID -> {
                    finishAndReturn(
                        order,
                        AssemblyTransportOrderStates.DENIED,
                        AssemblySystemStates.ASSEMBLY_INVALID
                    )
                }
                AssemblyValidationOutcome.VALID -> {
                    finishAndReturn(
                        order,
                        AssemblyTransportOrderStates.COMPLETED,
                        AssemblySystemStates.ASSEMBLY_COMPLETED
                    )
                }
            }
        } finally {
            ports.releaseAssemblyPermit()
        }
    }
}
