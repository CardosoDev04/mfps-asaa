package com.group9.asaa.communication.service.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.communication.model.CommunicationMessage
import com.group9.asaa.classes.communication.model.CommunicationState
import com.group9.asaa.classes.communication.model.FailureReason
import com.group9.asaa.communication.service.CommunicationStateMachine
import com.group9.asaa.communication.service.outbox.DuplicationGate
import com.group9.asaa.communication.service.outbox.OutboxRecord
import com.group9.asaa.communication.service.outbox.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class ReceiveStage(
    private val template: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun accept(
        fromSubsystem: String,
        toSubsystem: String,
        type: String,
        payload: String,
        correlationId: String?
    ): String {
        val tr = CommunicationStateMachine.onReceive(
            rawFromSubsystem = fromSubsystem,
            rawToSubsystem = toSubsystem,
            rawType = type,
            rawPayload = payload,
            rawCorrelationId = correlationId
        )
        val json = mapper.writeValueAsString(tr.updated)

        template.executeInTransaction { ops ->
            ops.send(KafkaConfiguration.TOPIC_INBOUND, tr.updated.messageId, json)
            listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                ops.send(
                    KafkaConfiguration.TOPIC_NOTIFICATIONS,
                    e.messageId,
                    mapper.writeValueAsString(e)
                )
            }
            Unit
        }

        log.info(
            "{} RECEIVED from={} to={} type={} correlationId={}",
            tr.updated.messageId,
            fromSubsystem,
            toSubsystem,
            type,
            correlationId
        )
        return tr.updated.messageId
    }
}

@Service
class ConnectStage(
    private val mapper: ObjectMapper,
    private val template: KafkaTemplate<String, String>,
    private val duplicationGate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun process(message: CommunicationMessage) =
        duplicationGate.withLock(message.messageId) {
            try {
                val enrichment = mapOf("enriched" to "true")
                val tr = CommunicationStateMachine.connect(message, enrichment)
                template.executeInTransaction { ops ->
                    ops.send(
                        KafkaConfiguration.TOPIC_CONNECTED,
                        tr.updated.messageId,
                        mapper.writeValueAsString(tr.updated)
                    )
                    listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                        ops.send(
                            KafkaConfiguration.TOPIC_NOTIFICATIONS,
                            e.messageId,
                            mapper.writeValueAsString(e)
                        )
                    }
                    Unit
                }
                log.info("{} CONNECTED", message.messageId)
            } catch (e: Exception) {
                val fail = CommunicationStateMachine.failed(message, FailureReason.EnrichmentFailed)
                template.executeInTransaction { ops ->
                    ops.send(
                        KafkaConfiguration.TOPIC_DLQ,
                        message.messageId,
                        mapper.writeValueAsString(fail.updated)
                    )
                    listOfNotNull(fail.stateEvent, fail.statusEvent, fail.logEvent).forEach { ev ->
                        ops.send(
                            KafkaConfiguration.TOPIC_NOTIFICATIONS,
                            ev.messageId,
                            mapper.writeValueAsString(ev)
                        )
                    }
                    Unit
                }
                log.error("{} FAILED connect {}", message.messageId, e.message)
            }
        }
}

@Service
class SendStage(
    private val template: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
    private val outboxRepository: OutboxRepository,
    private val duplicationGate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun begin(message: CommunicationMessage) =
        duplicationGate.withLock(message.messageId) {
            // Transition CONNECTED -> SENDING
            val sending = CommunicationStateMachine.sending(message)

            // Store the full CommunicationMessage in the outbox, serialized
            outboxRepository.save(
                OutboxRecord(
                    messageId = sending.updated.messageId,
                    payload = mapper.writeValueAsString(sending.updated),
                    headers = mapOf("Idempotency-Key" to sending.updated.messageId)
                )
            )

            template.executeInTransaction { ops ->
                // Observability
                listOfNotNull(sending.stateEvent, sending.statusEvent, sending.logEvent)
                    .forEach { e ->
                        ops.send(
                            KafkaConfiguration.TOPIC_NOTIFICATIONS,
                            e.messageId,
                            mapper.writeValueAsString(e)
                        )
                    }

                // Emit updated SENDING message to outbound
                ops.send(
                    KafkaConfiguration.TOPIC_OUTBOUND,
                    sending.updated.messageId,
                    mapper.writeValueAsString(sending.updated)
                )
                Unit
            }

            log.info(
                "{} SENDING from={} to={} type={}",
                sending.updated.messageId,
                sending.updated.fromSubsystem,
                sending.updated.toSubsystem,
                sending.updated.type
            )
        }

    /**
     * Simulated delivery draining the outbox; in a real system use
     * an HTTP/gRPC client here based on msg.toSubsystem / msg.type.
     */
    fun drainOutbox(max: Int = 50) {
        outboxRepository.findPending(max).forEach { rec ->
            // Deserialize full CommunicationMessage
            val msg = mapper.readValue(rec.payload, CommunicationMessage::class.java)

            // TODO: real delivery here (HTTP call, etc.) based on msg.toSubsystem/msg.type

            // Transition SENDING -> SENT
            val tr = CommunicationStateMachine.sent(msg)

            template.executeInTransaction { ops ->
                listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent)
                    .forEach { e ->
                        ops.send(
                            KafkaConfiguration.TOPIC_NOTIFICATIONS,
                            e.messageId,
                            mapper.writeValueAsString(e)
                        )
                    }

                ops.send(
                    KafkaConfiguration.TOPIC_OUTBOUND,
                    tr.updated.messageId,
                    mapper.writeValueAsString(tr.updated)
                )
                Unit
            }

            outboxRepository.markDispatched(rec.messageId)

            log.info(
                "{} SENT from={} to={} type={}",
                rec.messageId,
                msg.fromSubsystem,
                msg.toSubsystem,
                msg.type
            )
        }
    }
}

@Service
class NotifyStage(
    private val mapper: ObjectMapper,
    private val template: KafkaTemplate<String, String>,
    private val duplicationGate: DuplicationGate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun notify(message: CommunicationMessage) =
        duplicationGate.withLock(message.messageId) {
            val tr = CommunicationStateMachine.notified(message)
            template.executeInTransaction { ops ->
                listOfNotNull(tr.stateEvent, tr.statusEvent, tr.logEvent).forEach { e ->
                    ops.send(
                        KafkaConfiguration.TOPIC_NOTIFICATIONS,
                        e.messageId,
                        mapper.writeValueAsString(e)
                    )
                }
            }
            log.info("{} NOTIFIED", message.messageId)
        }
}
