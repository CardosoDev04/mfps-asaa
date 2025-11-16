package com.group9.asaa.classes.assembly

data class AssemblyOrderMetrics(
    val orderId: String,
    val sentAt: java.time.Instant? = null,
    val confirmationAt: java.time.Instant? = null,
    val confirmationLatencyMs: Long? = null,
    val acceptedAt: java.time.Instant? = null,
    val assemblingStartedAt: java.time.Instant? = null,
    val acceptedToAssemblingMs: Long? = null,
    val testRunId: String? = null
)
