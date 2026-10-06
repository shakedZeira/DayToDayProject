package com.daytoday.data.spotify

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface SpotifyAuthService {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun token(
        @Field("grant_type") grantType: String,
        @Field("code") code: String?,
        @Field("redirect_uri") redirectUri: String?,
        @Field("client_id") clientId: String?,
        @Field("code_verifier") codeVerifier: String?,
        @Field("refresh_token") refreshToken: String?,
    ): TokenResponse
}