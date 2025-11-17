package com.group9.asaa.classes.communication.model

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
