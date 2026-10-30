package com.daytoday.network

/**
 * Converts low-level socket failures into a message a user can act on.
 *
 * Raw messages such as `Failed to connect to /127.0.0.1:4000` mean nothing to most
 * users and hide the real fix (start the backend or correct the URL in Settings).
 */
internal fun friendlyNetworkError(error: Throwable): String {
    val baseUrl = ApiBaseUrl.current()
    return if (isConnectionFailure(error)) {
        "Can't reach the server at $baseUrl. " +
            "Check the server is running and the URL in Settings."
    } else {
        error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }
}

/** True when [error] (or any of its causes) is a connect/read-timeout/resolve failure. */
internal fun isConnectionFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 8) {
        val name = current::class.simpleName.orEmpty()
        if (name in CONNECTION_EXCEPTION_NAMES) return true

        val message = current.message?.lowercase().orEmpty()
        if (CONNECTION_MESSAGE_MARKERS.any { it in message }) return true

        current = current.cause
        depth++
    }
    return false
}

private val CONNECTION_EXCEPTION_NAMES = setOf(
    "ConnectException",
    "ConnectTimeoutException",
    "HttpRequestTimeoutException",
    "IOException",
    "NoRouteToHostException",
    "SocketException",
    "SocketTimeoutException",
    "SSLException",
    "UnknownHostException",
    "UnresolvedAddressException",
    "EOFException",
)

private val CONNECTION_MESSAGE_MARKERS = listOf(
    "failed to connect",
    "connection refused",
    "connection reset",
    "no route to host",
    "software caused connection abort",
    "timed out",
    "timeout",
    "unable to resolve host",
    "unknown host",
    "network is unreachable",
)
