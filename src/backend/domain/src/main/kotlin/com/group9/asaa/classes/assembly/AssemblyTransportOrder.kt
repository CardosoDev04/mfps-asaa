package com.group9.asaa.classes.assembly

import com.group9.asaa.misc.Locations

data class AssemblyTransportOrder(
    val orderId: String,
    val components: List<Component>,
    val deliveryLocation: Locations
)
