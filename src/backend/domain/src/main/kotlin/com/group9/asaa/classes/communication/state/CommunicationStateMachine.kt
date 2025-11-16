package com.group9.asaa.classes.communication.state

import com.group9.asaa.classes.communication.model.CommunicationMessage
import com.group9.asaa.classes.communication.model.CommunicationState
import com.group9.asaa.classes.communication.model.EventEnvelope
import com.group9.asaa.classes.communication.model.FailureReason
import com.group9.asaa.classes.communication.model.StatusMilestone
import java.time.Instant
import java.util.UUID

/**
 * Pure deterministic state machine. NO I/O here. Returns updated message + emitted events.
 */
object CommunicationStateMachine {
    data class TransitionResult(
        val updated: CommunicationMessage,
        val stateEvent: EventEnvelope,
        val statusEvent: EventEnvelope?,
        val logEvent: EventEnvelope?
    )

    fun onReceive(rawSubsystem: String, rawPayload: String, rawCorrelationId: String?): TransitionResult {
        val messageId = UUID.randomUUID().toString() // For prod: use UUIDv7 library
        // Basic validation / normalization
        val subsystem = rawSubsystem.trim().lowercase()
        val payload = rawPayload.trim()
        require(subsystem.isNotBlank()) { "subsystem blank" }
        require(payload.isNotBlank()) { "payload blank" }
        val msg = CommunicationMessage(
            messageId = messageId,
            subsystem = subsystem,
            payload = payload,
            correlationId = rawCorrelationId?.takeIf { it.isNotBlank() },
            state = CommunicationState.RECEIVED
        )
        return buildTransition(msg, CommunicationState.RECEIVED, StatusMilestone.RECEIVED, "Message received")
    }

    fun connect(message: CommunicationMessage, enrichment: Map<String, String>): TransitionResult {
        check(message.state == CommunicationState.RECEIVED) { "Illegal state: ${message.state}" }
        val updated = message.copy(
            metadata = message.metadata + enrichment,
            state = CommunicationState.CONNECTED
        )
        return buildTransition(updated, CommunicationState.CONNECTED, StatusMilestone.CONNECTED, "Message connected/enriched")
    }

    fun sending(message: CommunicationMessage): TransitionResult {
        check(message.state == CommunicationState.CONNECTED) { "Illegal state: ${message.state}" }
        val updated = message.copy(state = CommunicationState.SENDING)
        return buildTransition(updated, CommunicationState.SENDING, StatusMilestone.SENDING, "Delivery started")
    }

    fun sent(message: CommunicationMessage): TransitionResult {
        check(message.state == CommunicationState.SENDING) { "Illegal state: ${message.state}" }
        val updated = message.copy(state = CommunicationState.SENT)
        return buildTransition(updated, CommunicationState.SENT, StatusMilestone.SENT, "Delivery succeeded")
    }

    fun notified(message: CommunicationMessage): TransitionResult {
        check(message.state == CommunicationState.SENT) { "Illegal state: ${message.state}" }
        val updated = message.copy(state = CommunicationState.NOTIFIED)
        return buildTransition(updated, CommunicationState.NOTIFIED, StatusMilestone.NOTIFIED, "Frontend notified")
    }

    fun failed(message: CommunicationMessage, reason: FailureReason): TransitionResult {
        val updated = message.copy(state = CommunicationState.FAILED, lastError = reason.reason)
        return buildTransition(updated, CommunicationState.FAILED, StatusMilestone.FAILED, "Failure: ${reason.reason}")
    }

    private fun buildTransition(
        msg: CommunicationMessage,
        newState: CommunicationState,
        milestone: StatusMilestone,
        log: String
    ): TransitionResult {
        val now = Instant.now()
        val stateEvent = EventEnvelope(
            id = msg.messageId + ":state:${newState.name}",
            eventType = "state",
            timestamp = now,
            messageId = msg.messageId,
            correlationId = msg.correlationId,
            data = mapOf("state" to newState.name, "attempts" to msg.attempts)
        )
        val statusEvent = EventEnvelope(
            id = msg.messageId + ":status:${milestone.name}",
            eventType = "status",
            timestamp = now,
            messageId = msg.messageId,
            correlationId = msg.correlationId,
            data = mapOf("milestone" to milestone.name)
        )
        val logEvent = EventEnvelope(
            id = msg.messageId + ":log:${newState.name}",
            eventType = "log",
            timestamp = now,
            messageId = msg.messageId,
            correlationId = msg.correlationId,
            data = mapOf("message" to log)
        )
        return TransitionResult(msg, stateEvent, statusEvent, logEvent)
    }
}