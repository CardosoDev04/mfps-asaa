package com.group9.asaa.system_observer

import com.group9.asaa.classes.observer.Status
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class StatusRepository(
    private val systemWriterJdbi: Jdbi,
    private val systemReaderJdbi: Jdbi
) : IStatusRepository {
    private val logger = LoggerFactory.getLogger("StatusRepository")

    override fun storeStatus(status: Status): Int {
        return try {
            systemWriterJdbi.withHandle<Int, Exception> { handle ->
                val statusId = status.id ?: UUID.randomUUID().toString()
                handle.createUpdate(
                    """
                    INSERT INTO statuses (
                        id,
                        status_text,
                        set_at,
                        test_run_id
                    )
                    VALUES (
                        :id,
                        :statusText,
                        :setAt,
                        :testRunId
                    )
                    """.trimIndent()
                )
                    .bind("id", statusId)
                    .bind("statusText", status.statusText)
                    .bind("setAt", status.setAt)
                    .bind("testRunId", status.testRunId)
                    .execute()
            }
        } catch (e: Exception) {
            logger.error("Failed to store status", e)
            throw e
        }
    }

    override fun retrieveStatus(statusId: String): Status {
        return try {
            systemReaderJdbi.withHandle<Status, Exception> { handle ->
                handle.createQuery(
                    """
                    SELECT id,
                           status_text as statusText,
                           set_at as setAt,
                           test_run_id as testRunId
                    FROM statuses
                    WHERE id = :statusId
                    """.trimIndent()
                )
                    .bind("statusId", statusId)
                    .map { rs, _ ->
                        Status(
                            id = rs.getString("id"),
                            statusText = rs.getString("statusText"),
                            setAt = rs.getTimestamp("setAt")?.toInstant()
                                ?: throw IllegalStateException("setAt cannot be null"),
                            testRunId = rs.getString("testRunId")
                        )
                    }
                    .one()
            }
        } catch (e: Exception) {
            logger.error("Failed to retrieve status with id: $statusId", e)
            throw e
        }
    }

    override fun fetchStatusForTestRun(testRunId: String): List<Status> {
        return try {
            systemReaderJdbi.withHandle<List<Status>, Exception> { handle ->
                handle.createQuery(
                    """
                    SELECT id,
                           status_text as statusText,
                           set_at as setAt,
                           test_run_id as testRunId
                    FROM statuses
                    WHERE test_run_id = :testRunId
                    ORDER BY set_at ASC
                    """.trimIndent()
                )
                    .bind("testRunId", testRunId)
                    .map { rs, _ ->
                        Status(
                            id = rs.getString("id"),
                            statusText = rs.getString("statusText"),
                            setAt = rs.getTimestamp("setAt")?.toInstant()
                                ?: throw IllegalStateException("setAt cannot be null"),
                            testRunId = rs.getString("testRunId")
                        )
                    }
                    .list()
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch statuses for test run: $testRunId", e)
            throw e
        }
    }
}


