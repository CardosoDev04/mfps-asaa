package com.group9.asaa.classes.assembly

data class Blueprint(
    val id: String,
    val name: String,
    val components: List<Component>,
    val attachments: List<ComponentAttachment>
)
