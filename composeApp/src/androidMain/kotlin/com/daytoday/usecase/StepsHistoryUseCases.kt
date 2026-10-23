package com.daytoday.usecase

import android.content.Context
import com.daytoday.data.health.HealthPermissionState
import com.daytoday.data.health.HealthRepository
import com.daytoday.data.health.HealthSdkStatus
import com.daytoday.model.UserProfile
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class StepsHistoryUseCases @Inject constructor(
    private val healthRepository: HealthRepository,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context,
) {
    internal var cacheTtlMs: Long = 60_000L
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    private data class CacheKey(val period: StepsPeriod, val today: LocalDate, val goal: Long)
    private data class CacheEntry(val history: StepsHistory, val storedAtMs: Long)

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<CacheKey, CacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?) = size > 8
    }

    private suspend fun readCache(key: CacheKey): StepsHistory? = cacheMutex.withLock {
        val entry = cache[key] ?: return@withLock null
        if (nowMs() - entry.storedAtMs > cacheTtlMs) {
            cache.remove(key)
            null
        } else entry.history
    }

    private suspend fun writeCache(key: CacheKey, history: StepsHistory) = cacheMutex.withLock {
        cache[key] = CacheEntry(history, nowMs())
    }

    suspend fun invalidateCache() {
        cacheMutex.withLock { cache.clear() }
    }

    suspend fun hasPermission(): Boolean = withContext(Dispatchers.IO) {
        healthRepository.getHealthPermissionState(context).getOrNull() == HealthPermissionState.GRANTED
    }

    suspend fun requestPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { healthRepository.requestStepsPermission(context) }.getOrDefault(false)
    }

    suspend fun hasBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { healthRepository.hasBackgroundReadPermission(context) }.getOrDefault(false)
    }

    suspend fun requestBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { healthRepository.requestBackgroundReadPermission(context) }.getOrDefault(false)
    }

    suspend fun healthSdkStatus(): HealthSdkStatus = withContext(Dispatchers.IO) {
        runCatching { healthRepository.isSdkAvailable(context) }.getOrDefault(HealthSdkStatus.UNAVAILABLE)
    }

    private fun timeoutFor(period: StepsPeriod): Long = when (period) {
        StepsPeriod.DAY -> 5000L
        StepsPeriod.WEEK -> 8000L
        StepsPeriod.MONTH -> 15000L
        StepsPeriod.YEAR -> 20000L
    }

    suspend fun load(period: StepsPeriod, forceRefresh: Boolean = false): StepsLoadOutcome = withContext(Dispatchers.IO) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val permissionState = runCatching { healthRepository.getHealthPermissionState(context).getOrNull() }.getOrNull()
        if (permissionState != HealthPermissionState.GRANTED) {
            return@withContext StepsLoadOutcome.PermissionMissing
        }
        val goal = runCatching {
            val goals = settingsManager.goals.first()
            goals.firstOrNull { it.type == GoalType.STEPS_TODAY }?.target?.toLong()?.coerceAtLeast(1L)
        }.getOrNull() ?: GoalType.STEPS_TODAY.defaultValue.toLong()

        val cacheKey = CacheKey(period, today, goal)
        if (!forceRefresh) {
            val cached = readCache(cacheKey)
            if (cached != null) {
                return@withContext StepsLoadOutcome.Success(cached)
            }
        }

        try {
            val result = withTimeoutOrNull(timeoutFor(period)) {
                when (period) {
                    StepsPeriod.DAY -> loadDay(today, goal)
                    StepsPeriod.WEEK -> loadWeek(today, goal)
                    StepsPeriod.MONTH -> loadMonth(today, goal)
                    StepsPeriod.YEAR -> loadYear(today, goal)
                }
            }
            if (result == null) {
                return@withContext StepsLoadOutcome.Failure("Reading steps timed out. Try again.")
            }
            val history = result
            writeCache(cacheKey, history)
            StepsLoadOutcome.Success(history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StepsLoadOutcome.Failure(e.message ?: "Could not load steps")
        }
    }

    private suspend fun loadDay(today: LocalDate, goal: Long): StepsHistory {
        val range = StepsAggregator.periodRange(StepsPeriod.DAY, today)
        val map = healthRepository.stepsInRange(context, range.start, range.endInclusive)
        if (map.isEmpty()) {
            throw IllegalStateException("Could not read steps from Health Connect. Check the permission and try again.")
        }
        val days = StepsAggregator.buildDays(range, today, map)
        val buckets = StepsAggregator.dailyBuckets(StepsPeriod.DAY, days, goal)
        val stats = StepsAggregator.stats(days, goal, today)
        val previousRange = StepsAggregator.previousRange(StepsPeriod.DAY, today)
        val previousTotal = healthRepository.totalSteps(context, previousRange.start, previousRange.endInclusive)
        val todaySteps = map[today] ?: 0L
        val todayHasData = map.containsKey(today)
        return buildHistory(StepsPeriod.DAY, range, goal, days, buckets, stats, previousTotal, todaySteps, todayHasData)
    }

    private suspend fun loadWeek(today: LocalDate, goal: Long): StepsHistory {
        val range = StepsAggregator.periodRange(StepsPeriod.WEEK, today)
        val effectiveEnd = minOf(range.endInclusive, today)
        val map = healthRepository.stepsInRange(context, range.start, effectiveEnd)
        if (map.isEmpty()) {
            throw IllegalStateException("Could not read steps from Health Connect. Check the permission and try again.")
        }
        val days = StepsAggregator.buildDays(DayRange(range.start, range.endInclusive), today, map)
        val buckets = StepsAggregator.dailyBuckets(StepsPeriod.WEEK, days, goal)
        val stats = StepsAggregator.stats(days, goal, today)
        val previousRange = StepsAggregator.previousRange(StepsPeriod.WEEK, today)
        val previousTotal = healthRepository.totalSteps(context, previousRange.start, previousRange.endInclusive)
        val todaySteps = map[today] ?: 0L
        val todayHasData = map.containsKey(today)
        return buildHistory(StepsPeriod.WEEK, range, goal, days, buckets, stats, previousTotal, todaySteps, todayHasData)
    }

    private suspend fun loadMonth(today: LocalDate, goal: Long): StepsHistory {
        val range = StepsAggregator.periodRange(StepsPeriod.MONTH, today)
        val effectiveEnd = minOf(range.endInclusive, today)
        val map = healthRepository.stepsInRange(context, range.start, effectiveEnd)
        if (map.isEmpty()) {
            throw IllegalStateException("Could not read steps from Health Connect. Check the permission and try again.")
        }
        val days = StepsAggregator.buildDays(DayRange(range.start, range.endInclusive), today, map)
        val buckets = StepsAggregator.dailyBuckets(StepsPeriod.MONTH, days, goal)
        val stats = StepsAggregator.stats(days, goal, today)
        val previousRange = StepsAggregator.previousRange(StepsPeriod.MONTH, today)
        val previousTotal = healthRepository.totalSteps(context, previousRange.start, previousRange.endInclusive)
        val todaySteps = map[today] ?: 0L
        val todayHasData = map.containsKey(today)
        return buildHistory(StepsPeriod.MONTH, range, goal, days, buckets, stats, previousTotal, todaySteps, todayHasData)
    }

    private suspend fun loadYear(today: LocalDate, goal: Long): StepsHistory {
        val range = StepsAggregator.periodRange(StepsPeriod.YEAR, today)
        val monthTotals = mutableMapOf<Int, Long>()
        for (month in 1..12) {
            val monthStart = LocalDate.of(today.year, month, 1)
            if (monthStart.isAfter(today)) break
            val monthEnd = monthStart.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
            val end = minOf(monthEnd, today)
            val total = runCatching { healthRepository.totalSteps(context, monthStart, end) }.getOrDefault(0L)
            if (total > 0 || monthStart.isBefore(today) || (monthStart.monthValue == today.monthValue)) {
                monthTotals[month] = total
            }
        }
        val todayMap = runCatching { healthRepository.stepsInRange(context, today, today) }.getOrDefault(emptyMap())
        val todaySteps = todayMap[today] ?: 0L
        val todayHasData = todayMap.isNotEmpty()
        val buckets = StepsAggregator.monthlyBuckets(today, monthTotals, goal)
        val stats = StepsAggregator.yearStats(buckets, goal, today)
        val previousRange = StepsAggregator.previousRange(StepsPeriod.YEAR, today)
        val previousTotal = healthRepository.totalSteps(context, previousRange.start, previousRange.endInclusive)
        return buildHistory(StepsPeriod.YEAR, range, goal, emptyList(), buckets, stats, previousTotal, todaySteps, todayHasData)
    }

    private suspend fun buildHistory(
        period: StepsPeriod,
        range: DayRange,
        goal: Long,
        days: List<StepsDay>,
        buckets: List<StepsBucket>,
        stats: StepsStats,
        previousTotal: Long?,
        todaySteps: Long,
        todayHasData: Boolean,
    ): StepsHistory {
        val profile = runCatching { settingsManager.userProfile.first() }.getOrDefault(UserProfile())
        val paceTarget = goal * stats.elapsedDays.toLong()
        val listRows = if (period == StepsPeriod.YEAR) StepsAggregator.monthListRows(buckets) else StepsAggregator.dayListRows(days, goal)
        return StepsHistory(
            period = period,
            range = range,
            goal = goal,
            paceTarget = paceTarget,
            todaySteps = todaySteps,
            todayHasData = todayHasData,
            days = days,
            buckets = buckets,
            listRows = listRows,
            stats = stats,
            previousTotal = previousTotal,
            changePercent = StepsAggregator.changePercent(stats.total, previousTotal),
            totalDistanceKm = StepMetrics.distanceKm(stats.total, profile.heightCm, profile.isMale),
            totalCalories = StepMetrics.calories(stats.total, profile.weightKg),
            generatedAtMs = nowMs(),
        )
    }
}
