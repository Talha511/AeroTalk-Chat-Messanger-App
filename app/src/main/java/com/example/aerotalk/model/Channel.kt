package com.example.aerotalk.model

data class Channel(
    val id: String = "",
    val name: String= "Unkown",
    val createdAt: Long = System.currentTimeMillis(),
)

