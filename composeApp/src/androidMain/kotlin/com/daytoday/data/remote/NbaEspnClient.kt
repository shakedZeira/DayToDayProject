package com.daytoday.data.remote

import com.daytoday.repository.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ESPN Site API client for NBA news, injuries, transactions, and scoreboard.
 * Base: https://site.api.espn.com/apis/site/v2/sports/basketball/nba/
 * No auth required.
 */
@Singleton
class NbaEspnClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.HEADERS }
    }

    private val baseUrl = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba"

    suspend fun getNews(limit: Int = 20): Result<List<EspNewsArticle>> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/news") {
                parameter("limit", limit.toString())
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            val articles = (root["articles"] as? JsonArray) ?: emptyList()
            Result.success(articles.mapNotNull { (it as? JsonObject)?.let { obj -> EspNewsArticle(obj) } })
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }

    suspend fun getInjuries(): Result<List<EspInjuryParsed>> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/injuries") {
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            val teams = (root["injuries"] as? JsonArray) ?: emptyList()
            val all = mutableListOf<EspInjuryParsed>()
            for (elem in teams) {
                val obj = elem as? JsonObject ?: continue
                all.addAll(parseInjuryTeam(obj))
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }

    suspend fun getTransactions(): Result<List<EspTransaction>> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/transactions") {
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            val txs = (root["transactions"] as? JsonArray) ?: emptyList()
            Result.success(txs.mapNotNull { (it as? JsonObject)?.let { obj -> EspTransaction(obj) } })
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }

    suspend fun getScoreboard(date: String): Result<List<EspScoreboardGame>> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/scoreboard") {
                parameter("dates", espnDate(date))
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            val events = (root["events"] as? JsonArray) ?: emptyList()
            Result.success(events.mapNotNull { (it as? JsonObject)?.let { obj -> parseScoreboardEvent(obj) } })
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }

    private fun espnDate(date: String): String =
        if (date.equals("today", ignoreCase = true)) "today" else date.replace("-", "")
}

// ---------- ESPN news ----------

data class EspNewsArticle(val root: JsonObject) {
    val id: String get() = root["id"].jsonPrimitiveContent() ?: ""
    val headline: String get() = root["headline"].jsonPrimitiveContent() ?: ""
    val description: String get() = root["description"].jsonPrimitiveContent() ?: ""
    val published: String get() = root["published"].jsonPrimitiveContent() ?: ""
    val url: String get() {
        val links = root["links"] as? JsonObject ?: return ""
        val web = links["web"] as? JsonObject ?: return ""
        return web["href"].jsonPrimitiveContent() ?: ""
    }
    val imageUrl: String? get() {
        val images = root["images"] as? JsonArray ?: return null
        return images.firstNotNullOfOrNull { image ->
            (image as? JsonObject)?.getString("url")
        }
    }
    val source: String get() = "ESPN"
    val teamIds: List<String> get() = emptyList()
}

// ---------- ESPN injuries ----------

data class EspInjuryParsed(
    val id: String,
    val playerName: String?,
    val playerId: String?,
    val teamId: String,
    val teamName: String,
    val teamAbbreviation: String?,
    val status: String,
    val date: String,
    val comment: String,
    val injuryType: String?,
    val bodyPart: String?,
    val detail: String?,
    val side: String?,
    val returnDate: String?,
    val fantasyStatus: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

fun parseInjuryTeam(teamRoot: JsonObject): List<EspInjuryParsed> {
    val teamName = teamRoot["displayName"].jsonPrimitiveContent() ?: "Unknown"
    val teamAbbr = teamRoot["abbreviation"].jsonPrimitiveContent()
    val teamId = teamRoot["id"].jsonPrimitiveContent() ?: ""
    val injuries = (teamRoot["injuries"] as? JsonArray) ?: emptyList()
    return injuries.mapNotNull { (it as? JsonObject)?.parseOneInjury(teamId, teamName, teamAbbr) }
}

fun JsonObject.parseOneInjury(teamId: String, teamName: String, teamAbbr: String?): EspInjuryParsed {
    val athlete = this["athlete"] as? JsonObject
    val details = this["details"] as? JsonObject
    val type = this["type"] as? JsonObject
    return EspInjuryParsed(
        id = this["id"].jsonPrimitiveContent() ?: "",
        playerName = athlete?.getString("displayName")
            ?: "${athlete?.getString("firstName") ?: ""} ${athlete?.getString("lastName") ?: ""}".trim().ifEmpty { null },
        playerId = athlete?.getString("id"),
        teamId = teamId,
        teamName = teamName,
        teamAbbreviation = teamAbbr,
        status = this["status"].jsonPrimitiveContent() ?: "",
        date = this["date"].jsonPrimitiveContent() ?: "",
        comment = this["longComment"].jsonPrimitiveContent()
            ?: (this["shortComment"].jsonPrimitiveContent() ?: ""),
        injuryType = type?.getString("name"),
        bodyPart = details?.getString("location"),
        detail = details?.getString("detail"),
        side = details?.getString("side"),
        returnDate = details?.getString("returnDate"),
        fantasyStatus = type?.getString("abbreviation"),
    )
}

// ---------- ESPN transactions ----------

data class EspTransaction(val root: JsonObject) {
    val id: String get() = root["id"].jsonPrimitiveContent() ?: ""
    val date: String get() = root["date"].jsonPrimitiveContent() ?: ""
    val description: String get() = root["description"].jsonPrimitiveContent() ?: ""
    val teamName: String? get() = (root["team"] as? JsonObject)?.getString("name")
    val teamAbbreviation: String? get() = (root["team"] as? JsonObject)?.getString("abbreviation")
}

// ---------- ESPN scoreboard ----------

data class EspScoreboardTeam(
    val id: String = "",
    val name: String = "",
    val abbreviation: String = "",
    val logoUrl: String = "",
    val color: String = "",
)

data class EspScoreboardGame(
    val id: String,
    val homeTeam: EspScoreboardTeam,
    val awayTeam: EspScoreboardTeam,
    val homeScore: Int,
    val awayScore: Int,
    val status: String,
    val startTime: Long,
    val quarter: Int,
    val timeRemaining: String,
    val isCompleted: Boolean,
)

fun parseScoreboardEvent(event: JsonObject): EspScoreboardGame {
    val statusType = (event["status"] as? JsonObject)?.get("type") as? JsonObject
    val state = statusType?.stringOrNull("state") ?: ""
    val eventDetail = statusType?.stringOrNull("detail") ?: ""
    val competition = (event["competitions"] as? JsonArray)?.firstOrNull() as? JsonObject
    val competitionStatusType = (competition?.get("status") as? JsonObject)?.get("type") as? JsonObject
    var homeTeam = EspScoreboardTeam()
    var awayTeam = EspScoreboardTeam()
    var homeScore = 0
    var awayScore = 0
    val competitors = (competition?.get("competitors") as? JsonArray) ?: emptyList()
    for (element in competitors) {
        val competitor = element as? JsonObject ?: continue
        val teamRoot = competitor["team"] as? JsonObject
        val team = EspScoreboardTeam(
            id = teamRoot?.stringOrNull("id") ?: "",
            name = teamRoot?.stringOrNull("displayName") ?: teamRoot?.stringOrNull("name") ?: "",
            abbreviation = teamRoot?.stringOrNull("abbreviation") ?: "",
            logoUrl = teamRoot?.stringOrNull("logo") ?: "",
            color = teamRoot?.stringOrNull("color") ?: "",
        )
        val score = competitor.intOrNull("score") ?: 0
        when (competitor.stringOrNull("homeAway")) {
            "home" -> {
                homeTeam = team
                homeScore = score
            }
            "away" -> {
                awayTeam = team
                awayScore = score
            }
            else -> Unit
        }
    }
    val isCompleted = state == "post"
    val status = when (state) {
        "in" -> "Live"
        "post" -> "Final"
        else -> "Scheduled"
    }
    val quarter = if (state == "in") {
        Regex("Q(\\d+)").find(eventDetail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    } else {
        0
    }
    val timeRemaining = if (state == "in") {
        (competitionStatusType?.stringOrNull("detail") ?: "")
            .replace(Regex("^Q\\d+\\s*"), "")
            .trim()
    } else {
        ""
    }
    val startTime = runCatching {
        java.time.Instant.parse(event.getString("date") ?: "").toEpochMilli()
    }.getOrElse {
        System.currentTimeMillis()
    }
    return EspScoreboardGame(
        id = event.stringOrNull("id") ?: "",
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeScore = homeScore,
        awayScore = awayScore,
        status = status,
        startTime = startTime,
        quarter = quarter,
        timeRemaining = timeRemaining,
        isCompleted = isCompleted,
    )
}
