package com.daytoday.data.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.daytoday.BuildConfig
import com.daytoday.settings.SettingsManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SpotifyLoginResult {
    data object LOGGED_IN : SpotifyLoginResult
    data object CANCELLED : SpotifyLoginResult
    data class ERROR(val message: String) : SpotifyLoginResult
}

sealed interface SpotifyPlaylistsResult {
    data class SUCCESS(val playlists: List<SpotifyPlaylist>) : SpotifyPlaylistsResult
    data object NOT_LOGGED_IN : SpotifyPlaylistsResult
    data class ERROR(val message: String) : SpotifyPlaylistsResult
}

sealed interface SpotifyPlayResult {
    data object PLAYING : SpotifyPlayResult
    data object NOT_LOGGED_IN : SpotifyPlayResult
    data class NO_DEVICE(val playlistUri: String) : SpotifyPlayResult
    data class ERROR(val message: String) : SpotifyPlayResult
}

interface SpotifyRepository {
    suspend fun isLoggedIn(): Boolean

    suspend fun login(context: Context): SpotifyLoginResult

    suspend fun logout()

    suspend fun myPlaylists(): SpotifyPlaylistsResult

    suspend fun selectPlaylist(uri: String, name: String)

    suspend fun selectedPlaylist(): Pair<String, String>?

    suspend fun playPlaylist(context: Context): SpotifyPlayResult
}

@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    private val authManager: SpotifyAuthManager,
    private val api: SpotifyApi,
    private val settingsManager: SettingsManager,
) : SpotifyRepository {

    override suspend fun isLoggedIn(): Boolean {
        val loggedIn = settingsManager.spotifyLoggedIn.first()
        val accessToken = settingsManager.spotifyAccessToken.first()
        return loggedIn && !accessToken.isNullOrBlank()
    }

    override suspend fun login(context: Context): SpotifyLoginResult {
        if (BuildConfig.SPOTIFY_CLIENT_ID.isBlank()) {
            return SpotifyLoginResult.ERROR("Missing Spotify client id")
        }
        val deferred = SpotifyAuthCallback.next()
        val verifier = authManager.generateCodeVerifier()
        val state = authManager.generateState()
        val url = authManager.loginUrl(verifier, state)

        val launchResult = runCatching {
            val intent = CustomTabsIntent.Builder().build().intent
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent.apply { data = Uri.parse(url) })
        }
        if (launchResult.isFailure) {
            deferred.complete(null)
            return SpotifyLoginResult.ERROR(
                launchResult.exceptionOrNull()?.message ?: "Could not open browser",
            )
        }

        val callbackUri = withTimeoutOrNull(LOGIN_TIMEOUT_MS) { deferred.await() }
            ?: return SpotifyLoginResult.CANCELLED
        val error = callbackUri.getQueryParameter("error")
        val code = callbackUri.getQueryParameter("code")
        if (error != null || code.isNullOrBlank()) return SpotifyLoginResult.CANCELLED
        val returnedState = callbackUri.getQueryParameter("state")
        if (returnedState != state) return SpotifyLoginResult.ERROR("State mismatch")

        return if (authManager.exchangeCode(context, code, state, verifier)) {
            SpotifyLoginResult.LOGGED_IN
        } else {
            SpotifyLoginResult.ERROR("Token exchange failed")
        }
    }

    override suspend fun logout() {
        settingsManager.clearSpotifyTokens()
    }

    override suspend fun myPlaylists(): SpotifyPlaylistsResult {
        if (!isLoggedIn()) return SpotifyPlaylistsResult.NOT_LOGGED_IN
        if (!authManager.ensureFreshToken()) return SpotifyPlaylistsResult.NOT_LOGGED_IN
        return runCatching { api.myPlaylists(limit = PLAYLISTS_LIMIT) }
            .fold(
                onSuccess = { SpotifyPlaylistsResult.SUCCESS(it.items) },
                onFailure = { SpotifyPlaylistsResult.ERROR(it.message ?: "Failed to load playlists") },
            )
    }

    override suspend fun selectPlaylist(uri: String, name: String) {
        settingsManager.setSpotifyPlaylist(uri = uri, name = name)
    }

    override suspend fun selectedPlaylist(): Pair<String, String>? {
        val uri = settingsManager.spotifyPlaylistUri.first() ?: return null
        val name = settingsManager.spotifyPlaylistName.first() ?: ""
        return uri to name
    }

    override suspend fun playPlaylist(context: Context): SpotifyPlayResult {
        if (!authManager.ensureFreshToken()) return SpotifyPlayResult.NOT_LOGGED_IN
        val playlistUri = selectedPlaylist()?.first.orEmpty()
        if (playlistUri.isBlank()) return SpotifyPlayResult.ERROR("Pick a playlist first")

        var launched = false
        repeat(DEVICE_WAIT_ATTEMPTS) {
            when (val attempt = playOnAnyDevice(playlistUri)) {
                is PlayAttempt.PLAYED -> return SpotifyPlayResult.PLAYING
                PlayAttempt.NO_DEVICE -> launchSpotify(context, alreadyLaunched = launched, playlistUri = playlistUri)
                is PlayAttempt.ERROR -> return SpotifyPlayResult.ERROR(attempt.message)
            }
            launched = true
            delay(DEVICE_WAIT_DELAY_MS)
        }
        return SpotifyPlayResult.NO_DEVICE(playlistUri)
    }

    private suspend fun playOnAnyDevice(playlistUri: String): PlayAttempt {
        val devices = runCatching { api.devices().devices }.getOrElse { return PlayAttempt.ERROR(it.message ?: "Failed to reach Spotify") }
        val activeDevice = devices.firstOrNull { it.isActive }
        return try {
            if (activeDevice != null) {
                api.play(PlayRequest(contextUri = playlistUri))
            } else {
                val anyDevice = devices.firstOrNull { !it.id.isNullOrBlank() }
                    ?: return PlayAttempt.NO_DEVICE
                api.playOnDevice(anyDevice.id!!, PlayRequest(contextUri = playlistUri))
            }
            PlayAttempt.PLAYED
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) PlayAttempt.NO_DEVICE else PlayAttempt.ERROR("Spotify error ${e.code()}")
        } catch (e: Exception) {
            PlayAttempt.ERROR(e.message ?: "Playback failed")
        }
    }

    private fun launchSpotify(context: Context, alreadyLaunched: Boolean, playlistUri: String) {
        if (alreadyLaunched || !containsSpotifyPackage(context)) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playlistUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun containsSpotifyPackage(context: Context): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) != null
        }.getOrDefault(false)

    private sealed interface PlayAttempt {
        data object PLAYED : PlayAttempt
        data object NO_DEVICE : PlayAttempt
        data class ERROR(val message: String) : PlayAttempt
    }

    companion object {
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val DEVICE_WAIT_ATTEMPTS = 10
        private const val DEVICE_WAIT_DELAY_MS = 1_500L
        private const val PLAYLISTS_LIMIT = 50
        private const val LOGIN_TIMEOUT_MS = 60_000L
    }
}