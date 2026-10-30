package com.daytoday.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkErrorsTest {

    @Before
    fun setUp() {
        ApiBaseUrl.install("http://192.168.1.20:4000/")
    }

    @Test
    fun `connection refused becomes an actionable message`() {
        val message = friendlyNetworkError(
            ConnectException("Failed to connect to /127.0.0.1:4000")
        )

        assertTrue(message.contains("Can't reach the server at http://192.168.1.20:4000/"))
        assertTrue(message.contains("Settings"))
        assertFalse(message.contains("Failed to connect to /127.0.0.1:4000"))
    }

    @Test
    fun `timeouts and host resolution failures are connection failures`() {
        assertTrue(isConnectionFailure(SocketTimeoutException("timeout")))
        assertTrue(isConnectionFailure(UnknownHostException("example.invalid")))
        assertTrue(isConnectionFailure(IOException("Connection reset by peer")))
        assertTrue(isConnectionFailure(RuntimeException("wrapped", ConnectException("refused"))))
    }

    @Test
    fun `server-side messages are preserved`() {
        assertEquals(
            "Invalid email or password",
            friendlyNetworkError(Exception("Invalid email or password"))
        )
    }

    @Test
    fun `unhelpful empty messages fall back to a generic message`() {
        assertEquals(
            "Something went wrong. Please try again.",
            friendlyNetworkError(Exception())
        )
    }
}
