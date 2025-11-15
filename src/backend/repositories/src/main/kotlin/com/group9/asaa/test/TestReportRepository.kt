package com.group9.asaa.test

import com.group9.asaa.classes.assembly.AssemblyOrderMetrics
import com.group9.asaa.classes.test.TestReport
import com.group9.asaa.classes.test.getTestReportFromAssemblyOrderMetrics
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class TestReportRepository(
    private val systemReaderJdbi: Jdbi
): ITestReportRepository {
    private val logger = LoggerFactory.getLogger("TestReportRepository")
    override fun getAllTestReports(): List<TestReport> {
        try {
            return systemReaderJdbi.withHandle<List<TestReport>, Exception> { handle ->
                val metrics = handle.createQuery(
                    """
                SELECT order_id as orderId,
                          sent_at as sentAt,
                            confirmation_at as confirmationAt,
                            confirmation_latency_ms as confirmationLatencyMs,
                            accepted_at as acceptedAt,
                            assembling_started_at as assemblingStartedAt,
                            accepted_to_assembling_ms as acceptedToAssemblingMs,
                            test_run_id as testRunId
                FROM orders
                WHERE test_run_id IS NOT NULL AND sent_at IS NOT NULL
            """.trimIndent()
                )
                    .map { rs, _ ->
                        AssemblyOrderMetrics(
                            orderId = rs.getString("orderId"),
                            sentAt = rs.getTimestamp("sentAt")?.toInstant(),
                            confirmationAt = rs.getTimestamp("confirmationAt")?.toInstant(),
                            confirmationLatencyMs = rs.getLong("confirmationLatencyMs").takeIf { !rs.wasNull() },
                            acceptedAt = rs.getTimestamp("acceptedAt")?.toInstant(),
                            assemblingStartedAt = rs.getTimestamp("assemblingStartedAt")?.toInstant(),
                            acceptedToAssemblingMs = rs.getLong("acceptedToAssemblingMs").takeIf { !rs.wasNull() },
                            testRunId = rs.getString("testRunId")
                        )
                    }
                    .list()

                val metricsByTestId = metrics.groupBy { it.testRunId }.values.map { it }

                metricsByTestId.map {
                    getTestReportFromAssemblyOrderMetrics(it, it.first().testRunId!!, it.first().sentAt!!)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch test reports", e)
            throw e
        }
    }

    override fun getTestReport(testRunId: String): TestReport? {
        return systemReaderJdbi.withHandle<TestReport?, Exception> { handle ->
            val metrics = handle.createQuery(
                """
                SELECT order_id as orderId,
                          sent_at as sentAt,
                            confirmation_at as confirmationAt,
                            confirmation_latency_ms as confirmationLatencyMs,
                            accepted_at as acceptedAt,
                            assembling_started_at as assemblingStartedAt,
                            accepted_to_assembling_ms as acceptedToAssemblingMs,
                            test_run_id as testRunId
                FROM orders
                WHERE test_run_id = :testRunId AND sent_at IS NOT NULL
            """.trimIndent()
            )
                .bind("testRunId", testRunId)
                .map { rs, _ ->
                    AssemblyOrderMetrics(
                        orderId = rs.getString("orderId"),
                        sentAt = rs.getTimestamp("sentAt")?.toInstant(),
                        confirmationAt = rs.getTimestamp("confirmationAt")?.toInstant(),
                        confirmationLatencyMs = rs.getLong("confirmationLatencyMs").takeIf { !rs.wasNull() },
                        acceptedAt = rs.getTimestamp("acceptedAt")?.toInstant(),
                        assemblingStartedAt = rs.getTimestamp("assemblingStartedAt")?.toInstant(),
                        acceptedToAssemblingMs = rs.getLong("acceptedToAssemblingMs").takeIf { !rs.wasNull() },
                        testRunId = rs.getString("testRunId")
                    )
                }
                .list()

            return@withHandle if (metrics.isNotEmpty()) {
                getTestReportFromAssemblyOrderMetrics(metrics, testRunId, metrics.first().sentAt!!)
            } else {
                null
            }
        }
    }
}
