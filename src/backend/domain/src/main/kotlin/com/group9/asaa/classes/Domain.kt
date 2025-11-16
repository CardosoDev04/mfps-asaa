package com.group9.asaa.classes.transport

data class Component(val name: String)

data class AssemblyTransportOrder(
    val orderId: String,
    val components: List<Component>,
    val deliveryLocation: Locations
)

enum class AssemblyTransportOrderStates {
    ACCEPTED, DENIED, IN_PROGRESS, COMPLETED
}

enum class Locations(val estimatedTimeFromWarehouseInMinutes: Int) {
    ASSEMBLY_LINE_A(3), ASSEMBLY_LINE_B(5), ASSEMBLY_LINE_C(4)
}