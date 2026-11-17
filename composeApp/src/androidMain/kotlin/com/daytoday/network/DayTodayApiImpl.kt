package com.daytoday.network

import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession
import com.daytoday.network.RecipeView
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Applies the shared plugin set (JSON, logging, timeouts, error mapping) so the
 * production Android client and the MockEngine client used in unit tests behave alike.
 */
internal fun HttpClientConfig<*>.configureApiClient() {
    install(ContentNegotiation) {
        json(networkJson)
    }
    install(Logging) {
        level = LogLevel.ALL
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
    HttpResponseValidator {
        validateResponse { response ->
            if (!response.status.isSuccess()) {
                throw ApiException(extractErrorMessage(response), response.status.value)
            }
        }
    }
    defaultRequest {
        header("Accept", "application/json")
    }
}

internal fun buildApiClient(): HttpClient = HttpClient(Android) { configureApiClient() }

class DayTodayApiImpl internal constructor(private val client: HttpClient) : DayTodayApi {

    constructor() : this(buildApiClient())

    override suspend fun getScoreboard(date: String): List<NbaGame> =
        client.get(absoluteUrl("api/nba/scoreboard")) { parameter("date", date) }.body()

    override suspend fun getGameDetail(gameId: String): NbaGame =
        client.get(absoluteUrl("api/nba/game/$gameId")).body()

    override suspend fun getNews(limit: Int): List<NbaNews> =
        client.get(absoluteUrl("api/nba/news")) { parameter("limit", limit.toString()) }.body()

    override suspend fun getInjuries(teamId: String?): List<Injury> =
        client.get(absoluteUrl("api/nba/injuries")) {
            teamId?.let { parameter("teamId", it) }
        }.body()

    override suspend fun getSessions(limit: Int, offset: Int): List<WorkoutSession> =
        client.get(absoluteUrl("api/workouts/sessions")) {
            parameter("limit", limit.toString())
            parameter("offset", offset.toString())
        }.body()

    override suspend fun getSession(sessionId: String): WorkoutSession =
        client.get(absoluteUrl("api/workouts/sessions/$sessionId")).body()

    override suspend fun saveSession(session: WorkoutSession): WorkoutSession =
        client.post(absoluteUrl("api/workouts/sessions")) {
            contentType(ContentType.Application.Json)
            setBody(session)
        }.body()

    override suspend fun saveSessions(sessions: List<WorkoutSession>): List<WorkoutSession> =
        client.post(absoluteUrl("api/workouts/sessions/batch")) {
            contentType(ContentType.Application.Json)
            setBody(sessions)
        }.body()

    override suspend fun deleteSession(sessionId: String): Unit {
        client.delete(absoluteUrl("api/workouts/sessions/$sessionId"))
    }

    override suspend fun getExercises(): List<Exercise> =
        client.get(absoluteUrl("api/workouts/exercises")).body()

    override suspend fun getDailyProgress(date: String): DailyProgress =
        client.get(absoluteUrl("api/workouts/progress/daily")) { parameter("date", date) }.body()

    override suspend fun getPersonalBests(): List<PersonalBests> =
        client.get(absoluteUrl("api/workouts/progress/personal-bests")).body()

    override suspend fun getWeeklyProgress(weekStart: String): WeeklyProgress =
        client.get(absoluteUrl("api/workouts/progress/weekly")) { parameter("weekStart", weekStart) }.body()

    override suspend fun login(request: LoginRequest): AuthResponse =
        client.post(absoluteUrl("api/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun register(request: RegisterRequest): AuthResponse =
        client.post(absoluteUrl("api/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun getItalianCourse(authToken: String): ItalianCourseDto =
        client.get(absoluteUrl("api/italian-path/course")) {
            header("Authorization", "Bearer $authToken")
        }.body()

    override suspend fun getItalianProgress(authToken: String): ItalianPathProgressDto =
        client.get(absoluteUrl("api/italian-path/progress")) {
            header("Authorization", "Bearer $authToken")
        }.body()

    override suspend fun submitItalianLesson(
        authToken: String,
        lessonId: String,
        answers: List<ItalianAnswerDto>,
    ): ItalianSubmitResultDto =
        client.post(absoluteUrl("api/italian-path/lesson/$lessonId/submit")) {
            header("Authorization", "Bearer $authToken")
            contentType(ContentType.Application.Json)
            setBody(ItalianSubmitRequest(answers))
        }.body()

    override suspend fun sendItalianChat(
        authToken: String,
        messages: List<ChatMessageDto>,
    ): ItalianChatResponse =
        client.post(absoluteUrl("api/italian/chat")) {
            header("Authorization", "Bearer $authToken")
            contentType(ContentType.Application.Json)
            setBody(ItalianChatRequest(messages))
        }.body()

    override suspend fun streamItalianChat(
        authToken: String,
        messages: List<ChatMessageDto>,
        onDelta: (text: String, lang: String) -> Unit,
    ) {
        client.preparePost(absoluteUrl("api/italian/chat/stream")) {
            header(HttpHeaders.Authorization, "Bearer $authToken")
            contentType(ContentType.Application.Json)
            setBody(ItalianChatRequest(messages))
            timeout {
                requestTimeoutMillis = 300_000L
                socketTimeoutMillis = 300_000L
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw ApiException(extractErrorMessage(response), response.status.value)
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) continue
                val parsed = try {
                    networkJson.decodeFromString(ChatStreamLine.serializer(), line)
                } catch (t: Throwable) {
                    null
                } ?: continue
                if (parsed.done) break
                val delta = parsed.delta
                if (delta != null) onDelta(delta, parsed.lang ?: "it")
            }
        }
    }

    override suspend fun getRecipes(authToken: String, meal: String?, favorites: Boolean): List<RecipeView> =
        client.get(absoluteUrl("api/recipes")) {
            header("Authorization", "Bearer $authToken")
            meal?.let { parameter("meal", it) }
            if (favorites) parameter("favorites", "true")
        }.body()

    override suspend fun getRecipe(authToken: String, recipeId: String): RecipeView =
        client.get(absoluteUrl("api/recipes/$recipeId")) {
            header("Authorization", "Bearer $authToken")
        }.body()

    override suspend fun toggleFavorite(authToken: String, recipeId: String): Boolean =
        client.post(absoluteUrl("api/recipes/$recipeId/favorite")) {
            header("Authorization", "Bearer $authToken")
        }.body<FavoriteToggleResponse>().favorited

    override suspend fun getTasks(authToken: String): List<TaskDto> =
        client.get(absoluteUrl("api/tasks")) {
            header("Authorization", "Bearer $authToken")
        }.body()

    override suspend fun getWeeklyGoals(authToken: String): List<WeeklyGoalDto> =
        client.get(absoluteUrl("api/goals")) {
            header("Authorization", "Bearer $authToken")
        }.body()

    override suspend fun getMeals(authToken: String, date: String): MealsDetailDto =
        client.get(absoluteUrl("api/nutrition/detail")) {
            header("Authorization", "Bearer $authToken")
            parameter("date", date)
        }.body()
}

/** Non-2xx reply from the backend; [status] is the HTTP code (0 when unknown). */
class ApiException(message: String, val status: Int = 0) : Exception(message)

private suspend fun extractErrorMessage(response: HttpResponse): String {
    val raw = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
    val fromJson = runCatching {
        networkJson.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()
    return fromJson?.takeIf { it.isNotBlank() } ?: "Request failed (${response.status.value})"
}