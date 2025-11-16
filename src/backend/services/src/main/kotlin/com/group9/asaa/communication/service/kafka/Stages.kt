package com.group9.asaa.communication.service.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.communication.model.CommunicationMessage
import com.group9.asaa.communication.model.FailureReason
import com.group9.asaa.communication.state.CommunicationStateMachine
import com.group9.asaa.communication.outbox.DuplicationGate
import com.group9.asaa.communication.outbox.OutboxRecord
import com.group9.asaa.communication.outbox.OutboxRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class ReceiveStage(
    private val template: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun accept(subsystem: String, payload: String, correlationId: String?): String {
        val tr = CommunicationStateMachine.onReceive(subsystem, payload, correlationId)
        val json = mapper.writeValueAsString(tr.updated)
        template.executeInTransaction { ops ->
            ops.send(KafkaConfiguration.TOPIC_INBOUND, tr.updated.messageId, json)
            listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
            }
            Unit
        }
        log.info("{} RECEIVED subsystem={} correlationId={}", tr.updated.messageId, subsystem, correlationId)
        return tr.updated.messageId
    }

    private fun emitObservability(tr: CommunicationStateMachine.TransitionResult) { /* no-op, inlined into transaction */ }
}

@Service
class ConnectStage(
    private val mapper: ObjectMapper,
    private val template: KafkaTemplate<String, String>,
    private val gate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun process(message: CommunicationMessage) = gate.withLock(message.messageId) {
        try {
            val enrichment = mapOf("enriched" to "true")
            val tr = CommunicationStateMachine.connect(message, enrichment)
            template.executeInTransaction { ops ->
                ops.send(KafkaConfiguration.TOPIC_CONNECTED, tr.updated.messageId, mapper.writeValueAsString(tr.updated))
                listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                    ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
                }
                Unit // Kotlin Unit instead of null
            }
            log.info("{} CONNECTED", message.messageId)
        } catch (e: Exception) {
            val fail = CommunicationStateMachine.failed(message, FailureReason.EnrichmentFailed)
            template.executeInTransaction { ops ->
                ops.send(KafkaConfiguration.TOPIC_DLQ, message.messageId, mapper.writeValueAsString(fail.updated))
                listOfNotNull(fail.stateEvent, fail.statusEvent, fail.logEvent).forEach { ev ->
                    ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, ev.messageId, mapper.writeValueAsString(ev))
                }
                Unit
            }
            log.error("{} FAILED connect {}", message.messageId, e.message)
        }
    }

    private fun emitFailEvents(tr: CommunicationStateMachine.TransitionResult) {
        template.executeInTransaction { ops ->
            listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
            }
            Unit
        }
    }
}

@Service
class SendStage(
    private val template: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
    private val outbox: OutboxRepository,
    private val gate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun begin(message: CommunicationMessage) = gate.withLock(message.messageId) {
        val sending = CommunicationStateMachine.sending(message)
        // Create outbox record for later dispatch (idempotent) with placeholder headers
        outbox.save(
            OutboxRecord(
                messageId = message.messageId,
                payload = message.payload,
                headers = mapOf("Idempotency-Key" to message.messageId)
            )
        )
        template.executeInTransaction { ops ->
            // emit observability within the same transaction
            listOfNotNull(sending.stateEvent, sending.statusEvent, sending.logEvent).forEach { e ->
                ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
            }
            ops.send(KafkaConfiguration.TOPIC_OUTBOUND, message.messageId, mapper.writeValueAsString(sending.updated))
            Unit
        }
        log.info("{} SENDING", message.messageId)
    }

    /** Simulated delivery draining the outbox; in real system use scheduled/background worker. */
    fun drainOutbox(max: Int = 50) {
        outbox.findPending(max).forEach { rec ->
            // Simulated successful HTTP call
            val sentMsg = CommunicationMessage(
                messageId = rec.messageId,
                subsystem = "delivery",
                payload = rec.payload,
                correlationId = null,
                state = com.group9.asaa.communication.model.CommunicationState.SENDING // prior state to transition
            )
            val tr = CommunicationStateMachine.sent(sentMsg)
            // Produce observability + updated SENT message to outbound within one transaction
            template.executeInTransaction { ops ->
                listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                    ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
                }
                ops.send(KafkaConfiguration.TOPIC_OUTBOUND, rec.messageId, mapper.writeValueAsString(tr.updated))
                Unit
            }
            outbox.markDispatched(rec.messageId)
            log.info("{} SENT", rec.messageId)
        }
    }

    private fun emitObservability(tr: CommunicationStateMachine.TransitionResult) { /* no-op, inlined into transaction */ }
}

@Service
class NotifyStage(
    private val mapper: ObjectMapper,
    private val template: KafkaTemplate<String, String>,
    private val gate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    suspend fun notify(message: CommunicationMessage) = gate.withLock(message.messageId) {
        val tr = CommunicationStateMachine.notified(message)
        template.executeInTransaction { ops ->
            listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                ops.send(KafkaConfiguration.TOPIC_NOTIFICATIONS, e.messageId, mapper.writeValueAsString(e))
            }
            Unit
        }
        log.info("{} NOTIFIED", message.messageId)
    }
}
