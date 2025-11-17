package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates
import com.group9.asaa.classes.assembly.AssemblyValidationOutcome

data class AssemblyPorts(
    val sendOrder: suspend (order: AssemblyTransportOrder) -> Unit,
    val awaitOrderConfirmation: suspend () -> Boolean?,
    val awaitTransportArrival: suspend () -> Boolean,
    val performAssemblyAndValidate: suspend () -> AssemblyValidationOutcome?,
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
