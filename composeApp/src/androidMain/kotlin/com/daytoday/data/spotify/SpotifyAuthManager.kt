package com.daytoday.data.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.daytoday.BuildConfig
import com.daytoday.settings.SettingsManager
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SpotifyAuthManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val authClient: SpotifyAuthClient,
) {
    private val random = SecureRandom()

    fun generateCodeVerifier(): String = randomOpaque()

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, BASE64_FLAGS)
    }

    fun generateState(): String = randomOpaque()

    fun loginUrl(verifier: String, state: String): String =
        Uri.parse(BuildConfig.SPOTIFY_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

    suspend fun exchangeCode(context: Context, code: String, state: String, verifier: String): Boolean =
        runCatching {
            val response = authClient.exchangeCode(
                code = code,
                redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
                clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                codeVerifier = verifier,
            )
            settingsManager.saveSpotifyTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAtMs = System.currentTimeMillis() + response.expiresIn * MILLIS_PER_SECOND,
            )
            settingsManager.setSpotifyLoggedIn(true)
        }.isSuccess

    suspend fun ensureFreshToken(): Boolean {
        val expiresAtMs = settingsManager.spotifyTokenExpiresAtMs.first()
        if (expiresAtMs - System.currentTimeMillis() > REFRESH_AHEAD_MS) {
            return true
        }
        val refreshToken = settingsManager.spotifyRefreshToken.first()
        if (refreshToken.isNullOrBlank()) {
            return false
        }
        return runCatching {
            val response = authClient.refreshToken(
                refreshToken = refreshToken,
                clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            )
            settingsManager.saveSpotifyTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresAtMs = System.currentTimeMillis() + response.expiresIn * MILLIS_PER_SECOND,
            )
            settingsManager.setSpotifyLoggedIn(true)
        }.isSuccess
    }

    private fun randomOpaque(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    companion object {
        private const val TAG = "DayTodaySpotifyAuth"
        const val SCOPES =
            "user-modify-playback-state user-read-playback-state playlist-read-private playlist-read-collaborative user-read-private"
        const val REFRESH_AHEAD_MS = 5 * 60 * 1000L
        const val MILLIS_PER_SECOND = 1000L
        const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    }
}