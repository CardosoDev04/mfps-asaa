package com.group9.asaa.controllers

import com.group9.asaa.assembly.service.AssemblyService
import com.group9.asaa.assembly.service.IAssemblyService
import com.group9.asaa.classes.assembly.*
import kotlinx.coroutines.launch
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/assembly")
class AssemblyController(
    private val assemblyService: IAssemblyService
) {
    @PostMapping("/transport-order")
    suspend fun createTransportOrder(
        @RequestParam(defaultValue = "false") demo: Boolean
    ): ResponseEntity<AssemblyTransportOrder> {
        val dummyBlueprint = Blueprint(
            id = "bp-001",
            name = "Sample Blueprint",
            components = emptyList(),
            attachments = emptyList()
        )
        val transportOrder = assemblyService.createOrder(dummyBlueprint, demo)
        return ResponseEntity.ok(transportOrder)
    }

    @PostMapping("/transport-order/bulk")
    suspend fun bulk(
        @RequestParam(defaultValue = "10") n: Int,
        @RequestParam(defaultValue = "false") demo: Boolean
    ): ResponseEntity<String> {
        repeat(n) { idx ->
            val bp = Blueprint(
                id = "bp-${System.currentTimeMillis()}-$idx",
                name = "Bulk-$idx",
                components = emptyList(),
                attachments = emptyList()
            )
            assemblyService.createOrder(bp, demo)
        }
        return ResponseEntity.ok("Enqueued $n orders (demo=$demo)")
    }

    @GetMapping("/queue-size")
    fun getQueueSize(): ResponseEntity<Int> =
        ResponseEntity.ok(assemblyService.queueSize().value)

    @GetMapping("/system-state")
    fun getSystemState(): ResponseEntity<AssemblySystemStates> =
        ResponseEntity.ok(assemblyService.getAssemblySystemState())

    @PutMapping("/confirm-order")
    fun confirmCurrentOrder(@RequestParam(defaultValue = "true") accepted: Boolean): ResponseEntity<Void> {
        assemblyService.confirmCurrentOrder(accepted = accepted)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/signal-transport-arrived")
    fun signalTransportArrived(): ResponseEntity<Void> {
        assemblyService.signalTransportArrived()
        return ResponseEntity.ok().build()
    }

    @PutMapping("/validate-assembly")
    fun validateAssembly(@RequestParam(defaultValue = "true") valid: Boolean): ResponseEntity<Void> {
        assemblyService.validateAssembly(valid = valid)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/events")
    fun streamEvents(): SseEmitter {
        val emitter = SseEmitter(0L)
        val svc = assemblyService as? AssemblyService
            ?: return emitter.also { it.completeWithError(IllegalStateException("AssemblyService required")) }

        val scopeField = svc.javaClass.getDeclaredField("scope").also { it.isAccessible = true }
        val scope = scopeField.get(svc) as kotlinx.coroutines.CoroutineScope

        scope.launch {
            try {
                svc.observeEvents().collect { ev: AssemblyEvent ->
                    emitter.send(SseEmitter.event().name(ev.kind).data(ev))
                }
                emitter.complete()
            } catch (t: Throwable) {
                emitter.completeWithError(t)
            }
        }
        return emitter
    }
}
