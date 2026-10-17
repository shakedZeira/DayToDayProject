package com.daytoday.data.notification

import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.StatsSource
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
 * stats plus WEEK-source goals.
 */
object SummaryContentBuilder {

    private const val MAX_GOAL_LINES = 5

    /**
     * The receiver runs under `goAsync()` (a ~10s budget), and the step read
     * goes through Health Connect, which can be slow to respond. Bound both
     * stats reads and fall back to zeroed stats so the notification still goes
     * out with whatever workout data is available.
     */
    private const val TODAY_STATS_TIMEOUT_MS = 5_000L
    private const val WEEK_STATS_TIMEOUT_MS = 3_000L

    private val titleFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    suspend fun build(
        kind: SummaryKind,
        localSummaryUseCases: LocalSummaryUseCases,
        settingsManager: SettingsManager,
    ): SummaryContent {
        val day = withTimeoutOrNull(TODAY_STATS_TIMEOUT_MS) { localSummaryUseCases.todayStats() }
            ?: LocalDayStats()
        val week = withTimeoutOrNull(WEEK_STATS_TIMEOUT_MS) { localSummaryUseCases.weekStats() }
            ?: LocalWeekStats()
        val goals = runCatching { settingsManager.goals.first() }.getOrDefault(emptyList())

        return when (kind) {
            SummaryKind.DAILY -> SummaryContent(
                title = "Daily Summary · ${LocalDate.now().format(titleFormatter)}",
                lines = dailyLines(day, week, goals),
            )
            SummaryKind.WEEKLY -> SummaryContent(
                title = "Weekly Summary",
                lines = weeklyLines(day, week, goals),
            )
        }
    }

    private fun dailyLines(day: LocalDayStats, week: LocalWeekStats, goals: List<GoalEntry>): List<String> {
        val workoutLine = if (day.completedSessions == 0) {
            "No workouts logged today"
        } else {
            "${workouts(day.completedSessions)} · ${day.durationMinutes} min · ${fmt(day.totalVolume)} kg volume"
        }
        val lines = mutableListOf(
            workoutLine,
            "Calories burned: ${fmt(day.totalCalories.toLong())} kcal " +
                "(steps ${fmt(day.stepsCalories.toLong())} + workout ${fmt(day.workoutCalories.toLong())})",
            "Steps: ${fmt(day.steps)}",
        )
        lines += goalLines(goals, StatsSource.TODAY, day, week)
        return lines
    }

    private fun weeklyLines(day: LocalDayStats, week: LocalWeekStats, goals: List<GoalEntry>): List<String> {
        val workoutLine = if (week.workouts == 0) {
            "No workouts logged this week"
        } else {
            "${workouts(week.workouts)} · ${week.durationMinutes} min · ${fmt(week.volume)} kg volume"
        }
        val lines = mutableListOf(
            workoutLine,
            "Calories burned: ${fmt(week.caloriesBurned.toLong())} kcal " +
                "(steps ${fmt(week.stepCalories.toLong())} + workout ${fmt(week.workoutCalories.toLong())})",
        )
        lines += goalLines(goals, StatsSource.WEEK, day, week)
        return lines
    }

    private fun goalLines(
        goals: List<GoalEntry>,
        source: StatsSource,
        day: LocalDayStats,
        week: LocalWeekStats,
    ): List<String> {
        val relevant = goals.filter { it.type.source == source }
        if (relevant.isEmpty()) return emptyList()

        val lines = mutableListOf("Goals:")
        relevant.take(MAX_GOAL_LINES).forEach { entry ->
            val current = currentValue(entry.type, day, week)
            val check = if (current >= entry.target) " ✓" else ""
            lines += "• ${entry.type.label}: ${fmt(current)}/${fmt(entry.target)} ${entry.type.unit}$check"
        }
        if (relevant.size > MAX_GOAL_LINES) {
            lines += "• +${relevant.size - MAX_GOAL_LINES} more"
        }
        return lines
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

    private fun fmt(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

    private fun fmt(value: Double): String = String.format(Locale.getDefault(), "%,.0f", value)
}
