package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.AssemblySystemStates
import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.Blueprint
import com.group9.asaa.misc.Locations
import kotlinx.coroutines.flow.StateFlow

interface IAssemblyService {
    suspend fun createOrder(orderBlueprint: Blueprint, demo: Boolean = false, testRunId: String? = null): AssemblyTransportOrder

    fun observeAssemblySystemState(): StateFlow<AssemblySystemStates>
    fun getAssemblySystemState(): AssemblySystemStates
    fun queueSize(): StateFlow<Int>

    fun confirmOrder(orderId: String, accepted: Boolean)
    fun signalTransportArrived(orderId: String)
    fun validateAssembly(orderId: String, valid: Boolean)

    fun demoAutopilot(acceptAfterMs: Long, deliverAfterMs: Long, validateAfterMs: Long, valid: Boolean)
}
