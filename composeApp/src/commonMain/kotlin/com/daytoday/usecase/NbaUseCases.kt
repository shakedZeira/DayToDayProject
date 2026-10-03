package com.daytoday.usecase

import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews
import com.daytoday.repository.NbaRepository
import com.daytoday.repository.Result
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

class NbaUseCases @Inject constructor(
    private val repository: NbaRepository
) {
    suspend fun getScoreboard(
        date: String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
    ): UseCaseResult<List<NbaGame>> {
        val cached = repository.getCachedGames()
        val result = repository.getScoreboard(date)
        return when (result) {
            is Result.Success -> {
                val data = result.data
                // Empty list means "no games that day" — never mark as stale.
                if (data.isEmpty()) {
                    return UseCaseResult(result, false)
                }
                // Stale when we're showing cached data that was already in the DB
                // before this fetch cycle (i.e. the network hasn't refreshed it yet).
                val isStale = cached.isNotEmpty() &&
                    cached.any { it.id in data.map { it.id } } &&
                    cached.size == data.size
                UseCaseResult(result, isStale)
            }
            is Result.Failure -> UseCaseResult(result, cached.isNotEmpty())
        }
    }

    suspend fun getGameDetail(gameId: String): UseCaseResult<NbaGame> {
        val cached = repository.getCachedGames().firstOrNull { it.id == gameId }
        val result = repository.getGameDetail(gameId)
        return when (result) {
            is Result.Success -> UseCaseResult(result, cached != null)
            is Result.Failure -> UseCaseResult(result, cached != null)
        }
    }

    suspend fun getNews(limit: Int = 20): UseCaseResult<List<NbaNews>> {
        val cached = repository.getCachedNews()
        val result = repository.getNews(limit)
        return when (result) {
            is Result.Success -> {
                val isStale = cached.isNotEmpty() && cached.any { it.id in result.data.map { it.id } }
                UseCaseResult(result, isStale)
            }
            is Result.Failure -> UseCaseResult(result, cached.isNotEmpty())
        }
    }

    suspend fun getInjuries(teamId: String?): UseCaseResult<List<Injury>> {
        val cached = repository.getCachedNews() // This doesn't exist for injuries, simplify
        val result = repository.getInjuries(teamId)
        return when (result) {
            is Result.Success -> UseCaseResult(result, false) // Simplified - could check cache
            is Result.Failure -> UseCaseResult(result, false)
        }
    }
}

data class UseCaseResult<out T>(
    val result: Result<T>,
    val isStale: Boolean
)