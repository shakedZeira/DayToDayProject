package com.daytoday.network

/** Used when the app has no stored server URL and no emulator was detected. */
internal const val DEFAULT_BASE_URL: String = "http://127.0.0.1:4000/"

/** Special loopback alias the Android emulator maps to the host machine. */
internal const val EMULATOR_BASE_URL: String = "http://10.0.2.2:4000/"

/**
 * Process-wide base URL consulted by [absoluteUrl] and the startup health check.
 *
 * The Android layer installs the persisted value from [com.daytoday.network.ServerUrlStore]
 * during `Application.onCreate`, so network calls never need to suspend just to resolve
 * the URL. Values are normalized, so callers can rely on a trailing slash.
 */
internal object ApiBaseUrl {

    @Volatile
    private var value: String = DEFAULT_BASE_URL

    fun current(): String = value

    fun install(raw: String) {
        value = normalizeServerUrl(raw) ?: DEFAULT_BASE_URL
    }
}

/**
 * The default base URL for this platform: the emulator's host alias on an emulator,
 * loopback on a physical device (where the backend may run in Termux or be reached
 * through `adb reverse`).
 */
internal fun defaultServerUrl(isEmulator: Boolean): String =
    if (isEmulator) EMULATOR_BASE_URL else DEFAULT_BASE_URL

/**
 * Sanitizes a user-entered server URL.
 *
 * Requires an `http://` or `https://` scheme, rejects blank/malformed input, trims
 * surrounding whitespace, and always returns a single trailing slash. Returns `null`
 * when the value is not a usable base URL.
 */
internal fun normalizeServerUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val lower = trimmed.lowercase()
    val scheme = when {
        lower.startsWith("http://") -> trimmed.substring(0, "http://".length)
        lower.startsWith("https://") -> trimmed.substring(0, "https://".length)
        else -> return null
    }

    val remainder = trimmed.substring(scheme.length).trimEnd('/')
    if (remainder.isEmpty() || remainder.startsWith("/")) return null

    val authority = remainder.substringBefore('/')
    if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null
    // Credentials in the URL are not supported and would leak into logs.
    if (authority.contains('@')) return null
    if (authority.startsWith(':') || authority.endsWith(':')) return null

    return scheme + remainder + "/"
}
