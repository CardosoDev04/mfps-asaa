package com.group9.asaa.assembly.service

import com.group9.asaa.classes.assembly.AssemblySystemStates
import com.group9.asaa.classes.assembly.AssemblyTransportOrder
import com.group9.asaa.classes.assembly.Blueprint
import kotlinx.coroutines.flow.StateFlow

interface IAssemblyService {
    suspend fun createOrder(orderBlueprint: Blueprint, demo: Boolean = false): AssemblyTransportOrder

    fun observeAssemblySystemState(): StateFlow<AssemblySystemStates>
    fun getAssemblySystemState(): AssemblySystemStates
    fun queueSize(): StateFlow<Int>

    fun confirmCurrentOrder(accepted: Boolean)
    fun signalTransportArrived()
    fun validateAssembly(valid: Boolean)

    fun demoAutopilot(acceptAfterMs: Long, deliverAfterMs: Long, validateAfterMs: Long, valid: Boolean)
}
