package com.group9.asaa.classes.transport

import com.group9.asaa.classes.transport.Locations

enum class AGVState { AVAILABLE, UNAVAILABLE, FAULTY }

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

    fun deliverParts() = apply { state = AGVState.UNAVAILABLE }

    fun returnHome() = apply {
        state = AGVState.AVAILABLE
        isWorking = false
        pickupPlace = null
    }

    fun setPickup(location: Locations) {
        pickupPlace = location
    }
}