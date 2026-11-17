package com.daytoday.network

import com.daytoday.ui.recipe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DayTodayApiImplTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Before
    fun setUp() {
        ApiBaseUrl.install("http://api.test/")
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): DayTodayApiImpl {
        val engine = MockEngine { request ->
            requests += request
            handler(this, request)
        }
        return DayTodayApiImpl(HttpClient(engine) { configureApiClient() })
    }

    @Test
    fun `getRecipes sends bearer token and filters`() = runTest {
        val body = networkJson.encodeToString(ListSerializer(RecipeView.serializer()), listOf(recipe("a", meal = "lunch")))
        val api = api { json(body) }

        val result = api.getRecipes("tok", "lunch", favorites = true)

        assertEquals(listOf("a"), result.map { it.id })
        val req = requests.single()
        assertEquals(HttpMethod.Get, req.method)
        assertEquals("/api/recipes", req.url.encodedPath)
        assertEquals("lunch", req.url.parameters["meal"])
        assertEquals("true", req.url.parameters["favorites"])
        assertEquals("Bearer tok", req.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `getRecipes omits optional filters`() = runTest {
        val api = api { json("[]") }
        api.getRecipes("tok", null, favorites = false)
        val req = requests.single()
        assertNull(req.url.parameters["meal"])
        assertNull(req.url.parameters["favorites"])
    }

    @Test
    fun `toggleFavorite posts and returns the new state`() = runTest {
        val api = api { json("""{"favorited":true,"recipeId":"r1"}""") }
        assertTrue(api.toggleFavorite("tok", "r1"))
        val req = requests.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/api/recipes/r1/favorite", req.url.encodedPath)
    }

    @Test
    fun `non-2xx replies become ApiException carrying the server message and status`() = runTest {
        val api = api { json("""{"error":"not found"}""", HttpStatusCode.NotFound) }
        val ex = try {
            api.getRecipe("tok", "missing")
            null
        } catch (e: ApiException) {
            e
        }
        assertEquals("not found", ex!!.message)
        assertEquals(404, ex.status)
    }

    @Test
    fun `error without a json body falls back to a generic message`() = runTest {
        val api = api { respond("oops", HttpStatusCode.BadGateway) }
        val ex = assertThrows(ApiException::class.java) {
            kotlinx.coroutines.runBlocking { api.getRecipe("tok", "x") }
        }
        assertEquals("Request failed (502)", ex.message)
        assertEquals(502, ex.status)
    }

    @Test
    fun `getScoreboard passes the date`() = runTest {
        val api = api { json("[]") }
        assertTrue(api.getScoreboard("2026-11-17").isEmpty())
        val req = requests.single()
        assertEquals("/api/nba/scoreboard", req.url.encodedPath)
        assertEquals("2026-11-17", req.url.parameters["date"])
    }

    @Test
    fun `getSessions passes paging and deleteSession uses DELETE`() = runTest {
        val api = api { request ->
            if (request.method == HttpMethod.Delete) respond("", HttpStatusCode.NoContent) else json("[]")
        }
        api.getSessions(limit = 5, offset = 10)
        api.deleteSession("s1")
        assertEquals("5", requests[0].url.parameters["limit"])
        assertEquals("10", requests[0].url.parameters["offset"])
        assertEquals(HttpMethod.Delete, requests[1].method)
        assertEquals("/api/workouts/sessions/s1", requests[1].url.encodedPath)
    }

    @Test
    fun `login posts credentials as json`() = runTest {
        val api = api {
            json("""{"user":{"id":"u1","email":"a@b.c","name":"A"},"token":"jwt"}""")
        }
        val auth = api.login(LoginRequest("a@b.c", "pw"))
        assertEquals("jwt", auth.token)
        assertEquals("u1", auth.user.id)
        val sent = requests.single().body.toByteArray().decodeToString()
        assertTrue(sent.contains("\"email\":\"a@b.c\""))
        assertTrue(sent.contains("\"password\":\"pw\""))
    }

    @Test
    fun `unknown json fields are ignored`() = runTest {
        val api = api { json("""{"favorited":false,"recipeId":"r","extra":123}""") }
        assertEquals(false, api.toggleFavorite("t", "r"))
    }
}
