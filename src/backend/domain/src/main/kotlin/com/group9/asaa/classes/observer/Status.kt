package com.group9.asaa.classes.observer

import java.time.Instant

data class Status(
    val id: String? = null,
    val statusText: String,
    val setAt: Instant,
    val testRunId: String? = null
)
