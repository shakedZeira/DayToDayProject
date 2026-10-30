package com.daytoday.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `normalizes a URL by trimming whitespace and adding a trailing slash`() {
        assertEquals("http://192.168.1.20:4000/", normalizeServerUrl("  http://192.168.1.20:4000  "))
    }

    @Test
    fun `keeps a single trailing slash`() {
        assertEquals("http://10.0.2.2:4000/", normalizeServerUrl("http://10.0.2.2:4000/"))
        assertEquals("http://10.0.2.2:4000/", normalizeServerUrl("http://10.0.2.2:4000///"))
    }

    @Test
    fun `accepts https and paths`() {
        assertEquals("https://example.com/api/", normalizeServerUrl("https://example.com/api"))
    }

    @Test
    fun `rejects non-http schemes and bare hosts`() {
        assertNull(normalizeServerUrl("ftp://example.com"))
        assertNull(normalizeServerUrl("192.168.1.20:4000"))
        assertNull(normalizeServerUrl("example.com"))
    }

    @Test
    fun `rejects blank and malformed values`() {
        assertNull(normalizeServerUrl(null))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("http://"))
        assertNull(normalizeServerUrl("http:///api"))
        assertNull(normalizeServerUrl("http://user:pass@example.com"))
        assertNull(normalizeServerUrl("http://exa mple.com"))
    }

    @Test
    fun `selects the emulator alias only on emulators`() {
        assertEquals("http://10.0.2.2:4000/", defaultServerUrl(isEmulator = true))
        assertEquals("http://127.0.0.1:4000/", defaultServerUrl(isEmulator = false))
    }

    @Test
    fun `installing a URL updates the active base URL`() {
        ApiBaseUrl.install(" http://192.168.1.5:4000 ")
        assertEquals("http://192.168.1.5:4000/", ApiBaseUrl.current())

        ApiBaseUrl.install("not a url")
        assertEquals(DEFAULT_BASE_URL, ApiBaseUrl.current())
    }
}
