package com.daytoday.data.notification

import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.StatsSource
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Rendered text of a summary notification. */
data class SummaryContent(
    val title: String,
    val lines: List<String>,
) {
    val body: String = lines.joinToString(separator = "\n")
    val singleLine: String = lines.firstOrNull().orEmpty()
}

/**
 * Builds the text of the daily and weekly summary notifications from on-device
 * data only (Room workout cache, Health Connect steps via [LocalSummaryUseCases],
 * and the goal list from [SettingsManager]).
 *
 * One builder serves both windows — the daily notification reports today's
 * stats plus TODAY-source goals, the weekly notification reports this week's
 * stats plus WEEK-source goals. A window that neither the kind nor the goals
 * need is not read at all, which keeps the alarm-started cold path short.
 *
 * When a fresh step read comes back as zero, the last same-day (or same-week)
 * snapshot from [SummaryStatsCache] is shown with an "(as of HH:mm)" annotation
 * instead of a misleading `0`. With no snapshot at all the value is rendered as
 * an em dash rather than claiming the user walked nothing.
 */
object SummaryContentBuilder {

    private const val MAX_GOAL_LINES = 5

    /** Goals whose value includes Health Connect steps, so an unread step source makes them unknown. */
    private val STEP_DEPENDENT_GOALS = setOf(
        GoalType.CALORIES_TODAY,
        GoalType.STEPS_TODAY,
        GoalType.CALORIES_BURNED_PER_WEEK,
    )

    /** Shown when the step source is unavailable and nothing is cached. */
    internal const val UNKNOWN_VALUE = "—"

    private val titleFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    internal data class DayView(
        val stats: LocalDayStats,
        val stepsAsOf: LocalTime?,
        val stepsKnown: Boolean,
    )

    internal data class WeekView(
        val stats: LocalWeekStats,
        val stepsAsOf: LocalTime?,
        val stepsKnown: Boolean,
    )

    /**
     * Builds the content for [kind].
     *
     * [nowMillis] is injectable so the day/week bucketing and the cache lookup
     * can be tested deterministically. [cache] is optional: without it the
     * builder renders the fresh values as-is (used by tests and callers that do
     * not post notifications).
     */
    suspend fun build(
        kind: SummaryKind,
        localSummaryUseCases: LocalSummaryUseCases,
        settingsManager: SettingsManager,
        cache: SummaryStatsCache? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): SummaryContent {
        val goals = loadGoals(settingsManager)
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        // Each kind renders only its own goal source (`dailyLines` uses TODAY,
        // `weeklyLines` uses WEEK), so the other window is never needed. Keeping
        // the daily build to a single stats read halves the cold-start work.
        val needsDay = kind == SummaryKind.DAILY
        val needsWeek = kind == SummaryKind.WEEKLY

        val freshDay = if (needsDay) localSummaryUseCases.todayStats() else LocalDayStats()
        val freshWeek = if (needsWeek) localSummaryUseCases.weekStats() else LocalWeekStats()

        val day = if (needsDay) {
            resolveDay(freshDay, cache?.dayFor(today), cacheEnabled = cache != null)
        } else {
            DayView(LocalDayStats(), null, false)
        }
        val week = if (needsWeek) {
            resolveWeek(freshWeek, cache?.weekFor(weekStart(today)), cacheEnabled = cache != null)
        } else {
            WeekView(LocalWeekStats(), null, false)
        }

        // Persist only real reads: a zero from a timeout must not overwrite a good snapshot.
        if (needsDay && freshDay.steps > 0) {
            cache?.saveDay(freshDay, date = today, capturedAtMs = nowMillis)
        }
        if (needsWeek && freshWeek.steps > 0) {
            cache?.saveWeek(freshWeek, weekStart(today), capturedAtMs = nowMillis)
        }

        return when (kind) {
            SummaryKind.DAILY -> SummaryContent(
                title = "Daily Summary · ${today.format(titleFormatter)}",
                lines = dailyLines(day.stats, week.stats, goals, day.stepsAsOf, day.stepsKnown),
            )
            SummaryKind.WEEKLY -> SummaryContent(
                title = "Weekly Summary",
                lines = weeklyLines(day.stats, week.stats, goals, week.stepsAsOf, week.stepsKnown),
            )
        }
    }

    /** Monday-based week start, matching [LocalSummaryUseCases.weekStats]. */
    internal fun weekStart(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    internal fun dailyLines(
        day: LocalDayStats,
        week: LocalWeekStats,
        goals: List<GoalEntry>,
        stepsAsOf: LocalTime? = null,
        stepsKnown: Boolean = true,
    ): List<String> {
        val workoutLine = if (day.completedSessions == 0) {
            "No workouts logged today"
        } else {
            "${workouts(day.completedSessions)} · ${day.durationMinutes} min · ${fmt(day.totalVolume)} kg volume"
        }
        val stepsLine = if (!stepsKnown) {
            "Steps: $UNKNOWN_VALUE"
        } else {
            val asOf = stepsAsOf?.let { " (as of ${it.format(timeFormatter)})" }.orEmpty()
            "Steps: ${formatCount(day.steps)}$asOf"
        }
        val caloriesLine = if (!stepsKnown) {
            "Calories burned: ${formatCount(day.workoutCalories.toLong())} kcal (workouts only)"
        } else {
            val asOf = stepsAsOf?.let { " (as of ${it.format(timeFormatter)})" }.orEmpty()
            "Calories burned: ${formatCount(day.totalCalories.toLong())} kcal " +
                "(steps ${formatCount(day.stepsCalories.toLong())} + workout ${formatCount(day.workoutCalories.toLong())})$asOf"
        }
        val lines = mutableListOf(
            workoutLine,
            caloriesLine,
            stepsLine,
        )
        lines += goalLines(goals, StatsSource.TODAY, day, week, stepsKnown)
        return lines
    }

    internal fun weeklyLines(
        day: LocalDayStats,
        week: LocalWeekStats,
        goals: List<GoalEntry>,
        stepsAsOf: LocalTime? = null,
        stepsKnown: Boolean = true,
    ): List<String> {
        val workoutLine = if (week.workouts == 0) {
            "No workouts logged this week"
        } else {
            "${workouts(week.workouts)} · ${week.durationMinutes} min · ${fmt(week.volume)} kg volume"
        }
        val caloriesLine = if (!stepsKnown) {
            "Calories burned: ${formatCount(week.workoutCalories.toLong())} kcal (workouts only)"
        } else {
            val asOf = stepsAsOf?.let { " (as of ${it.format(timeFormatter)})" }.orEmpty()
            "Calories burned: ${formatCount(week.caloriesBurned.toLong())} kcal " +
                "(steps ${formatCount(week.stepCalories.toLong())} + workout ${formatCount(week.workoutCalories.toLong())})$asOf"
        }
        val lines = mutableListOf(workoutLine, caloriesLine)
        lines += goalLines(goals, StatsSource.WEEK, day, week, stepsKnown)
        return lines
    }

    internal fun goalLines(
        goals: List<GoalEntry>,
        source: StatsSource,
        day: LocalDayStats,
        week: LocalWeekStats,
        stepsKnown: Boolean = true,
    ): List<String> {
        val relevant = goals.filter { it.type.source == source }
        if (relevant.isEmpty()) return emptyList()

        val lines = mutableListOf("Goals:")
        relevant.take(MAX_GOAL_LINES).forEach { entry ->
            if (!stepsKnown && entry.type in STEP_DEPENDENT_GOALS) {
                lines += "• ${entry.type.label}: $UNKNOWN_VALUE/${fmt(entry.target)} ${entry.type.unit}"
                return@forEach
            }
            val current = currentValue(entry.type, day, week)
            val check = if (current >= entry.target) " ✓" else ""
            lines += "• ${entry.type.label}: ${fmt(current)}/${fmt(entry.target)} ${entry.type.unit}$check"
        }
        if (relevant.size > MAX_GOAL_LINES) {
            lines += "• +${relevant.size - MAX_GOAL_LINES} more"
        }
        return lines
    }

    /** Formats a whole number with the locale's grouping separator, e.g. `7,639`. */
    internal fun formatCount(value: Long): String =
        String.format(Locale.getDefault(), "%,d", value)

    internal fun resolveDay(
        fresh: LocalDayStats,
        cached: CachedDayStats?,
        cacheEnabled: Boolean,
    ): DayView {
        val useCache = fresh.steps <= 0 && (cached?.stats?.steps ?: 0L) > 0
        val stats = if (useCache && cached != null) {
            fresh.copy(
                steps = cached.stats.steps,
                stepsCalories = cached.stats.stepsCalories,
                totalCalories = cached.stats.stepsCalories + fresh.workoutCalories,
            )
        } else {
            fresh
        }
        return DayView(
            stats = stats,
            stepsAsOf = if (useCache) cached?.let { timeOf(it.capturedAtMs) } else null,
            stepsKnown = fresh.steps > 0 || useCache || !cacheEnabled || fresh.stepsReadable,
        )
    }

    internal fun resolveWeek(
        fresh: LocalWeekStats,
        cached: CachedWeekStats?,
        cacheEnabled: Boolean,
    ): WeekView {
        val useCache = fresh.steps <= 0 && (cached?.stats?.steps ?: 0L) > 0
        val stats = if (useCache && cached != null) {
            fresh.copy(
                steps = cached.stats.steps,
                stepCalories = cached.stats.stepCalories,
                caloriesBurned = fresh.workoutCalories + cached.stats.stepCalories,
            )
        } else {
            fresh
        }
        return WeekView(
            stats = stats,
            stepsAsOf = if (useCache) cached?.let { timeOf(it.capturedAtMs) } else null,
            stepsKnown = fresh.steps > 0 || useCache || !cacheEnabled || fresh.stepsReadable,
        )
    }

    private fun timeOf(epochMillis: Long): LocalTime =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()

    private suspend fun loadGoals(settingsManager: SettingsManager): List<GoalEntry> = try {
        settingsManager.goals.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** Mirrors the mapping used by the Goals screen (see GoalsViewModel). */
    private fun currentValue(
        type: GoalType,
        day: LocalDayStats,
        week: LocalWeekStats,
    ): Double = when (type) {
        GoalType.CALORIES_TODAY -> day.totalCalories.toDouble()
        GoalType.WORKOUTS_PER_WEEK -> week.workouts.toDouble()
        GoalType.STEPS_TODAY -> day.steps.toDouble()
        GoalType.ACTIVE_MINUTES_TODAY -> day.durationMinutes.toDouble()
        GoalType.WORKOUT_MINUTES_PER_WEEK -> week.durationMinutes.toDouble()
        GoalType.CALORIES_BURNED_PER_WEEK -> week.caloriesBurned.toDouble()
        GoalType.LIFTING_VOLUME_PER_WEEK -> week.volume
        else -> 0.0
    }

    private fun workouts(count: Int): String = if (count == 1) "1 workout" else "$count workouts"

    private fun fmt(value: Double): String = String.format(Locale.getDefault(), "%,.0f", value)
}
