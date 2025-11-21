package com.example.aerotalk.model


data class Message(
    val id: String = "",
    val senderId: String = "",
    val message: String? = "",
    val createdAt: Long = System.currentTimeMillis(),
    val senderName: String = "",
    val senderImage: String? = null,
    val imageUrl: String? = null,

    // NEW fields
    val status: String = "sent",   // "sent" | "delivered" | "seen"
    val replyToId: String? = null,
    val replyToMessage: String? = null
)
