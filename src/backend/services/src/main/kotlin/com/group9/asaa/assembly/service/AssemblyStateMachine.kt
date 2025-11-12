package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.*
import com.group9.asaa.misc.Locations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    val releaseAssemblyPermit: suspend () -> Unit
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

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs: SharedFlow<String> = _logs

    private fun transition(to: AssemblySystemStates) {
        _state.value = to
        _logs.tryEmit("Transition → $to")
    }

    private suspend fun notifyOnce(s: AssemblyTransportOrderStates) {
        transition(AssemblySystemStates.NOTIFYING_STATUS)
        ports.notifyStatus(s)
    }

    private suspend fun finishAndReturn(
        order: AssemblyTransportOrder,
        reported: AssemblyTransportOrderStates
    ): AssemblyResult {
        notifyOnce(reported)
        transition(AssemblySystemStates.IDLE)
        return AssemblyResult(order, state.value, reported)
    }

    suspend fun run(
        blueprint: Blueprint,
        deliveryLocation: Locations = Locations.ASSEMBLY_LINE_A
    ): AssemblyResult {
        require(scope.isActive) { "State machine scope is not active." }

        transition(AssemblySystemStates.CREATING_ORDER)
        val order = AssemblyTransportOrder(
            orderId = "order-${System.currentTimeMillis()}",
            components = blueprint.components,
            deliveryLocation = deliveryLocation
        )
        transition(AssemblySystemStates.ORDER_CREATED)

        transition(AssemblySystemStates.SENDING_ORDER)
        ports.sendOrder(order)

        transition(AssemblySystemStates.RECEIVING_CONFIRMATION)
        val confirmation: Boolean? = withTimeoutOrNull(timeouts.confirmationTimeout) {
            ports.awaitOrderConfirmation()
        }

        transition(AssemblySystemStates.EVALUATING_CONFIRMATION)
        when (confirmation) {
            null -> {
                transition(AssemblySystemStates.ORDER_TIMED_OUT)
                return finishAndReturn(order, AssemblyTransportOrderStates.DENIED)
            }
            false -> {
                transition(AssemblySystemStates.ORDER_DENIED)
                return finishAndReturn(order, AssemblyTransportOrderStates.DENIED)
            }
            true -> {
                transition(AssemblySystemStates.ORDER_ACCEPTED)
                scope.launch { notifyOnce(AssemblyTransportOrderStates.ACCEPTED) }
            }
        }

        transition(AssemblySystemStates.WAITING_FOR_TRANSPORT)
        val delivered = withTimeoutOrNull(timeouts.deliveryTimeout) {
            ports.awaitTransportArrival()
        } ?: false

        if (!delivered) {
            transition(AssemblySystemStates.ORDER_TIMED_OUT)
            return finishAndReturn(order, AssemblyTransportOrderStates.DENIED)
        }

        ports.acquireAssemblyPermit()
        try {
            transition(AssemblySystemStates.ASSEMBLING)
            val validation = withTimeoutOrNull(timeouts.validationTimeout) {
                ports.performAssemblyAndValidate()
            }
            return when (validation) {
                null -> {
                    transition(AssemblySystemStates.ASSEMBLY_TIMED_OUT)
                    finishAndReturn(order, AssemblyTransportOrderStates.DENIED)
                }
                ValidationOutcome.INVALID -> {
                    finishAndReturn(order, AssemblyTransportOrderStates.DENIED)
                }
                ValidationOutcome.VALID -> {
                    transition(AssemblySystemStates.ASSEMBLY_COMPLETED)
                    finishAndReturn(order, AssemblyTransportOrderStates.COMPLETED)
                }
            }
        } finally {
            ports.releaseAssemblyPermit()
        }
    }
}
