package com.group9.asaa.communication.service.sse

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.communication.EventEnvelope
import com.group9.asaa.communication.service.kafka.KafkaConfiguration
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseBroadcaster(
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    private var consumer: KafkaConsumer<String, String>? = null

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(0L) // no timeout
        emitters.add(emitter)

        fun remove() { emitters.remove(emitter) }

        emitter.onCompletion { remove() }
        emitter.onTimeout { remove() }
        emitter.onError { remove() }

        // Send a proper SSE heartbeat event (NOT raw ":comment" strings)
        scope.launch {
            while (emitters.contains(emitter) && isActive) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("heartbeat")
                            .data("ping", MediaType.TEXT_PLAIN)
                    )
                } catch (_: Exception) {
                    remove()
                    break
                }
                delay(15_000)
            }
        }

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
                // recommended in long-running consumers:
                put("enable.auto.commit", "true")
            }

            KafkaConsumer<String, String>(props).also { consumer = it }.use { c ->
                c.subscribe(listOf(KafkaConfiguration.TOPIC_NOTIFICATIONS))

                while (isActive) {
                    val records = c.poll(Duration.ofMillis(500))
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
        }
    }

    private fun broadcast(evt: EventEnvelope) {
        val dead = mutableListOf<SseEmitter>()

        for (em in emitters) {
            try {
                em.send(
                    SseEmitter.event()
                        .id(evt.id)
                        .name(evt.eventType)
                        .data(evt, MediaType.APPLICATION_JSON)
                )
            } catch (_: Exception) {
                dead += em
            }
        }

        if (dead.isNotEmpty()) emitters.removeAll(dead)
    }

    @PreDestroy
    fun shutdown() {
        try {
            consumer?.wakeup()
        } catch (_: Exception) {
        }

        // complete all emitters so requests finish cleanly
        for (em in emitters) {
            try { em.complete() } catch (_: Exception) {}
        }
        emitters.clear()

        job.cancel()
    }
}
