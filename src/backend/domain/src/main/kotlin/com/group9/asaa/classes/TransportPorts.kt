package com.group9.asaa.classes.transport

import com.group9.asaa.classes.transport.*
import com.group9.asaa.classes.transport.OrderQueue
import kotlin.time.Duration

interface TransportPorts {
    suspend fun sendOrder(order: AssemblyTransportOrder)
    suspend fun awaitConfirmation(): Boolean?
    suspend fun acquireAGV(): AGV?
    suspend fun performTransport(agv: AGV, location: Locations)
    suspend fun releaseAGV(agv: AGV)
    suspend fun log(msg: String)
    suspend fun notifyStatus(state: AssemblyTransportOrderStates)
    suspend fun receiveOrder(message: Message): String
    suspend fun decodeOrderFromMessage(message: Message): AssemblyTransportOrder
    suspend fun denyOrder(orderId: String)
    suspend fun acceptOrder(orderId: String)
}

class InMemoryTransportPorts(
    private val confirmationTimeout: Duration,
    private val deliveryTimeout: Duration,
    private val simulateConfirmationFailure: Boolean = false,
    private val makeAgvsUnavailable: Boolean = false
) : TransportPorts {

    init { if (makeAgvsUnavailable) AGVPool.makeAllUnavailable() }

    override suspend fun sendOrder(order: AssemblyTransportOrder) {
        log("Sent order ${order.orderId}")
    }

    override suspend fun awaitConfirmation(): Boolean? {
        kotlinx.coroutines.delay(confirmationTimeout.inWholeMilliseconds / 2)
        return if (simulateConfirmationFailure) false else true
    }

    override suspend fun acquireAGV(): AGV? = AGVPool.acquire()

    override suspend fun performTransport(agv: AGV, location: Locations) {
        log("[${agv.id}] picking up parts …")
        kotlinx.coroutines.delay(500)
        log("[${agv.id}] delivering to ${location} (≈${location.estimatedTimeFromWarehouseInMinutes} min) …")
        kotlinx.coroutines.delay(location.estimatedTimeFromWarehouseInMinutes * 60_000L)
        log("[${agv.id}] returning home …")
        kotlinx.coroutines.delay(500)
    }

    override suspend fun releaseAGV(agv: AGV) = AGVPool.release(agv)

    override suspend fun log(msg: String) = println("[Transport] $msg")

    override suspend fun notifyStatus(state: AssemblyTransportOrderStates) {
        log("Status → $state")
    }

    override suspend fun receiveOrder(message: Message): String {
        return try {
            val order = decodeOrderFromMessage(message)
            if (OrderQueue.queueOrder(order)) {
                acceptOrder(order.orderId)
                "Order ${order.orderId} queued"
            } else {
                denyOrder(order.orderId)
                "Order ${order.orderId} rejected"
            }
        } catch (e: Exception) {
            denyOrder(message.content.take(8))
            "Invalid order"
        }
    }

    override suspend fun decodeOrderFromMessage(message: Message): AssemblyTransportOrder {
        val parts = message.content.split("|")
        require(parts.size >= 3) { "Invalid message format" }
        return AssemblyTransportOrder(
            orderId = parts[0],
            components = parts[1].split(",").map { Component(it.trim()) },
            deliveryLocation = Locations.valueOf(parts[2])
        )
    }

    override suspend fun denyOrder(orderId: String) {
        notifyStatus(AssemblyTransportOrderStates.DENIED)
        log("Order $orderId DENIED")
    }

    override suspend fun acceptOrder(orderId: String) {
        notifyStatus(AssemblyTransportOrderStates.ACCEPTED)
        log("Order $orderId ACCEPTED")
    }
}