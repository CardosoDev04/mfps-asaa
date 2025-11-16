package com.group9.asaa.transportation

import com.group9.asaa.classes.transport.TransportSystemState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.springframework.stereotype.Service

data class TransportEvent(
    val kind: String, // "state" | "status" | "log"
    val message: String? = null,
    val state: TransportSystemState? = null,
    val orderId: String? = null,
    val ts: Long = System.currentTimeMillis()
)

@Service
class TransportEventBus {
    private val _events = MutableSharedFlow<TransportEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun events(): SharedFlow<TransportEvent> = _events.asSharedFlow()

    fun tryEmit(ev: TransportEvent) {
        _events.tryEmit(ev)
    }
}
