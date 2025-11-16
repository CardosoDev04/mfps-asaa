package com.group9.asaa.controllers

import com.group9.asaa.transportation.TransportEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/transport")
class TransportEventsController(
    private val eventBus: TransportEventBus
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter {
        val emitter = SseEmitter(0L) // no timeout; rely on client disconnect

        scope.launch {
            try {
                eventBus.events().collect { ev ->
                    emitter.send(
                        SseEmitter.event()
                            .name(ev.kind) // "state" | "status" | "log"
                            .data(ev)
                    )
                }
                emitter.complete()
            } catch (t: Throwable) {
                emitter.completeWithError(t)
            }
        }

        return emitter
    }
}
