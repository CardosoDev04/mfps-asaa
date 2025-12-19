package com.group9.asaa.controllers

import com.group9.asaa.communication.service.kafka.ReceiveStage
import com.group9.asaa.communication.service.sse.SseBroadcaster
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/communication")
class CommunicationController(
    private val receiveStage: ReceiveStage,
    private val sseBroadcaster: SseBroadcaster
) {
    data class PostMessageRequest(
        val toSubsystem: String,
        val fromSubsystem: String,
        val payload: String,
        val correlationId: String? = null,
        val type: String
    )
    data class PostMessageResponse(val messageId: String, val status: String = "accepted")

    @PostMapping("/messages")
    fun post(@RequestBody body: PostMessageRequest): ResponseEntity<PostMessageResponse> {
        val id = receiveStage.accept(
            body.fromSubsystem,
            body.toSubsystem,
            body.type,
            body.payload,
            body.correlationId
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PostMessageResponse(id))
    }

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @RequestHeader(name = "Last-Event-ID", required = false) lastId: String?,
        response: HttpServletResponse
    ): SseEmitter {
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Connection", "keep-alive")
        response.setHeader("X-Accel-Buffering", "no")

        return sseBroadcaster.subscribe()
    }
}
