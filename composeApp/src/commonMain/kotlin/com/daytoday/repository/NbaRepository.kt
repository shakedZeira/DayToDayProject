package com.daytoday.repository

import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews

interface NbaRepository {
    suspend fun getScoreboard(date: String): Result<List<NbaGame>>
    suspend fun getGameDetail(gameId: String): Result<NbaGame>
    suspend fun getNews(limit: Int): Result<List<NbaNews>>
    suspend fun getInjuries(teamId: String?): Result<List<Injury>>
    suspend fun getCachedGames(): List<NbaGame>
    suspend fun getCachedNews(): List<NbaNews>
    suspend fun cacheGames(games: List<NbaGame>)
    suspend fun cacheNews(news: List<NbaNews>)
}