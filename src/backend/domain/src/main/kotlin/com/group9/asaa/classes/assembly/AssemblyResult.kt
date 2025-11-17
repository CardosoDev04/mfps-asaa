package com.group9.asaa.classes.assembly

data class AssemblyResult(
    val order: AssemblyTransportOrder,
    val finalSystemState: AssemblySystemStates,
    val reportedOrderState: AssemblyTransportOrderStates
)
