package com.daytoday.data.remote

import com.daytoday.repository.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        install(ContentNegotiation) { json(this@NbaBigBallsClient.json) }
        install(Logging) { level = LogLevel.BASIC }
        defaultRequest {
            url("https://api.bigballsdata.com/v1/nba/")
            header("Accept", "application/json")
        }
    }

    /**
     * Today's games / box scores list.
     * BBS /matches endpoint with sport=basketball&league=nba&date=today
     * returns live NBA games with team info.
     */
    suspend fun getMatches(date: String = "today", limit: Int = 50): Result<List<BbsMatchDto>> = runCatching {
        val resp = client.get("matches") {
            parameters {
                "sport" to "basketball"
                "league" to "nba"
                "date" to date
                "limit" to limit.toString()
            }
        }
        val root = resp.body<JsonObject>()
        val data = root["data"] as? JsonArray ?: emptyArray()
        data.map { BbsMatchDto(it as JsonObject) }
    }

    /**
     * Box-score detail for a single game (player stats).
     * BBS: GET /games/{matchId}/matchup?view=nerd
     */
    suspend fun getMatchup(matchId: String): Result<BbsMatchupDto> = runCatching {
        val resp = client.get("games/$matchId/matchup") {
            parameters { "view" to "nerd" }
        }
        val root = resp.body<JsonObject>()
        BbsMatchupDto(root)
    }
}

// ---------- BBS matches ----------

data class BbsMatchDto(val root: JsonObject) {
    val matchId: String
        get() = string("match_id", "id").ifEmpty { "bbs_${System.currentTimeMillis()}_${(0..9999).random()}" }
    val date: String
        get() = string("game_date", "date")
    val status: String
        get() = string("status").ifEmpty { "scheduled" }
    val homeTeam: BbsTeamDto
        get() = BbsTeamDto(root["home"] as? JsonObject ?: root["home_team"] as? JsonObject ?: JsonObject.empty())
    val awayTeam: BbsTeamDto
        get() = BbsTeamDto(root["away"] as? JsonObject ?: root["away_team"] as? JsonObject ?: JsonObject.empty())
    val venue: String?
        get() = (root["venue"] as? JsonObject)?.getString("name")
    val periods: List<BbsPeriodDto>
        get() {
            val p = root["periods"] as? JsonArray ?: return emptyList()
            return p.map { BbsPeriodDto(it as JsonObject) }
        }
    val attendance: Long
        get() = (root["attendance"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}

data class BbsTeamDto(val root: JsonObject) {
    val id: String? get() = stringOrNull("id")
    val name: String? get() = stringOrNull("name")
    val abbreviation: String? get() = stringOrNull("abbreviation", "short_name")
    val score: Int? get() = intOrNull("score", "pts")
}

data class BbsPeriodDto(val root: JsonObject) {
    val period: Int? get() = intOrNull("period")
    val homeScore: Int? get() = intOrNull("home_score", "home")
    val awayScore: Int? get() = intOrNull("away_score", "away")
    val timeRemaining: String? get() = stringOrNull("time_remaining")
}

// ---------- BBS matchup (box score) ----------

data class BbsMatchupDto(val root: JsonObject) {
    val error: String?
        get() = (root["error"] as? JsonObject)?.getString("message")
    val matchId: String? get() = stringOrNull("match_id", "id")
    val status: String? get() = stringOrNull("status")
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
    val id: String? get() = stringOrNull("id")
    val name: String? get() = stringOrNull("name")
    val abbreviation: String? get() = stringOrNull("abbreviation", "short_name")
    val score: Int? get() = intOrNull("score", "home_score", "away_score")
    val players: List<BbsPlayerDto>
        get() = (root["players"] as? JsonArray)?.map { BbsPlayerDto(it as JsonObject) } ?: emptyList()
}

data class BbsPlayerDto(val root: JsonObject) {
    val id: String? get() = stringOrNull("id", "player_id")
    val name: String? get() = stringOrNull("name", "player_name")
    val position: String? get() = stringOrNull("position")
    val jerseyNumber: Int? get() = intOrNull("jersey_number", "jersey")
    val stats: BbsPlayerStatsDto?
        get() = root["stats"]?.let { (it as? JsonObject)?.let { BbsPlayerStatsDto(it) } }
            ?: root["season_averages"]?.let { (it as? JsonObject)?.let { BbsPlayerStatsDto(it) } }
}

data class BbsPlayerStatsDto(val root: JsonObject) {
    val points: Int? get() = intOrNull("points", "pts")
    val rebounds: Int? get() = intOrNull("rebounds", "reb")
    val assists: Int? get() = intOrNull("assists", "ast")
    val steals: Int? get() = intOrNull("steals", "stl")
    val blocks: Int? get() = intOrNull("blocks", "blk")
    val turnovers: Int? get() = intOrNull("turnovers", "tov")
    val fgMade: Int? get() = intOrNull("fgm", "field_goals_made")
    val fgAttempted: Int? get() = intOrNull("fga", "field_goals_attempted")
    val fg3Made: Int? get() = intOrNull("3pm", "three_pointers_made", "tpm")
    val fg3Attempted: Int? get() = intOrNull("3pa", "three_pointers_attempted", "tpa")
    val ftMade: Int? get() = intOrNull("ftm", "free_throws_made")
    val ftAttempted: Int? get() = intOrNull("fta", "free_throws_attempted")
    val plusMinus: Int? get() = intOrNull("plus_minus", "pm")
}

// ---------- JSON helpers ----------

private fun JsonPrimitive.contentOrNull(): String? = content

private fun JsonObject.stringOrNull(vararg keys: String): String? {
    for (k in keys) {
        (this[k] as? JsonPrimitive)?.let { return it.content }
    }
    return null
}

private fun JsonObject.intOrNull(vararg keys: String): Int? {
    for (k in keys) {
        (this[k] as? JsonPrimitive)?.let {
            return it.content.toIntOrNull()
        }
    }
    return null
}

private fun JsonPrimitive.toLongOrNull(): Long? = content.toLongOrNull()
