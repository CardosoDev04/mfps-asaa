package com.group9.asaa.assembly.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.communication.model.CommunicationMessage
import com.group9.asaa.classes.communication.model.CommunicationState
import com.group9.asaa.communication.service.kafka.KafkaConfiguration
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class AssemblyCommunicationAdapter(
    private val mapper: ObjectMapper,
    private val assemblyService: AssemblyService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaConfiguration.TOPIC_OUTBOUND],
        groupId = "assembly-adapter"
    )
    fun onOutbound(json: String) {
        try {
            val msg = mapper.readValue(json, CommunicationMessage::class.java)

            // Only care about messages that:
            // - are destined to assembly
            // - reached SENT state (delivered by comms system)
            if (msg.toSubsystem != "assembly" || msg.state != CommunicationState.SENT) {
                return
            }

            // Domain payload: we expect small JSON events like:
            // { "type": "ORDER_CONFIRMED", "orderId": "...", "accepted": true }
            // { "type": "TRANSPORT_ARRIVED", "orderId": "..." }
            // { "type": "ASSEMBLY_VALIDATED", "orderId": "...", "valid": true }
            val node = mapper.readTree(msg.payload)
            when (val type = msg.type.uppercase()) {
                "ORDER_CONFIRMED" -> {
                    val orderId = node.get("orderId").asText()
                    val accepted = node.get("accepted").asBoolean()
                    assemblyService.confirmOrder(orderId, accepted)
                    log.info("ORDER_CONFIRMED for orderId={} accepted={}", orderId, accepted)
                }

                "TRANSPORT_ARRIVED" -> {
                    val orderId = node.get("orderId").asText()
                    assemblyService.signalTransportArrived(orderId)
                    log.info("TRANSPORT_ARRIVED for orderId={}", orderId)
                }

                "ASSEMBLY_VALIDATED" -> {
                    val orderId = node.get("orderId").asText()
                    val valid = node.get("valid").asBoolean()
                    assemblyService.validateAssembly(orderId, valid)
                    log.info("ASSEMBLY_VALIDATED for orderId={} valid={}", orderId, valid)
                }

                else -> {
                    log.debug("Ignoring message for assembly with unsupported type={}", type)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to handle outbound message for assembly: {}", e.message)
        }
    }
}
