package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Injury(
    @SerialName("id") val id: String,
    @SerialName("playerId") val playerId: String,
    @SerialName("playerName") val playerName: String,
    @SerialName("teamId") val teamId: String,
    @SerialName("teamName") val teamName: String,
    @SerialName("status") val status: String,
    @SerialName("description") val description: String,
    @SerialName("startDate") val startDate: Long,
    @SerialName("endDate") val endDate: Long?,
    @SerialName("lastUpdated") val lastUpdated: Long
)