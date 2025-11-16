package com.group9.asaa.classes.transport

import com.group9.asaa.classes.transport.AGV
import com.group9.asaa.classes.transport.Locations

object AGVPool {
    private val pool = mutableListOf(
        AGV("AGV-1"), AGV("AGV-2"), AGV("AGV-3")
    )

    fun snapshot(): List<AGV> = pool.toList()

    fun acquire(): AGV? =
        pool.firstOrNull { it.state == AGVState.AVAILABLE }?.apply { state = AGVState.UNAVAILABLE }

    fun release(agv: AGV) {
        agv.state = AGVState.AVAILABLE
    }

    fun makeAllUnavailable() = pool.forEach { it.state = AGVState.UNAVAILABLE }

    fun checkAvailability(): List<AGV> = pool.filter { it.state == AGVState.AVAILABLE }

    fun chooseAGV(availableAgvs: List<AGV>): AGV? = availableAgvs.firstOrNull()

    fun loadOrderToAGV(agv: AGV): Boolean =
        agv.state == AGVState.AVAILABLE && agv.pickUpParts().state == AGVState.UNAVAILABLE

    fun startAGVOrder(agv: AGV): Boolean = agv.state == AGVState.UNAVAILABLE && agv.isWorking
}