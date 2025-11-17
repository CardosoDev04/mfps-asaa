package com.group9.asaa.assembly

import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates
import org.jdbi.v3.core.Jdbi
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JdbiAssemblyMetricsRepository(
    private val systemWriterJdbi: Jdbi
) : IAssemblyMetricsRepository {

    override fun markOrderSent(orderId: String, sentAt: Instant, testRunId: String?) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO orders (
                    order_id,
                    delivery_location,
                    state,
                    sent_at,
                    test_run_id
                )
                VALUES (
                    :orderId,
                    'UNKNOWN',       
                    'PENDING',      
                    :sentAt,
                    :testRunId
                )
                ON CONFLICT (order_id) DO UPDATE
                SET sent_at    = EXCLUDED.sent_at,
                    test_run_id = COALESCE(orders.test_run_id, EXCLUDED.test_run_id),
                    updated_at  = now();
                """
            )
                .bind("orderId", orderId)
                .bind("sentAt", sentAt)
                .bind("testRunId", testRunId)
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
                UPDATE orders
                SET confirmation_at         = :confirmationAt,
                    confirmation_latency_ms = :latency,
                    updated_at              = now()
                WHERE order_id = :orderId;
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
                UPDATE orders
                SET accepted_at = :acceptedAt,
                    state       = :state,
                    updated_at  = now()
                WHERE order_id = :orderId;
                """
            )
                .bind("orderId", orderId)
                .bind("acceptedAt", acceptedAt)
                .bind("state", AssemblyTransportOrderStates.ACCEPTED.name)
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
                UPDATE orders
                SET assembling_started_at     = :assemblingStartedAt,
                    accepted_to_assembling_ms = :durationMs,
                    state                     = :state,
                    updated_at                = now()
                WHERE order_id = :orderId;
                """
            )
                .bind("orderId", orderId)
                .bind("assemblingStartedAt", assemblingStartedAt)
                .bind("durationMs", acceptedToAssemblingMs)
                .bind("state", AssemblyTransportOrderStates.IN_PROGRESS.name)
                .execute()
        }
    }

    override fun insertOrderWithState(order: AssemblyTransportOrder, state: AssemblyTransportOrderStates) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO orders (
                    order_id,
                    delivery_location,
                    state
                )
                VALUES (:orderId, :deliveryLocation, :state)
                ON CONFLICT (order_id) DO UPDATE
                SET delivery_location = EXCLUDED.delivery_location,
                    state             = EXCLUDED.state,
                    updated_at        = now();
                """
            )
                .bind("orderId", order.orderId)
                .bind("deliveryLocation", order.deliveryLocation.name)
                .bind("state", state.name)
                .execute()
        }
    }

    override fun markTransportFulfilled(orderId: String, fulfilledAt: Instant, transportLatencyMs: Long) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
            UPDATE orders
            SET transport_fulfilled_at = :fulfilledAt,
                transport_latency_ms   = :latency,
                updated_at             = now()
            WHERE order_id = :orderId;
            """
            )
                .bind("orderId", orderId)
                .bind("fulfilledAt", fulfilledAt)
                .bind("latency", transportLatencyMs)
                .execute()
        }
    }

    override fun markOrderFinalized(
        orderId: String,
        completedAt: Instant,
        totalLeadTimeMs: Long,
        finalSystemState: String,
        finalOrderState: String
    ) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE orders
                SET completed_at        = :completedAt,
                    total_lead_time_ms  = :totalLeadTimeMs,
                    final_system_state  = :finalSystemState,
                    final_order_state   = :finalOrderState,
                    updated_at          = now()
                WHERE order_id = :orderId;
                """
            )
                .bind("orderId", orderId)
                .bind("completedAt", completedAt)
                .bind("totalLeadTimeMs", totalLeadTimeMs)
                .bind("finalSystemState", finalSystemState)
                .bind("finalOrderState", finalOrderState)
                .execute()
        }
    }

    override fun recordStateTransition(
        orderId: String,
        subsystem: String,
        fromState: String?,
        toState: String,
        at: Instant
    ) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            // 1) insert into state_transitions
            handle.createUpdate(
                """
                INSERT INTO state_transitions (
                    order_id,
                    subsystem,
                    from_state,
                    to_state,
                    ts
                )
                VALUES (:orderId, :subsystem, :fromState, :toState, :ts);
                """
            )
                .bind("orderId", orderId)
                .bind("subsystem", subsystem)
                .bind("fromState", fromState)
                .bind("toState", toState)
                .bind("ts", at)
                .execute()
        }

        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            // 2) keep current system_state on orders in sync
            handle.createUpdate(
                """
                UPDATE orders
                SET system_state = :toState,
                    updated_at   = now()
                WHERE order_id = :orderId;
                """
            )
                .bind("orderId", orderId)
                .bind("toState", toState)
                .execute()
        }
    }

    override fun recordQueueEvent(
        orderId: String,
        line: String,
        eventType: String,
        queueSize: Int,
        pendingOnLine: Int,
        at: Instant
    ) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                INSERT INTO queue_events (
                    order_id,
                    line,
                    event_type,
                    queue_size,
                    pending_on_line,
                    ts
                )
                VALUES (
                    :orderId,
                    :line,
                    :eventType,
                    :queueSize,
                    :pendingOnLine,
                    :ts
                );
                """
            )
                .bind("orderId", orderId)
                .bind("line", line)
                .bind("eventType", eventType)
                .bind("queueSize", queueSize)
                .bind("pendingOnLine", pendingOnLine)
                .bind("ts", at)
                .execute()
        }
    }

    override fun markAssemblyCompleted(orderId: String, completedAt: Instant, assemblyDurationMs: Long) {
        systemWriterJdbi.withHandle<Unit, Exception> { handle ->
            handle.createUpdate(
                """
                UPDATE orders
                SET assembly_duration_ms = :duration,
                    updated_at           = now()
                WHERE order_id = :orderId;
                """
            )
                .bind("orderId", orderId)
                .bind("duration", assemblyDurationMs)
                .execute()
        }
    }
}
