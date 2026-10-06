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
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BigBallsData NBA API client.
 * Base: https://api.bigballsdata.com/v1/nba/
 * Requires Authorization: Bearer <key> header on every request.
 *
 * Used for: today's games / box scores, matchup detail.
 *
 * NOTE: This client currently makes unauthenticated calls. The NBA Daily project
 * uses a BBS_API_KEY Bearer token. Without a key, BBS returns 401.
 * The app degrades to ESPN-only (news/injuries) when BBS is unavailable.
 */
@Singleton
class NbaBigBallsClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.HEADERS }
    }

    private val baseUrl = "https://api.bigballsdata.com/v1/nba"

    /**
     * Today's games / box scores list.
     * BBS /matches endpoint with sport=basketball&league=nba&date=today
     * returns live NBA games with team info.
     */
    suspend fun getMatches(date: String = "today", limit: Int = 50): Result<List<BbsMatchDto>> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/matches") {
                parameter("sport", "basketball")
                parameter("league", "nba")
                parameter("date", date)
                parameter("limit", limit.toString())
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            val data = (root["data"] as? JsonArray) ?: emptyList()
            Result.success(data.mapNotNull { (it as? JsonObject)?.let { obj -> BbsMatchDto(obj) } })
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }

    /**
     * Box-score detail for a single game (player stats).
     * BBS: GET /games/{matchId}/matchup?view=nerd
     */
    suspend fun getMatchup(matchId: String): Result<BbsMatchupDto> {
        return try {
            val resp: HttpResponse = client.get("$baseUrl/games/$matchId/matchup") {
                parameter("view", "nerd")
                header(HttpHeaders.Accept, "application/json")
            }
            val root = resp.body<JsonObject>()
            Result.success(BbsMatchupDto(root))
        } catch (e: Exception) {
            Result.failure(e.message ?: (e::class.simpleName ?: "Unknown error"), e)
        }
    }
}

// ---------- BBS matches ----------

data class BbsMatchDto(val root: JsonObject) {
    val matchId: String
        get() = root.stringOrNull("match_id", "id")?.ifEmpty { "bbs_${System.currentTimeMillis()}_${(0..9999).random()}" } ?: "bbs_${System.currentTimeMillis()}_${(0..9999).random()}"
    val date: String
        get() = root.stringOrNull("game_date", "date") ?: ""
    val status: String
        get() = root.stringOrNull("status") ?: "scheduled"
    val homeTeam: BbsTeamDto
        get() = BbsTeamDto(root["home"] as? JsonObject ?: root["home_team"] as? JsonObject ?: JsonObject(emptyMap()))
    val awayTeam: BbsTeamDto
        get() = BbsTeamDto(root["away"] as? JsonObject ?: root["away_team"] as? JsonObject ?: JsonObject(emptyMap()))
    val venue: String?
        get() = (root["venue"] as? JsonObject)?.getString("name")
    val periods: List<BbsPeriodDto>
        get() {
            val p = root["periods"] as? JsonArray ?: return emptyList()
            return p.mapNotNull { (it as? JsonObject)?.let { obj -> BbsPeriodDto(obj) } }
        }
    val attendance: Long
        get() = (root["attendance"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}

data class BbsTeamDto(val root: JsonObject) {
    val id: String? get() = root.stringOrNull("id")
    val name: String? get() = root.stringOrNull("name")
    val abbreviation: String? get() = root.stringOrNull("abbreviation", "short_name")
    val score: Int? get() = root.intOrNull("score", "pts")
}

data class BbsPeriodDto(val root: JsonObject) {
    val period: Int? get() = root.intOrNull("period")
    val homeScore: Int? get() = root.intOrNull("home_score", "home")
    val awayScore: Int? get() = root.intOrNull("away_score", "away")
    val timeRemaining: String? get() = root.stringOrNull("time_remaining")
}

// ---------- BBS matchup (box score) ----------

data class BbsMatchupDto(val root: JsonObject) {
    val error: String?
        get() = (root["error"] as? JsonObject)?.getString("message")
    val matchId: String? get() = root.stringOrNull("match_id", "id")
    val status: String? get() = root.stringOrNull("status")
    val venue: String?
        get() = (root["venue"] as? JsonObject)?.getString("name")
    val homeTeam: BbsMatchupTeamDto?
        get() = root["home_team"]?.let { (it as? JsonObject)?.let { BbsMatchupTeamDto(it) } }
            ?: root["home"]?.let { (it as? JsonObject)?.let { BbsMatchupTeamDto(it) } }
    val awayTeam: BbsMatchupTeamDto?
        get() = root["away_team"]?.let { (it as? JsonObject)?.let { BbsMatchupTeamDto(it) } }
            ?: root["away"]?.let { (it as? JsonObject)?.let { BbsMatchupTeamDto(it) } }
}

data class BbsMatchupTeamDto(val root: JsonObject) {
    val id: String? get() = root.stringOrNull("id")
    val name: String? get() = root.stringOrNull("name")
    val abbreviation: String? get() = root.stringOrNull("abbreviation", "short_name")
    val score: Int? get() = root.intOrNull("score", "home_score", "away_score")
    val players: List<BbsPlayerDto>
        get() = (root["players"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { obj -> BbsPlayerDto(obj) } } ?: emptyList()
}

data class BbsPlayerDto(val root: JsonObject) {
    val id: String? get() = root.stringOrNull("id", "player_id")
    val name: String? get() = root.stringOrNull("name", "player_name")
    val position: String? get() = root.stringOrNull("position")
    val jerseyNumber: Int? get() = root.intOrNull("jersey_number", "jersey")
    val stats: BbsPlayerStatsDto?
        get() = root["stats"]?.let { (it as? JsonObject)?.let { BbsPlayerStatsDto(it) } }
            ?: root["season_averages"]?.let { (it as? JsonObject)?.let { BbsPlayerStatsDto(it) } }
}

data class BbsPlayerStatsDto(val root: JsonObject) {
    val points: Int? get() = root.intOrNull("points", "pts")
    val rebounds: Int? get() = root.intOrNull("rebounds", "reb")
    val assists: Int? get() = root.intOrNull("assists", "ast")
    val steals: Int? get() = root.intOrNull("steals", "stl")
    val blocks: Int? get() = root.intOrNull("blocks", "blk")
    val turnovers: Int? get() = root.intOrNull("turnovers", "tov")
    val fgMade: Int? get() = root.intOrNull("fgm", "field_goals_made")
    val fgAttempted: Int? get() = root.intOrNull("fga", "field_goals_attempted")
    val fg3Made: Int? get() = root.intOrNull("3pm", "three_pointers_made", "tpm")
    val fg3Attempted: Int? get() = root.intOrNull("3pa", "three_pointers_attempted", "tpa")
    val ftMade: Int? get() = root.intOrNull("ftm", "free_throws_made")
    val ftAttempted: Int? get() = root.intOrNull("fta", "free_throws_attempted")
    val plusMinus: Int? get() = root.intOrNull("plus_minus", "pm")
}
