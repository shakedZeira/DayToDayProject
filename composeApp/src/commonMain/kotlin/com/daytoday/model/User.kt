package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class User(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String,
    @SerialName("createdAt") val createdAt: Long
)