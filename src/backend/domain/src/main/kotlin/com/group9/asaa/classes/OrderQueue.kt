package com.group9.asaa.classes.transport

import com.group9.asaa.classes.transport.AssemblyTransportOrder
import java.util.ArrayDeque

object OrderQueue {
    private val queue = ArrayDeque<AssemblyTransportOrder>()

    fun queueOrder(order: AssemblyTransportOrder): Boolean = queue.offer(order)

    fun priorityDequeueOrder(): AssemblyTransportOrder? = queue.pollFirst()

    fun findOrder(orderId: String): AssemblyTransportOrder? = queue.find { it.orderId == orderId }

    fun clear() = queue.clear()
}