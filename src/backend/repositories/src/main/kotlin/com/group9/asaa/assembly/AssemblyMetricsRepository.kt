package com.group9.asaa.assembly

import org.jdbi.v3.core.Jdbi
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbiAssemblyMetricsRepository(
    private val systemWriterJdbi: Jdbi
) : IAssemblyMetricsRepository {

    override fun markOrderSent(orderId: String, sentAt: Instant) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO assembly_order_metrics (order_id, sent_at)
                VALUES (:orderId, :sentAt)
                ON CONFLICT (order_id) DO UPDATE
                SET sent_at = EXCLUDED.sent_at
                """
            )
                .bind("orderId", orderId)
                .bind("sentAt", sentAt)
                .execute()
        }
    }

    override fun markOrderConfirmed(
        orderId: String,
        confirmationAt: Instant,
        confirmationLatencyMs: Long
    ) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO assembly_order_metrics (order_id, confirmation_at, confirmation_latency_ms)
                VALUES (:orderId, :confirmationAt, :latency)
                ON CONFLICT (order_id) DO UPDATE
                SET confirmation_at         = EXCLUDED.confirmation_at,
                    confirmation_latency_ms = EXCLUDED.confirmation_latency_ms
                """
            )
                .bind("orderId", orderId)
                .bind("confirmationAt", confirmationAt)
                .bind("latency", confirmationLatencyMs)
                .execute()
        }
    }

    override fun markOrderAccepted(orderId: String, acceptedAt: Instant) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO assembly_order_metrics (order_id, accepted_at)
                VALUES (:orderId, :acceptedAt)
                ON CONFLICT (order_id) DO UPDATE
                SET accepted_at = EXCLUDED.accepted_at
                """
            )
                .bind("orderId", orderId)
                .bind("acceptedAt", acceptedAt)
                .execute()
        }
    }

    override fun markAssemblingStarted(
        orderId: String,
        assemblingStartedAt: Instant,
        acceptedToAssemblingMs: Long?
    ) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO assembly_order_metrics (order_id, assembling_started_at, accepted_to_assembling_ms)
                VALUES (:orderId, :assemblingStartedAt, :dur)
                ON CONFLICT (order_id) DO UPDATE
                SET assembling_started_at     = EXCLUDED.assembling_started_at,
                    accepted_to_assembling_ms = COALESCE(EXCLUDED.accepted_to_assembling_ms,
                                                          assembly_order_metrics.accepted_to_assembling_ms)
                """
            )
                .bind("orderId", orderId)
                .bind("assemblingStartedAt", assemblingStartedAt)
                .bind("dur", acceptedToAssemblingMs)
                .execute()
        }
    }
}

