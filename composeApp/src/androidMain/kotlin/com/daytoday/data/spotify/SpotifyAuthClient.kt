package com.daytoday.data.spotify

import javax.inject.Inject
import javax.inject.Singleton

interface SpotifyAuthClient {
    suspend fun exchangeCode(code: String, redirectUri: String, clientId: String, codeVerifier: String): TokenResponse

    suspend fun refreshToken(refreshToken: String, clientId: String): TokenResponse
}

@Singleton
class SpotifyAuthClientImpl @Inject constructor(
    private val service: SpotifyAuthService,
) : SpotifyAuthClient {

    override suspend fun exchangeCode(
        code: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String,
    ): TokenResponse = service.token(
        grantType = "authorization_code",
        code = code,
        redirectUri = redirectUri,
        clientId = clientId,
        codeVerifier = codeVerifier,
        refreshToken = null,
    )

    override suspend fun refreshToken(refreshToken: String, clientId: String): TokenResponse = service.token(
        grantType = "refresh_token",
        code = null,
        redirectUri = null,
        clientId = clientId,
        codeVerifier = null,
        refreshToken = refreshToken,
    )
}