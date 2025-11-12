package com.group9.asaa.classes.assembly

data class AssemblyEvent(
    val kind: String,                       // "state" | "status" | "log"
    val message: String? = null,            // for "status" or "log"
    val state: AssemblySystemStates? = null,// for "state"
    val orderId: String? = null,
    val ts: Long = System.currentTimeMillis()
)
