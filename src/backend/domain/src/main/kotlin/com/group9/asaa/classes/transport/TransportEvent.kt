package com.group9.asaa.classes.transport

data class TransportEvent(
    val kind: String, // "state" | "status" | "log"
    val message: String? = null,
    val state: TransportSystemState? = null,
    val orderId: String? = null,
    val ts: Long = System.currentTimeMillis()
)
