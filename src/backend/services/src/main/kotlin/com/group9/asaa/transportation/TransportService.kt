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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class TransportService(
    private val mapper: ObjectMapper,
    private val receiveStage: ReceiveStage,
    private val events: TransportEventBus
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val concurrency = Semaphore(3)

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

            if (
                msg.toSubsystem != "transport" ||
                msg.state != CommunicationState.SENT ||
                msg.type.uppercase() != "TRANSPORT_ORDER"
            ) {
                return
            }

            val order = mapper.readValue(msg.payload, AssemblyTransportOrder::class.java)
            val orderId = order.orderId
            log.info(
                "TransportService RECEIVED transport order orderId={} payload={} correlationId={}",
                orderId,
                msg.payload,
                msg.correlationId
            )

            scope.launch {
                concurrency.withPermit {
                    val ports = KafkaTransportPorts(
                        orderId = orderId,
                        correlationId = msg.correlationId,
                        receiveStage = receiveStage,
                        events = events
                    )
                    val sm = TransportStateMachine(ports, events)

                    val result = sm.run(order)
                    log.info(
                        "TransportService COMPLETED orderId={} finalSystemState={} reportedOrderState={}",
                        orderId,
                        result.finalSystemState,
                        result.reportedOrderState
                    )
                }
            }
        } catch (e: Exception) {
            log.error("TransportService failed to handle outbound message: {}", e.message, e)
        }
    }
}
