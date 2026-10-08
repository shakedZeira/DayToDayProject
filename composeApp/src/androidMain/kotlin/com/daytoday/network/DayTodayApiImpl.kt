package com.daytoday.network

import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews
import com.daytoday.model.PersonalBests
import com.daytoday.model.PdfMergeRequest
import com.daytoday.model.PdfMergeResponse
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

class DayTodayApiImpl : DayTodayApi {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(networkJson)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        defaultRequest {
            header("Accept", "application/json")
        }
    }

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
        client.post(absoluteUrl("api/workouts/sessions")) { setBody(session) }.body()

    override suspend fun saveSessions(sessions: List<WorkoutSession>): List<WorkoutSession> =
        client.post(absoluteUrl("api/workouts/sessions/batch")) { setBody(sessions) }.body()

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

    override suspend fun mergePdfs(request: PdfMergeRequest): PdfMergeResponse =
        client.post(absoluteUrl("api/pdfs/merge")) { setBody(request) }.body()

    override suspend fun login(request: LoginRequest): AuthResponse =
        client.post(absoluteUrl("api/auth/login")) { setBody(request) }.body()

    override suspend fun register(request: RegisterRequest): AuthResponse =
        client.post(absoluteUrl("api/auth/register")) { setBody(request) }.body()

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
}