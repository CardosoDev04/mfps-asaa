package com.group9.asaa.communication.service.sse

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.communication.model.EventEnvelope
import com.group9.asaa.communication.service.kafka.KafkaConfiguration
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseBroadcaster(
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(0L) // no timeout; rely on heartbeat
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        return emitter
    }

    @PostConstruct
    fun startKafkaFanOut() {
        scope.launch {
            val props = Properties().apply {
                put("bootstrap.servers", System.getenv("KAFKA_BOOTSTRAP") ?: "localhost:9092")
                put("group.id", "notify-svc")
                put("key.deserializer", StringDeserializer::class.java)
                put("value.deserializer", StringDeserializer::class.java)
                put("auto.offset.reset", "earliest")
                put("isolation.level", "read_committed")
            }
            val consumer = KafkaConsumer<String, String>(props)
            consumer.subscribe(listOf(KafkaConfiguration.TOPIC_NOTIFICATIONS))
            while (true) {
                val records = consumer.poll(Duration.ofMillis(500))
                for (rec in records) {
                    try {
                        val evt = mapper.readValue(rec.value(), EventEnvelope::class.java)
                        broadcast(evt)
                    } catch (e: Exception) {
                        log.error("Failed to parse notification: {}", e.message)
                    }
                }
            }
        }
        // Heartbeat
        scope.launch {
            while (true) {
                broadcastComment("heartbeat")
                kotlinx.coroutines.delay(15_000)
            }
        }
    }

    private fun broadcast(evt: EventEnvelope) {
        val json = mapper.writeValueAsString(evt)
        emitters.forEach { em ->
            try {
                em.send(SseEmitter.event().id(evt.id).name(evt.eventType).data(json, MediaType.APPLICATION_JSON))
            } catch (e: Exception) {
                emitters.remove(em)
            }
        }
    }

    private fun broadcastComment(comment: String) {
        emitters.forEach { em ->
            try {
                em.send(":" + comment) // colon-prefixed comment line per SSE spec
            } catch (_: Exception) { emitters.remove(em) }
        }
    }
}
