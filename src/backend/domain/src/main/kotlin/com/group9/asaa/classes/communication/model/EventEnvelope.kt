package com.group9.asaa.classes.communication.model

import java.time.Instant

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

