package com.group9.asaa.transport

// ================================
// Domain Classes
// ================================
// File 1: Domain.kt (UPDATED)

// ================================
// Domain Classes
// ================================
data class Component(val name: String)

data class AssemblyTransportOrder(
    val orderId: String,
    val components: List<Component>,
    val deliveryLocation: Locations
)

enum class AssemblyTransportOrderStates {
    ACCEPTED, DENIED, IN_PROGRESS, COMPLETED
}

enum class Locations(val estimatedTimeFromWarehouseInMinutes: Int) {
    ASSEMBLY_LINE_A(3), ASSEMBLY_LINE_B(5), ASSEMBLY_LINE_C(4)
}

// ================================
// Message & Queue (NEW)
// ================================
enum class MessageTypes { GENERIC, ALERT, ORDER_CONFIRMATION }

data class Message(
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val messageType: MessageTypes = MessageTypes.GENERIC
)

object OrderQueue {
    private val queue = java.util.ArrayDeque<AssemblyTransportOrder>()

    fun queueOrder(order: AssemblyTransportOrder): Boolean = queue.offer(order)

    fun priorityDequeueOrder(): AssemblyTransportOrder? = queue.pollFirst()

    fun queueOrder(orderId: String): AssemblyTransportOrder? = queue.find { it.orderId == orderId }

    fun clear() = queue.clear()
}