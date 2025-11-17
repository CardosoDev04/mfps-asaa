package com.group9.asaa.classes.assembly

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AssemblyTimeouts(
    val confirmationTimeout: Duration = 30.seconds,
    val deliveryTimeout: Duration = 300.seconds,
    val validationTimeout: Duration = 30.seconds
)
