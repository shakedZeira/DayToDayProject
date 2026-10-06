package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class NbaNews(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("summary") val summary: String,
    @SerialName("url") val url: String,
    @SerialName("imageUrl") val imageUrl: String?,
    @SerialName("source") val source: String,
    @SerialName("publishedAt") val publishedAt: Long,
    @SerialName("teamIds") val teamIds: List<String>
)