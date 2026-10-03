package com.daytoday.data.remote

import com.daytoday.repository.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ESPN Site API client for NBA news, injuries, and transactions.
 * Base: https://site.api.espn.com/apis/site/v2/sports/basketball/nba/
 * No auth required.
 */
@Singleton
class NbaEspnClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(this@NbaEspnClient.json) }
        install(Logging) { level = LogLevel.BASIC }
        defaultRequest {
            url("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/")
            header("Accept", "application/json")
        }
    }

    suspend fun getNews(limit: Int = 20): Result<List<EspNewsArticle>> = runCatching {
        val resp = client.get("news") { parameters { "limit" to limit.toString() } }
        val root = resp.body<JsonObject>()
        val articles = root["articles"] as? JsonArray ?: emptyArray()
        articles.map { EspNewsArticle(it as JsonObject) }
    }

    suspend fun getInjuries(): Result<List<EspInjuryParsed>> = runCatching {
        val resp = client.get("injuries")
        val root = resp.body<JsonObject>()
        val teams = root["injuries"] as? JsonArray ?: emptyArray()
        val all = mutableListOf<EspInjuryParsed>()
        for (elem in teams) {
            val obj = elem as? JsonObject ?: continue
            all.addAll(parseInjuryTeam(obj))
        }
        all
    }

    suspend fun getTransactions(): Result<List<EspTransaction>> = runCatching {
        val resp = client.get("transactions")
        val root = resp.body<JsonObject>()
        val txs = root["transactions"] as? JsonArray ?: emptyArray()
        txs.map { EspTransaction(it as JsonObject) }
    }
}

// ---------- ESPN news ----------

data class EspNewsArticle(val root: JsonObject) {
    val id: String get() = root["id"]?.jsonPrimitiveContent() ?: ""
    val headline: String get() = root["headline"]?.jsonPrimitiveContent() ?: ""
    val description: String get() = root["description"]?.jsonPrimitiveContent() ?: ""
    val published: String get() = root["published"]?.jsonPrimitiveContent() ?: ""
    val url: String get() {
        val links = root["links"] as? JsonObject ?: return ""
        val web = links["web"] as? JsonObject ?: return ""
        return web["href"]?.jsonPrimitiveContent() ?: ""
    }
    val imageUrl: String? get() {
        val images = root["images"] as? JsonArray ?: return null
        return images.firstOrNull()?.jsonObject?.getString("url")
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

private fun parseInjuryTeam(teamRoot: JsonObject): List<EspInjuryParsed> {
    val teamName = teamRoot["displayName"]?.jsonPrimitiveContent() ?: "Unknown"
    val teamAbbr = teamRoot["abbreviation"]?.jsonPrimitiveContent()
    val teamId = teamRoot["id"]?.jsonPrimitiveContent() ?: ""
    val injuries = teamRoot["injuries"] as? JsonArray ?: emptyArray()
    return injuries.map { (it as? JsonObject ?: return@map).parseOneInjury(teamId, teamName, teamAbbr) }
}

private fun JsonObject.parseOneInjury(teamId: String, teamName: String, teamAbbr: String?): EspInjuryParsed {
    val athlete = this["athlete"] as? JsonObject
    val details = this["details"] as? JsonObject
    val type = this["type"] as? JsonObject
    return EspInjuryParsed(
        id = this["id"]?.jsonPrimitiveContent() ?: "",
        playerName = athlete?.getString("displayName")
            ?: "${athlete?.getString("firstName") ?: ""} ${athlete?.getString("lastName") ?: ""}".trim().ifEmpty { null },
        playerId = athlete?.getString("id"),
        teamId = teamId,
        teamName = teamName,
        teamAbbreviation = teamAbbr,
        status = this["status"]?.jsonPrimitiveContent() ?: "",
        date = this["date"]?.jsonPrimitiveContent() ?: "",
        comment = this["longComment"]?.jsonPrimitiveContent()
            ?: this["shortComment"]?.jsonPrimitiveContent() ?: "",
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
    val id: String get() = root["id"]?.jsonPrimitiveContent() ?: ""
    val date: String get() = root["date"]?.jsonPrimitiveContent() ?: ""
    val description: String get() = root["description"]?.jsonPrimitiveContent() ?: ""
    val teamName: String? get() = root["team"]?.jsonObject?.getString("name")
    val teamAbbreviation: String? get() = root["team"]?.jsonObject?.getString("abbreviation")
}

// ---------- JSON helpers ----------

private fun JsonPrimitive.jsonPrimitiveContent(): String? = content

private fun JsonObject.getString(key: String): String? = (this[key] as? JsonPrimitive)?.content

private fun JsonPrimitive.jsonPrimitiveContent(): String? = this.content
