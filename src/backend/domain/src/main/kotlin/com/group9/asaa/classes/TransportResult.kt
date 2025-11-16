package com.group9.asaa.classes.transport

import com.group9.asaa.classes.transport.AssemblyTransportOrder
import com.group9.asaa.classes.transport.AssemblyTransportOrderStates

data class TransportResult(
    val order: AssemblyTransportOrder,
    val finalSystemState: TransportSystemState,
    val reportedOrderState: AssemblyTransportOrderStates
)