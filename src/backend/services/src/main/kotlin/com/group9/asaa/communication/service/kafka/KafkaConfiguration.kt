package com.group9.asaa.communication.service.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.transaction.KafkaTransactionManager
import java.util.*

@Configuration
@EnableKafka
class KafkaConfiguration {
    companion object {
        const val TOPIC_INBOUND = "mfps.messages.inbound"
        const val TOPIC_CONNECTED = "mfps.messages.connected"
        const val TOPIC_OUTBOUND = "mfps.messages.outbound"
        const val TOPIC_NOTIFICATIONS = "mfps.notifications"
        const val TOPIC_DLQ = "mfps.messages.dlq"
    }

    @Bean
    fun kafkaProps(): Map<String, Any> = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to (System.getenv("KAFKA_BOOTSTRAP") ?: "localhost:9092"),
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.GROUP_ID_CONFIG to "receive-svc", // overridden where needed
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
        ProducerConfig.RETRIES_CONFIG to 10,
        ProducerConfig.TRANSACTIONAL_ID_CONFIG to "connect-svc-tx-${UUID.randomUUID()}"
    )

    @Bean
    fun producerFactory(): ProducerFactory<String, String> = DefaultKafkaProducerFactory(kafkaProps())

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> = KafkaTemplate(producerFactory())

    @Bean
    fun transactionManager(): KafkaTransactionManager<String, String> = KafkaTransactionManager(producerFactory())

    @Bean fun inboundTopic() = NewTopic(TOPIC_INBOUND, 6, 1)
    @Bean fun connectedTopic() = NewTopic(TOPIC_CONNECTED, 6, 1)
    @Bean fun outboundTopic() = NewTopic(TOPIC_OUTBOUND, 6, 1)
    @Bean fun notificationsTopic() = NewTopic(TOPIC_NOTIFICATIONS, 6, 1)
    @Bean fun dlqTopic() = NewTopic(TOPIC_DLQ, 6, 1)

    @Bean
    fun kafkaListenerContainerFactory(cf: ConsumerFactory<String, String>, tm: KafkaTransactionManager<String, String>): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = cf
        factory.isBatchListener = false
        factory.setConcurrency(3)
        factory.setCommonErrorHandler(org.springframework.kafka.listener.DefaultErrorHandler())
        factory.setAfterRollbackProcessor(org.springframework.kafka.listener.DefaultAfterRollbackProcessor())
        // Removed factory.setTransactionManager(tm) due to API mismatch; transactional send handled via KafkaTemplate.executeInTransaction in ConnectStage.
        return factory
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> = DefaultKafkaConsumerFactory(kafkaProps())
}
