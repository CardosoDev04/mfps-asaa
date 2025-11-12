package com.group9.asaa.misc

enum class MessageTypes {
    GENERIC,
    ALERT,
    ORDER_CONFIRMATION
}

data class Message(
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val messageType: MessageTypes = MessageTypes.GENERIC
)
