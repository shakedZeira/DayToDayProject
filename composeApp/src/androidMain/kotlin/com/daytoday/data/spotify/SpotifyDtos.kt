package com.daytoday.data.spotify

import com.squareup.moshi.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @Json(name = "access_token")
    @SerialName("access_token") val accessToken: String,
    @Json(name = "token_type")
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @Json(name = "expires_in")
    @SerialName("expires_in") val expiresIn: Long,
    @Json(name = "refresh_token")
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val uri: String,
    val owner: Owner = Owner(""),
    val tracks: Tracks = Tracks(0),
) {
    @Serializable
    data class Owner(val id: String = "")

    @Serializable
    data class Tracks(val total: Int = 0)
}

@Serializable
data class PlaylistsResponse(
    val items: List<SpotifyPlaylist> = emptyList(),
)

@Serializable
data class PlaybackDevice(
    val id: String? = null,
    val name: String = "",
    @Json(name = "is_active")
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class DevicesResponse(
    val devices: List<PlaybackDevice> = emptyList(),
)

@Serializable
data class PlayRequest(
    @Json(name = "context_uri")
    @SerialName("context_uri") val contextUri: String,
)