package com.group9.asaa.assembly

interface IAssemblyMetricsRepository {
    fun markOrderSent(orderId: String, sentAt: java.time.Instant)
    fun markOrderConfirmed(
        orderId: String,
        confirmationAt: java.time.Instant,
        confirmationLatencyMs: Long
    )
    fun markOrderAccepted(orderId: String, acceptedAt: java.time.Instant)
    fun markAssemblingStarted(
        orderId: String,
        assemblingStartedAt: java.time.Instant,
        acceptedToAssemblingMs: Long?
    )
}
