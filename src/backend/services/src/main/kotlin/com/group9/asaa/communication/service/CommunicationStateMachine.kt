package com.group9.asaa.communication.service

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

    fun onReceive(
        rawFromSubsystem: String,
        rawToSubsystem: String,
        rawType: String,
        rawPayload: String,
        rawCorrelationId: String?
    ): TransitionResult {
        val messageId = UUID.randomUUID().toString()

        val from = rawFromSubsystem.trim().lowercase()
        val to = rawToSubsystem.trim().lowercase()
        val type = rawType.trim().uppercase()  // or lowercase, but be consistent
        val payload = rawPayload.trim()
        val correlationId = rawCorrelationId?.takeIf { it.isNotBlank() }

        require(from.isNotBlank()) { "fromSubsystem blank" }
        require(to.isNotBlank()) { "toSubsystem blank" }
        require(type.isNotBlank()) { "type blank" }
        require(payload.isNotBlank()) { "payload blank" }

        val msg = CommunicationMessage(
            messageId = messageId,
            fromSubsystem = from,
            toSubsystem = to,
            type = type,
            payload = payload,
            correlationId = correlationId,
            state = CommunicationState.RECEIVED
        )

        return buildTransition(
            msg,
            CommunicationState.RECEIVED,
            StatusMilestone.RECEIVED,
            "Message received"
        )
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
            data = mapOf(
                "state" to newState.name,
                "attempts" to msg.attempts,
                "from" to msg.fromSubsystem,
                "to" to msg.toSubsystem,
                "type" to msg.type
            )
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
