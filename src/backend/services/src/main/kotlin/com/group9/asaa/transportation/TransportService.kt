package com.group9.asaa.transportation

import com.group9.asaa.classes.TransportSystemState
import com.group9.asaa.classes.transport.*
import com.group9.asaa.classes.transport.InMemoryTransportPorts
import com.group9.asaa.classes.transport.OrderQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds

object TransportService {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private const val MAX_CONCURRENT = 2
    private val semaphore = Semaphore(MAX_CONCURRENT)

    private val _orderStates = MutableStateFlow<Map<String, TransportSystemState>>(emptyMap())
    val orderStates: StateFlow<Map<String, TransportSystemState>> = _orderStates

    private fun portsFor(simulateTimeout: Boolean, makeAgvUnavailable: Boolean): InMemoryTransportPorts {
        val confirmation = if (simulateTimeout) 1.seconds else 8.seconds
        return InMemoryTransportPorts(
            confirmationTimeout = confirmation,
            deliveryTimeout = 30.seconds,
            simulateConfirmationFailure = false,
            makeAgvsUnavailable = makeAgvUnavailable
        )
    }

    fun submitOrder(
        order: AssemblyTransportOrder,
        simulateTimeout: Boolean = false,
        makeAgvUnavailable: Boolean = false
    ) {
        serviceScope.launch {
            semaphore.withPermit {
                val ports = portsFor(simulateTimeout, makeAgvUnavailable)
                val sm = TransportStateMachine(this, ports)

                sm.state
                    .onEach { sysState -> _orderStates.update { it + (order.orderId to sysState) } }
                    .collect()

                val result = sm.run(order)

                _orderStates.update { it - order.orderId }
                println("Order ${order.orderId} finished → $result")
            }
        }
    }

    suspend fun receiveOrder(message: Message): String {
        val ports = InMemoryTransportPorts(8.seconds, 30.seconds)
        return ports.receiveOrder(message)
    }

    suspend fun processNextOrder() {
        OrderQueue.priorityDequeueOrder()?.let { order ->
            submitOrder(order)
        }
    }
}
