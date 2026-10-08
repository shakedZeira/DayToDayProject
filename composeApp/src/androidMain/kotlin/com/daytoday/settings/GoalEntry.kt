package com.daytoday.settings

import kotlinx.serialization.Serializable

enum class StatsSource {
    TODAY,
    WEEK,
    NONE,
}

/** Time window a custom goal's target counts over. */
@Serializable
enum class GoalPeriod {
    DAY,
    WEEK,
}

@Serializable
enum class GoalType(
    val id: String,
    val label: String,
    val unit: String,
    val defaultValue: Double,
    val maxValue: Int,
    val source: StatsSource,
) {
    CALORIES_TODAY(
        id = "calories_today",
        label = "Calories today",
        unit = "kcal",
        defaultValue = 500.0,
        maxValue = 10_000,
        source = StatsSource.TODAY,
    ),
    WORKOUTS_PER_WEEK(
        id = "workouts_per_week",
        label = "Workouts per week",
        unit = "workouts",
        defaultValue = 2.0,
        maxValue = 14,
        source = StatsSource.WEEK,
    ),
    STEPS_TODAY(
        id = "steps_today",
        label = "Steps today",
        unit = "steps",
        defaultValue = 8_000.0,
        maxValue = 100_000,
        source = StatsSource.TODAY,
    ),
    ACTIVE_MINUTES_TODAY(
        id = "active_minutes_today",
        label = "Active minutes today",
        unit = "min",
        defaultValue = 30.0,
        maxValue = 1_440,
        source = StatsSource.TODAY,
    ),
    WORKOUT_MINUTES_PER_WEEK(
        id = "workout_minutes_per_week",
        label = "Workout minutes per week",
        unit = "min",
        defaultValue = 90.0,
        maxValue = 10_080,
        source = StatsSource.WEEK,
    ),
    CALORIES_BURNED_PER_WEEK(
        id = "calories_burned_per_week",
        label = "Calories burned per week",
        unit = "kcal",
        defaultValue = 2_500.0,
        maxValue = 50_000,
        source = StatsSource.WEEK,
    ),
    LIFTING_VOLUME_PER_WEEK(
        id = "lifting_volume_per_week",
        label = "Lifting volume per week",
        unit = "kg",
        defaultValue = 10_000.0,
        maxValue = 1_000_000,
        source = StatsSource.WEEK,
    ),
    ITALIAN_LESSONS(
        id = "italian_lessons",
        label = "Italian lessons",
        unit = "lessons",
        defaultValue = 3.0,
        maxValue = 30,
        source = StatsSource.NONE,
    ),
    CUSTOM(
        id = "custom",
        label = "Custom goal",
        unit = "",
        defaultValue = 1.0,
        maxValue = 99,
        source = StatsSource.NONE,
    );
}

@Serializable
data class GoalEntry(
    val id: String,
    val type: GoalType,
    val target: Double,
    val label: String = "",
    val completed: Boolean = false,
    val period: GoalPeriod = GoalPeriod.WEEK,
    val progress: Double = 0.0,
    /** ISO-8601 local date (LocalDate.toString()) of the Sunday a WEEK counter was last zeroed on; "" = never reset. */
    val lastResetWeekStart: String = "",
)

/** Maximum length of the free text typed into a [GoalType.CUSTOM] goal. */
const val MAX_CUSTOM_GOAL_LENGTH = 80

/** Valid target range (inclusive) for a [GoalType.CUSTOM] goal's quantity. */
const val CUSTOM_TARGET_MIN = 1
const val CUSTOM_TARGET_MAX = 99

fun generateGoalId(type: GoalType, existingIds: Collection<String>): String {
    var id = "${type.id}_${System.currentTimeMillis()}"
    var suffix = 1
    while (existingIds.contains(id)) {
        id = "${type.id}_${System.currentTimeMillis()}_${suffix++}"
    }
    return id
}

fun italianWeekStart(): String =
    java.time.LocalDate.now()
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
        .toString()

fun italianWeeklyReset(entry: GoalEntry, anchor: String): GoalEntry {
    if (entry.type != GoalType.ITALIAN_LESSONS || entry.period != GoalPeriod.WEEK) return entry
    return when {
        entry.lastResetWeekStart.isEmpty() -> entry.copy(lastResetWeekStart = anchor)
        entry.lastResetWeekStart != anchor -> entry.copy(progress = 0.0, lastResetWeekStart = anchor)
        else -> entry
    }
}
