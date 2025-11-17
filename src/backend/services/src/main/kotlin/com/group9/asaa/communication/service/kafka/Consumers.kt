package com.group9.asaa.communication.service.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.communication.model.CommunicationMessage
import com.group9.asaa.classes.communication.model.CommunicationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled

/**
 * Kafka listeners invoking pipeline stages. Bridges blocking Kafka thread with coroutines.
 */
@Service
class CommunicationConsumers(
    private val mapper: ObjectMapper,
    private val connectStage: ConnectStage,
    private val sendStage: SendStage,
    private val notifyStage: NotifyStage
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)

    @KafkaListener(topics = [KafkaConfiguration.TOPIC_INBOUND], groupId = "connect-svc")
    fun onInbound(json: String) {
        scope.launch {
            try {
                val msg = mapper.readValue(json, CommunicationMessage::class.java)
                connectStage.process(msg)
            } catch (e: Exception) {
                log.error("Failed inbound consume: {}", e.message)
            }
        }
    }

    @KafkaListener(topics = [KafkaConfiguration.TOPIC_CONNECTED], groupId = "send-svc")
    fun onConnected(json: String) {
        scope.launch {
            try {
                val msg = mapper.readValue(json, CommunicationMessage::class.java)
                sendStage.begin(msg)
            } catch (e: Exception) {
                log.error("Failed connected consume: {}", e.message)
            }
        }
    }

    @KafkaListener(topics = [KafkaConfiguration.TOPIC_OUTBOUND], groupId = "notify-svc")
    fun onOutbound(json: String) {
        scope.launch {
            try {
                val msg = mapper.readValue(json, CommunicationMessage::class.java)
                if (msg.state == CommunicationState.SENT) {
                    notifyStage.notify(msg)
                }
            } catch (e: Exception) {
                log.error("Failed outbound consume: {}", e.message)
            }
        }
    }

    // Periodic outbox drain simulating delivery worker
    @Scheduled(fixedDelay = 200)
    fun drainOutbox() {
        sendStage.drainOutbox()
    }
}

