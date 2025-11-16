package com.group9.asaa.assembly

import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates

interface IAssemblyMetricsRepository {
    fun markOrderSent(orderId: String, sentAt: java.time.Instant, testRunId: String?)
    fun markOrderConfirmed(orderId: String, confirmationAt: java.time.Instant, latencyMs: Long)
    fun markOrderAccepted(orderId: String, acceptedAt: java.time.Instant)
    fun markAssemblingStarted(
        orderId: String,
        assemblingStartedAt: java.time.Instant,
        acceptedToAssemblingMs: Long?
    )
    fun insertOrderWithState(order: AssemblyTransportOrder, state: AssemblyTransportOrderStates)
}

