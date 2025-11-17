package com.group9.asaa.classes.transport
import com.group9.asaa.misc.Locations

data class AGV(
    val id: String,
    var pickupPlace: Locations? = null,
    var isWorking: Boolean = false,
    var state: AGVState = AGVState.AVAILABLE
) {
    fun pickUpParts() = apply {
        state = AGVState.UNAVAILABLE
        isWorking = true
    }
}
