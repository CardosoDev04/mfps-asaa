package com.group9.asaa.transportation

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.communication.model.CommunicationMessage
import com.group9.asaa.classes.communication.model.CommunicationState
import com.group9.asaa.communication.service.kafka.KafkaConfiguration
import com.group9.asaa.communication.service.kafka.ReceiveStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class TransportService(
    private val mapper: ObjectMapper,
    private val receiveStage: ReceiveStage
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Listen to outbound messages and react to TRANSPORT_ORDERs that
     * are destined to the "transport" subsystem and are SENT.
     */
    @KafkaListener(
        topics = [KafkaConfiguration.TOPIC_OUTBOUND],
        groupId = "transport-svc"
    )
    fun onOutbound(json: String) {
        try {
            val msg = mapper.readValue(json, CommunicationMessage::class.java)

            // We only care about:
            // - messages for the transport subsystem
            // - that have reached SENT (delivered by comms pipeline)
            // - and represent a TRANSPORT_ORDER
            if (msg.toSubsystem != "transport" ||
                msg.state != CommunicationState.SENT ||
                msg.type.uppercase() != "TRANSPORT_ORDER"
            ) {
                return
            }

            val order = mapper.readValue(msg.payload, AssemblyTransportOrder::class.java)
            val orderId = order.orderId
            log.info("Transport service RECEIVED transport order orderId={} payload={}", orderId, msg.payload)

            // Simulate async handling: confirm, then later signal arrival
            scope.launch {
                // 1) Confirm order quickly
                receiveStage.accept(
                    fromSubsystem = "transport",
                    toSubsystem = "assembly",
                    type = "ORDER_CONFIRMED",
                    payload = """
                        {
                          "orderId": "$orderId",
                          "accepted": true
                        }
                    """.trimIndent(),
                    correlationId = orderId
                )
                log.info("Transport service SENT ORDER_CONFIRMED for orderId={}", orderId)

                // 2) After a small delay, signal that the transport has arrived
                delay(5_000)

                receiveStage.accept(
                    fromSubsystem = "transport",
                    toSubsystem = "assembly",
                    type = "TRANSPORT_ARRIVED",
                    payload = """
                        {
                          "orderId": "$orderId"
                        }
                    """.trimIndent(),
                    correlationId = orderId
                )
                log.info("Transport service SENT TRANSPORT_ARRIVED for orderId={}", orderId)

                // If later you want transport to also decide about validation,
                // you can add a third message type: ASSEMBLY_VALIDATED
                // and handle it in AssemblyCommunicationAdapter.
            }
        } catch (e: Exception) {
            log.error("Transport service failed to handle outbound message: {}", e.message)
        }
    }
}
