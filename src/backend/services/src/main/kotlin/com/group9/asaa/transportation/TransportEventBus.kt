package com.group9.asaa.transportation

import com.group9.asaa.classes.transport.TransportEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.springframework.stereotype.Service

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
