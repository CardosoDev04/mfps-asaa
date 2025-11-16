package com.group9.asaa.transportation

import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates
import com.group9.asaa.classes.transport.AGV
import com.group9.asaa.classes.transport.AGVPool
import com.group9.asaa.classes.transport.TransportPorts
import com.group9.asaa.communication.service.kafka.ReceiveStage
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class KafkaTransportPorts(
    private val orderId: String,
    private val correlationId: String?,
    private val receiveStage: ReceiveStage,
    private val events: TransportEventBus? = null,
    private val confirmationDelay: Duration = 1.seconds
) : TransportPorts {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun log(msg: String) {
        log.info("[transport][$orderId] $msg")
        events?.tryEmit(
            TransportEvent(
                kind = "log",
                message = msg,
                orderId = orderId,
                ts = System.currentTimeMillis()
            )
        )
    }

    override suspend fun awaitConfirmation(): Boolean? {
        // purely internal simulation: we don't ask assembly here
        delay(confirmationDelay.inWholeMilliseconds)
        return true // always confirm for now; could be configurable
    }

    override suspend fun acquireAGV(): AGV? = AGVPool.acquire()

    override suspend fun releaseAGV(agv: AGV) = AGVPool.release(agv)

    override suspend fun notifyStatus(state: AssemblyTransportOrderStates) {
        log("Status → $state")

        // Here’s where we can map transport status to assembly-visible events.
        when (state) {
            AssemblyTransportOrderStates.ACCEPTED -> {
                // Already handled via acceptOrder() which sends ORDER_CONFIRMED; no extra message required.
            }

            AssemblyTransportOrderStates.IN_PROGRESS -> {
                // purely internal to transport; don't tell assembly yet
            }

            AssemblyTransportOrderStates.COMPLETED -> {
                receiveStage.accept(
                    fromSubsystem = "transport",
                    toSubsystem = "assembly",
                    type = "TRANSPORT_ARRIVED",
                    payload = """{"orderId":"$orderId"}""",
                    correlationId = correlationId ?: orderId
                )
            }

            else -> {
                // For DENIED/TIMED_OUT/etc. we already map via denyOrder.
            }
        }
    }

    override suspend fun denyOrder(orderId: String) {
        log("Denying order $orderId")
        receiveStage.accept(
            fromSubsystem = "transport",
            toSubsystem = "assembly",
            type = "ORDER_CONFIRMED",
            payload = """{"orderId":"$orderId","accepted":false}""",
            correlationId = correlationId ?: orderId
        )
    }

    override suspend fun acceptOrder(orderId: String) {
        log("Accepting order $orderId")
        receiveStage.accept(
            fromSubsystem = "transport",
            toSubsystem = "assembly",
            type = "ORDER_CONFIRMED",
            payload = """{"orderId":"$orderId","accepted":true}""",
            correlationId = correlationId ?: orderId
        )
    }
}
