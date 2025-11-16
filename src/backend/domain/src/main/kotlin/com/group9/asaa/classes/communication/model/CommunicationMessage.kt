package com.group9.asaa.classes.communication.model

import java.time.Instant

/**
 * Canonical message representation that flows through the communication pipeline.
 * All Kafka topics carry JSON-serialized instances keyed by messageId.
 */
data class CommunicationMessage(
    val messageId: String,
    val fromSubsystem: String,       // "assembly"
    val toSubsystem: String,         // "transport"
    val type: String,                // "TRANSPORT_ORDER", "ORDER_CONFIRMED", "TRANSPORT_ARRIVED"
    val payload: String,             // JSON of business object (order, event, etc.)
    val correlationId: String?,      // typically orderId
    val state: CommunicationState,
    val metadata: Map<String, String> = emptyMap(),
    val attempts: Int = 0,
    val lastError: String? = null
)


/**
 * Observability event envelope produced to notifications topic and fanned out via SSE.
 * eventType: one of "state", "status", "log" for frontend filtering.
 */
data class EventEnvelope(
    val id: String,            // messageId or composite if needed
    val eventType: String,     // state | status | log
    val timestamp: Instant = Instant.now(),
    val messageId: String,
    val correlationId: String?,
    val data: Map<String, Any?>
)

/**
 * Business status milestones (subset). Extend as needed.
 */
enum class StatusMilestone { RECEIVED, CONNECTED, SENDING, SENT, NOTIFIED, FAILED }

/**
 * Terminal failure reasons could be expanded.
 */
sealed class FailureReason(val reason: String) {
    data object ValidationFailed: FailureReason("validation_failed")
    data object EnrichmentFailed: FailureReason("enrichment_failed")
    data object DeliveryFailed: FailureReason("delivery_failed")
    data class Unknown(val details: String): FailureReason("unknown")
}

/**
 * Communication pipeline states.
 */
enum class CommunicationState { RECEIVED, CONNECTED, SENDING, SENT, NOTIFIED, FAILED }

