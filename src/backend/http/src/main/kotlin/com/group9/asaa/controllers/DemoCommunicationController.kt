package com.group9.asaa.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.group9.asaa.communication.service.kafka.ReceiveStage
import com.group9.asaa.communication.service.sse.SseBroadcaster
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/comm-demo")
class DemoCommunicationController(
    private val receiveStage: ReceiveStage,
    private val sseBroadcaster: SseBroadcaster,
    private val mapper: ObjectMapper,
) {
    data class PostMessageRequest(val subsystem: String, val payload: String, val correlationId: String? = null)
    data class PostMessageResponse(val messageId: String, val status: String = "accepted")

    @PostMapping("/messages")
    fun post(@RequestBody body: PostMessageRequest): ResponseEntity<PostMessageResponse> {
        val id = receiveStage.accept(body.subsystem, body.payload, body.correlationId)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PostMessageResponse(id))
    }

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@RequestHeader(name = "Last-Event-ID", required = false) lastId: String?): SseEmitter {
        return sseBroadcaster.subscribe()
    }
}
