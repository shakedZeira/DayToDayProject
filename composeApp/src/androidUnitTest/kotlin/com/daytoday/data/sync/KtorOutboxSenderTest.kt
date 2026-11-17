package com.daytoday.data.sync

import com.daytoday.network.configureApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KtorOutboxSenderTest {

    private val seen = mutableListOf<HttpRequestData>()

    @Before
    fun setUp() {
        com.daytoday.network.ApiBaseUrl.install("http://api.test/")
    }

    private fun sender(status: HttpStatusCode = HttpStatusCode.OK, body: String = "", fail: Boolean = false) =
        KtorOutboxSender(
            HttpClient(
                MockEngine { request ->
                    seen += request
                    if (fail) throw IOException("no route")
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) { configureApiClient() },
        )

    @Test
    fun `sends method, path, bearer token and the json body verbatim`() = runTest {
        val outcome = sender(HttpStatusCode.Created, "{\"id\":\"x\"}")
            .send("POST", "api/tasks", "{\"title\":\"a\"}", "tok")

        assertEquals(SendOutcome.Ok("{\"id\":\"x\"}"), outcome)
        val req = seen.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/api/tasks", req.url.encodedPath)
        assertEquals("Bearer tok", req.headers[HttpHeaders.Authorization])
        assertEquals("{\"title\":\"a\"}", req.body.toByteArray().decodeToString())
        assertTrue(req.body.contentType.toString().startsWith("application/json"))
    }

    @Test
    fun `an empty success reply has no body`() = runTest {
        val outcome = sender(HttpStatusCode.NoContent).send("DELETE", "api/tasks/1", null, "tok")
        assertEquals(SendOutcome.Ok(null), outcome)
        assertEquals(HttpMethod.Delete, seen.single().method)
    }

    @Test
    fun `error replies map to Http with the server message`() = runTest {
        val outcome = sender(HttpStatusCode.Unauthorized, "{\"error\":\"bad token\"}")
            .send("POST", "api/tasks", "{}", "tok")
        assertEquals(SendOutcome.Http(401, "bad token"), outcome)
    }

    @Test
    fun `connection failures map to Network`() = runTest {
        val outcome = sender(fail = true).send("POST", "api/tasks", "{}", "tok")
        assertTrue(outcome is SendOutcome.Network)
    }

    @Test
    fun `no body means no request body`() = runTest {
        sender().send("POST", "api/goals/1/checkoff", null, "tok")
        assertNull(seen.single().body.contentType)
    }
}
