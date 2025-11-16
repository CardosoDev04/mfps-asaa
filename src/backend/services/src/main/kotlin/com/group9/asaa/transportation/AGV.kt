package com.group9.asaa.transport

// ================================
// AGV + Supporting Structures
// ================================
enum class AGVState { AVAILABLE, UNAVAILABLE, FAULTY }

data class AGV(
    val id: String,
    public var pickupPlace: Locations? = null,
    public var isWorking: Boolean = false,
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

// Immutable pool – recreated when we need to reset for tests
object AGVPool {
    private val pool = mutableListOf(
        AGV("AGV-1"), AGV("AGV-2"), AGV("AGV-3")
    )

    fun snapshot(): List<AGV> = pool.toList()

    fun acquire(): AGV? = pool.firstOrNull { it.state == AGVState.AVAILABLE }?.apply { state = AGVState.UNAVAILABLE }

    fun release(agv: AGV) {
        agv.state = AGVState.AVAILABLE
    }

    fun makeAllUnavailable() = pool.forEach { it.state = AGVState.UNAVAILABLE }

    fun checkAvailability(): List<AGV> = pool.filter { it.state == AGVState.AVAILABLE }

    fun chooseAGV(availableAgvs: List<AGV>): AGV? = availableAgvs.firstOrNull()

    fun loadOrderToAGV(agv: AGV): Boolean = agv.state == AGVState.AVAILABLE && agv.pickUpParts().state == AGVState.UNAVAILABLE

    fun startAGVOrder(agv: AGV): Boolean = agv.state == AGVState.UNAVAILABLE && agv.isWorking
}