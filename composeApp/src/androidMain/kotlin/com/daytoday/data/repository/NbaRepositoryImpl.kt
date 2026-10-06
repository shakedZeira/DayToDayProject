package com.daytoday.data.repository

import android.util.Log
import com.daytoday.data.database.InjuryDao
import com.daytoday.data.database.NbaGameDao
import com.daytoday.data.database.NbaNewsDao
import com.daytoday.data.database.NbaGameEntity
import com.daytoday.data.database.NbaNewsEntity
import com.daytoday.data.remote.BbsMatchDto
import com.daytoday.data.remote.BbsMatchupDto
import com.daytoday.data.remote.EspInjuryParsed
import com.daytoday.data.remote.EspNewsArticle
import com.daytoday.data.remote.EspScoreboardGame
import com.daytoday.data.remote.EspScoreboardTeam
import com.daytoday.data.remote.NbaBigBallsClient
import com.daytoday.data.remote.NbaEspnClient
import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews
import com.daytoday.model.NbaTeam
import com.daytoday.repository.NbaRepository
import com.daytoday.repository.Result
import com.daytoday.ui.screen.nba.matchesTeamFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NbaRepositoryImpl @Inject constructor(
    private val gameDao: NbaGameDao,
    private val newsDao: NbaNewsDao,
    private val injuryDao: InjuryDao,
    private val bbsClient: NbaBigBallsClient,
    private val espnClient: NbaEspnClient,
) : NbaRepository {
    private val TAG = "NbaRepository"

    // ---------- Scoreboard ----------

    override suspend fun getScoreboard(date: String): Result<List<NbaGame>> = withContext(Dispatchers.IO) {
        val cached = gameDao.getGamesByDate(date).map { it.toModel() }
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Returning cached scoreboard for $date (${cached.size} games)")
            launchBackgroundRefresh(date)
            Result.success(cached)
        } else {
            // Try real APIs; seed demo data if both are unavailable.
            fetchScoreboardFromApi(date)
                .also { result ->
                    if (result.isFailure) {
                        Log.w(TAG, "Real APIs failed, seeding demo games", result.exceptionOrNull())
                        seedDemoGames(date)
                    }
                }
        }
    }

    private suspend fun fetchScoreboardFromApi(date: String): Result<List<NbaGame>> = withContext(Dispatchers.IO) {
        // 1) Try BigBallsData (real NBA game data, needs API key).
        val bbs = bbsClient.getMatches(date = if (date == "today") "today" else date)
        val bbsGames = bbs.getOrElse(emptyList())
        if (bbsGames.isNotEmpty()) {
            val games = bbsGames.map { it.toNbaGame(date) }
            val entities = games.map { NbaGameEntity.fromModel(it, date) }
            gameDao.insertAll(entities)
            Log.d(TAG, "BBS returned ${games.size} games for $date")
            return@withContext Result.success(games)
        }
        Log.w(TAG, "BBS failed or empty: ${bbs.exceptionOrNull()?.message}")

        // 2) Try ESPN scoreboard as fallback (free, no key).
        val espn = fetchEspnScoreboard(date)
        if (espn.isFailure) {
            val e = espn.exceptionOrNull()
            Log.w(TAG, "ESPN scoreboard failed", e)
            return@withContext Result.failure("Scoreboard unavailable", e)
        }
        val games = espn.getOrThrow()
        if (games.isNotEmpty()) {
            val entities = games.map { NbaGameEntity.fromModel(it, date) }
            gameDao.insertAll(entities)
            Log.d(TAG, "ESPN returned ${games.size} games for $date")
        }
        Result.success(games)
    }

    private fun BbsMatchDto.toNbaGame(date: String): NbaGame {
        val home = homeTeam
        val away = awayTeam
        val isLive = status == "in_progress"
        val isCompleted = status == "final" || status == "completed"
        val quarter = periods.firstOrNull { it.period != null }?.period ?: 0
        val timeRemaining = periods.firstOrNull { it.timeRemaining != null }?.timeRemaining ?: ""

        return NbaGame(
            id = matchId,
            homeTeam = NbaTeam(
                id = home.id ?: "",
                name = home.name ?: "",
                abbreviation = home.abbreviation ?: "",
                city = "",
                logoUrl = "",
                primaryColor = "",
                secondaryColor = "",
            ),
            awayTeam = NbaTeam(
                id = away.id ?: "",
                name = away.name ?: "",
                abbreviation = away.abbreviation ?: "",
                city = "",
                logoUrl = "",
                primaryColor = "",
                secondaryColor = "",
            ),
            homeScore = home.score ?: 0,
            awayScore = away.score ?: 0,
            status = status,
            startTime = System.currentTimeMillis(),
            quarter = quarter,
            timeRemaining = timeRemaining,
            isCompleted = isCompleted,
            boxScore = null,
        )
    }

    private suspend fun fetchEspnScoreboard(date: String): Result<List<NbaGame>> {
        val espn = espnClient.getScoreboard(date)
        if (espn.isFailure) {
            return Result.failure(espn.errorOrNull() ?: "ESPN scoreboard unavailable", espn.exceptionOrNull())
        }
        val games = espn.getOrThrow().map { game -> game.toNbaGame() }
        return Result.success(games)
    }

    private fun EspScoreboardGame.toNbaGame(): NbaGame = NbaGame(
        id = id,
        homeTeam = homeTeam.toNbaTeam(),
        awayTeam = awayTeam.toNbaTeam(),
        homeScore = homeScore,
        awayScore = awayScore,
        status = status,
        startTime = startTime,
        quarter = quarter,
        timeRemaining = timeRemaining,
        isCompleted = isCompleted,
        boxScore = null,
    )

    private fun EspScoreboardTeam.toNbaTeam(): NbaTeam = NbaTeam(
        id = id,
        name = name,
        abbreviation = abbreviation,
        city = "",
        logoUrl = logoUrl,
        primaryColor = color,
        secondaryColor = color,
    )

    private suspend fun launchBackgroundRefresh(date: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = fetchScoreboardFromApi(date)
                if (result.isSuccess) {
                    val games = result.getOrThrow()
                    Log.d(TAG, "Background refresh succeeded for $date (${games.size} games)")
                } else {
                    Log.w(TAG, "Background refresh failed for $date", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background refresh crashed for $date", e)
            }
        }
    }

    // ---------- Game detail (box score) ----------

    override suspend fun getGameDetail(gameId: String): Result<NbaGame> = withContext(Dispatchers.IO) {
        val cached = gameDao.getGameById(gameId)?.toModel()
        if (cached != null) {
            Log.d(TAG, "Returning cached game detail for $gameId")
            launchBackgroundBoxScore(gameId)
            Result.success(cached)
        } else {
            fetchBoxScore(gameId)
        }
    }

    private suspend fun fetchBoxScore(gameId: String): Result<NbaGame> = withContext(Dispatchers.IO) {
        val bbs = bbsClient.getMatchup(gameId)
        if (bbs.isFailure) {
            val e = bbs.exceptionOrNull()
            Log.w(TAG, "BBS matchup failed for $gameId", e)
            return@withContext Result.failure("Box score unavailable", e)
        }
        val matchup = bbs.getOrThrow()
        val err = matchup.error
        if (err != null) {
            return@withContext Result.failure(err)
        }
        val game = matchup.toNbaGame()
        val entity = NbaGameEntity.fromModel(game, "")
        gameDao.insert(entity)
        Log.d(TAG, "Cached box score for $gameId")
        Result.success(game)
    }

    private suspend fun launchBackgroundBoxScore(gameId: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = fetchBoxScore(gameId)
                if (result.isSuccess) {
                    Log.d(TAG, "Background box score succeeded for $gameId")
                }
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    private fun BbsMatchupDto.toNbaGame(): NbaGame {
        val home = homeTeam
        val away = awayTeam
        val isCompleted = status == "final" || status == "completed"
        return NbaGame(
            id = matchId ?: "",
            homeTeam = NbaTeam(
                id = home?.id ?: "",
                name = home?.name ?: "",
                abbreviation = home?.abbreviation ?: "",
                city = "",
                logoUrl = "",
                primaryColor = "",
                secondaryColor = "",
            ),
            awayTeam = NbaTeam(
                id = away?.id ?: "",
                name = away?.name ?: "",
                abbreviation = away?.abbreviation ?: "",
                city = "",
                logoUrl = "",
                primaryColor = "",
                secondaryColor = "",
            ),
            homeScore = home?.score ?: 0,
            awayScore = away?.score ?: 0,
            status = status ?: "unknown",
            startTime = System.currentTimeMillis(),
            quarter = 0,
            timeRemaining = "",
            isCompleted = isCompleted,
            boxScore = toBoxScore(),
        )
    }

    private fun BbsMatchupDto.toBoxScore(): com.daytoday.model.BoxScore? {
        val home = homeTeam ?: return null
        val away = awayTeam ?: return null
        return com.daytoday.model.BoxScore(
            gameId = matchId ?: "",
            homeTeamStats = com.daytoday.model.TeamStats(
                teamId = home.id ?: "",
                points = home.score ?: 0,
                rebounds = home.players.sumOf { it.stats?.rebounds ?: 0 },
                assists = home.players.sumOf { it.stats?.assists ?: 0 },
                steals = home.players.sumOf { it.stats?.steals ?: 0 },
                blocks = home.players.sumOf { it.stats?.blocks ?: 0 },
                turnovers = home.players.sumOf { it.stats?.turnovers ?: 0 },
                fgMade = home.players.sumOf { it.stats?.fgMade ?: 0 },
                fgAttempted = home.players.sumOf { it.stats?.fgAttempted ?: 0 },
                fg3Made = home.players.sumOf { it.stats?.fg3Made ?: 0 },
                fg3Attempted = home.players.sumOf { it.stats?.fg3Attempted ?: 0 },
                ftMade = home.players.sumOf { it.stats?.ftMade ?: 0 },
                ftAttempted = home.players.sumOf { it.stats?.ftAttempted ?: 0 },
                fastBreakPoints = 0,
                pointsInPaint = 0,
                largestLead = 0,
            ),
            awayTeamStats = com.daytoday.model.TeamStats(
                teamId = away.id ?: "",
                points = away.score ?: 0,
                rebounds = away.players.sumOf { it.stats?.rebounds ?: 0 },
                assists = away.players.sumOf { it.stats?.assists ?: 0 },
                steals = away.players.sumOf { it.stats?.steals ?: 0 },
                blocks = away.players.sumOf { it.stats?.blocks ?: 0 },
                turnovers = away.players.sumOf { it.stats?.turnovers ?: 0 },
                fgMade = away.players.sumOf { it.stats?.fgMade ?: 0 },
                fgAttempted = away.players.sumOf { it.stats?.fgAttempted ?: 0 },
                fg3Made = away.players.sumOf { it.stats?.fg3Made ?: 0 },
                fg3Attempted = away.players.sumOf { it.stats?.fg3Attempted ?: 0 },
                ftMade = away.players.sumOf { it.stats?.ftMade ?: 0 },
                ftAttempted = away.players.sumOf { it.stats?.ftAttempted ?: 0 },
                fastBreakPoints = 0,
                pointsInPaint = 0,
                largestLead = 0,
            ),
            playerStats = home.players.map { p ->
                com.daytoday.model.PlayerStat(
                    playerId = p.id ?: "",
                    playerName = p.name ?: "",
                    teamId = home.id ?: "",
                    minutes = "",
                    points = p.stats?.points ?: 0,
                    rebounds = p.stats?.rebounds ?: 0,
                    assists = p.stats?.assists ?: 0,
                    steals = p.stats?.steals ?: 0,
                    blocks = p.stats?.blocks ?: 0,
                    turnovers = p.stats?.turnovers ?: 0,
                    fgMade = p.stats?.fgMade ?: 0,
                    fgAttempted = p.stats?.fgAttempted ?: 0,
                    fg3Made = p.stats?.fg3Made ?: 0,
                    fg3Attempted = p.stats?.fg3Attempted ?: 0,
                    ftMade = p.stats?.ftMade ?: 0,
                    ftAttempted = p.stats?.ftAttempted ?: 0,
                    plusMinus = p.stats?.plusMinus ?: 0,
                )
            } + away.players.map { p ->
                com.daytoday.model.PlayerStat(
                    playerId = p.id ?: "",
                    playerName = p.name ?: "",
                    teamId = away.id ?: "",
                    minutes = "",
                    points = p.stats?.points ?: 0,
                    rebounds = p.stats?.rebounds ?: 0,
                    assists = p.stats?.assists ?: 0,
                    steals = p.stats?.steals ?: 0,
                    blocks = p.stats?.blocks ?: 0,
                    turnovers = p.stats?.turnovers ?: 0,
                    fgMade = p.stats?.fgMade ?: 0,
                    fgAttempted = p.stats?.fgAttempted ?: 0,
                    fg3Made = p.stats?.fg3Made ?: 0,
                    fg3Attempted = p.stats?.fg3Attempted ?: 0,
                    ftMade = p.stats?.ftMade ?: 0,
                    ftAttempted = p.stats?.ftAttempted ?: 0,
                    plusMinus = p.stats?.plusMinus ?: 0,
                )
            },
        )
    }

    // ---------- News ----------

    override suspend fun getNews(limit: Int): Result<List<NbaNews>> = withContext(Dispatchers.IO) {
        val cached = newsDao.getLatestNews(limit).map { it.toModel() }
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Returning cached news (${cached.size} articles)")
            launchBackgroundNews(limit)
            Result.success(cached)
        } else {
            fetchNewsFromApi(limit)
        }
    }

    private suspend fun fetchNewsFromApi(limit: Int): Result<List<NbaNews>> = withContext(Dispatchers.IO) {
        val espn = espnClient.getNews(limit)
        if (espn.isFailure) {
            val e = espn.exceptionOrNull()
            Log.w(TAG, "ESPN news failed", e)
            return@withContext Result.failure("NBA news unavailable", e)
        }
        val articles = espn.getOrThrow()
        val items = articles.map { it.toNbaNews() }
        val entities = items.map { NbaNewsEntity.fromModel(it) }
        newsDao.insertAll(entities)
        Log.d(TAG, "Cached ${items.size} ESPN news articles")
        Result.success(items)
    }

    private fun EspNewsArticle.toNbaNews(): NbaNews = NbaNews(
        id = id,
        title = headline,
        summary = description,
        url = url,
        imageUrl = imageUrl,
        source = source,
        publishedAt = publishedAtEpoch(),
        teamIds = teamIds,
    )

    private fun EspNewsArticle.publishedAtEpoch(): Long {
        return try {
            java.time.Instant.parse(published).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private suspend fun launchBackgroundNews(limit: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val r = fetchNewsFromApi(limit)
                if (r.isSuccess) Log.d(TAG, "Background news refresh succeeded")
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    // ---------- Injuries ----------

    override suspend fun getInjuries(teamId: String?): Result<List<Injury>> = withContext(Dispatchers.IO) {
        val cached = if (teamId != null) {
            injuryDao.getCachedInjuries(teamId).map { it.toModel() }
        } else {
            injuryDao.getCachedAllInjuries().map { it.toModel() }
        }
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Returning cached injuries for ${teamId ?: "all"} (${cached.size})")
            launchBackgroundInjuries(teamId)
            Result.success(cached)
        } else {
            fetchInjuriesFromApi(teamId)
        }
    }

    private suspend fun fetchInjuriesFromApi(teamId: String?): Result<List<Injury>> = withContext(Dispatchers.IO) {
        val espn = espnClient.getInjuries()
        if (espn.isFailure) {
            val e = espn.exceptionOrNull()
            Log.w(TAG, "ESPN injuries failed", e)
            return@withContext Result.failure("Injury reports unavailable", e)
        }
        val injuries = espn.getOrThrow().map { it.toNbaInjury() }
        val entities = injuries.map { com.daytoday.data.database.InjuryEntity.fromModel(it) }
        injuryDao.insertAll(entities)
        Log.d(TAG, "Cached ${injuries.size} ESPN injury reports")
        val filtered = if (teamId != null) {
            injuries.filter { injury -> injury.matchesTeamFilter(teamId) }
        } else {
            injuries
        }
        Result.success(filtered)
    }

    private fun EspInjuryParsed.toNbaInjury(): Injury = Injury(
        id = id,
        playerId = playerId ?: "",
        playerName = playerName ?: "",
        teamId = teamId,
        teamName = teamName,
        status = status,
        description = comment,
        startDate = try { java.time.Instant.parse(date).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() },
        endDate = returnDate?.let { try { java.time.Instant.parse(it).toEpochMilli() } catch (_: Exception) { null } },
        lastUpdated = lastUpdated,
    )

    private suspend fun launchBackgroundInjuries(teamId: String?) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val r = fetchInjuriesFromApi(teamId)
                if (r.isSuccess) Log.d(TAG, "Background injuries refresh succeeded")
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    // ---------- Cache helpers ----------

    override suspend fun getCachedGames(): List<NbaGame> = withContext(Dispatchers.IO) {
        gameDao.getAllGames().map { it.toModel() }
    }

    override suspend fun getCachedNews(): List<NbaNews> = withContext(Dispatchers.IO) {
        newsDao.getAllNews().map { it.toModel() }
    }

    override suspend fun cacheGames(games: List<NbaGame>) = withContext(Dispatchers.IO) {
        gameDao.insertAll(games.map { NbaGameEntity.fromModel(it, "") })
    }

    override suspend fun cacheNews(news: List<NbaNews>) = withContext(Dispatchers.IO) {
        newsDao.insertAll(news.map { NbaNewsEntity.fromModel(it) })
    }

    // ---------- Demo data seed (first-launch fallback) ----------

    private suspend fun seedDemoGames(date: String) {
        val demos = listOf(
            NbaGame(
                id = "demo_${date}_1",
                homeTeam = NbaTeam("1", "Lakers", "LAL", "Los Angeles", "", "", ""),
                awayTeam = NbaTeam("2", "Celtics", "BOS", "Boston", "", "", ""),
                homeScore = 0,
                awayScore = 0,
                status = "scheduled",
                startTime = System.currentTimeMillis(),
                quarter = 0,
                timeRemaining = "",
                isCompleted = false,
                boxScore = null,
            ),
            NbaGame(
                id = "demo_${date}_2",
                homeTeam = NbaTeam("3", "Warriors", "GSW", "Golden State", "", "", ""),
                awayTeam = NbaTeam("4", "Nuggets", "DEN", "Denver", "", "", ""),
                homeScore = 0,
                awayScore = 0,
                status = "scheduled",
                startTime = System.currentTimeMillis(),
                quarter = 0,
                timeRemaining = "",
                isCompleted = false,
                boxScore = null,
            ),
        )
        gameDao.insertAll(demos.map { NbaGameEntity.fromModel(it, date) })
        Log.d(TAG, "Seeded ${demos.size} demo games for $date")
    }
}
