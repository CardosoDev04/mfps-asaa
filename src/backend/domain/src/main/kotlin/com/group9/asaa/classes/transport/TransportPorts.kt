package com.group9.asaa.classes.transport

import com.group9.asaa.classes.assembly.AssemblyTransportOrderStates

interface TransportPorts {
    suspend fun awaitConfirmation(): Boolean?
    suspend fun acquireAGV(): AGV?
    suspend fun releaseAGV(agv: AGV)
    suspend fun log(msg: String)
    suspend fun notifyStatus(state: AssemblyTransportOrderStates)
    suspend fun denyOrder(orderId: String)
    suspend fun acceptOrder(orderId: String)
}
