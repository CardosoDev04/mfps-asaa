package com.group9.asaa.assembly

import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates
import java.time.Instant

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
    fun markTransportFulfilled(orderId: String, fulfilledAt: Instant, transportLatencyMs: Long)
    fun markAssemblyCompleted(orderId: String, completedAt: Instant, assemblyDurationMs: Long)
    fun markOrderFinalized(
        orderId: String,
        completedAt: Instant,
        totalLeadTimeMs: Long,
        finalSystemState: String,
        finalOrderState: String
    )
    fun recordStateTransition(
        orderId: String,
        subsystem: String,
        fromState: String?,
        toState: String,
        at: Instant = Instant.now()
    )
    fun recordQueueEvent(
        orderId: String,
        line: String,
        eventType: String,
        queueSize: Int,
        pendingOnLine: Int,
        at: Instant = Instant.now()
    )
}

