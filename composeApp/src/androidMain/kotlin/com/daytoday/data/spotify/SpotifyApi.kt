package com.daytoday.data.spotify

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface SpotifyApi {
    @GET("me/playlists")
    suspend fun myPlaylists(@Query("limit") limit: Int = 50): PlaylistsResponse

    @GET("me/player/devices")
    suspend fun devices(): DevicesResponse

    @PUT("me/player/play")
    suspend fun play(@Body body: PlayRequest)

    @PUT("me/player/play")
    suspend fun playOnDevice(@Query("device_id") deviceId: String, @Body body: PlayRequest)
}