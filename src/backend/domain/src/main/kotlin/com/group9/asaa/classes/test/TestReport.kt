package com.group9.asaa.classes.test

import com.group9.asaa.classes.assembly.AssemblyOrderMetrics
import java.time.Instant

data class TestReport(
    val id: String,
    val date: Instant,
    val averageTimeToConfirmationMs: Long,
    val averageTimeToAssemblingMs: Long
)

fun getTestReportFromAssemblyOrderMetrics(
    metrics: List<AssemblyOrderMetrics>,
    id: String,
    date: Instant
): TestReport {
    val count = metrics.size
    val totalConfirmationTime = metrics.mapNotNull { it.confirmationLatencyMs }.sum()
    val totalAssemblingTime = metrics.mapNotNull { it.acceptedToAssemblingMs }.sum()
    val averageTimeToAssemblingMs = if (count > 0) totalAssemblingTime / count else 0
    val averageTimeToConfirmationMs = if (count > 0) totalConfirmationTime / count else 0

    return TestReport(
        id = id,
        date = date,
        averageTimeToConfirmationMs = averageTimeToConfirmationMs,
        averageTimeToAssemblingMs = averageTimeToAssemblingMs
    )
}
