package com.group9.asaa.communication.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.group9.asaa.communication.service.outbox.DuplicationGate
import com.group9.asaa.communication.service.outbox.InMemoryDuplicationGate
import com.group9.asaa.communication.service.outbox.InMemoryOutboxRepository
import com.group9.asaa.communication.service.outbox.OutboxRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CommunicationBeansConfig {
    @Bean fun objectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    @Bean fun duplicationGate(): DuplicationGate = InMemoryDuplicationGate()
    @Bean fun outboxRepository(): OutboxRepository = InMemoryOutboxRepository()
}

