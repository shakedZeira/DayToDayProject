package com.daytoday.network

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous storage/access for the server base URL.
 *
 * DataStore is the source of truth ([com.daytoday.settings.SettingsManager]), but the
 * network layer cannot suspend just to build a URL. A small SharedPreferences mirror
 * is written alongside every DataStore write, so [ApiBaseUrl] can be installed during
 * `Application.onCreate` before the first request and updated immediately afterwards.
 */
interface ServerUrlStore {
    /** Value last written by the app, or null when the user never configured one. */
    fun persistedUrl(): String?

    /** Platform default: `10.0.2.2` on an emulator, loopback otherwise. */
    fun defaultUrl(): String

    /** Normalizes, mirrors, and activates [url]. Returns the normalized value. */
    fun save(url: String): String?
}

@Singleton
class ServerUrlProvider @Inject constructor(
    @ApplicationContext context: Context,
) : ServerUrlStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun defaultUrl(): String = defaultServerUrl(isProbablyEmulator())

    /** Called once at process start, before any network call. */
    fun install() {
        ApiBaseUrl.install(persistedUrl() ?: defaultUrl())
    }

    override fun persistedUrl(): String? =
        prefs.getString(KEY_SERVER_URL, null)?.let(::normalizeServerUrl)

    override fun save(url: String): String? {
        val normalized = normalizeServerUrl(url) ?: return null
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        ApiBaseUrl.install(normalized)
        return normalized
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true) ||
            Build.PRODUCT.contains("google_sdk", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    companion object {
        private const val PREFS_NAME = "settings"
        const val KEY_SERVER_URL = "server_base_url"
    }
}
