package com.daytoday.data.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
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
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class DevicesResponse(
    val devices: List<PlaybackDevice> = emptyList(),
)

@Serializable
data class PlayRequest(
    @SerialName("context_uri") val contextUri: String,
)