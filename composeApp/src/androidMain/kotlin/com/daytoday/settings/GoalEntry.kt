package com.daytoday.settings

import kotlinx.serialization.Serializable

enum class StatsSource {
    TODAY,
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
    );
}

@Serializable
data class GoalEntry(
    val id: String,
    val type: GoalType,
    val target: Double,
)

fun generateGoalId(type: GoalType, existingIds: Collection<String>): String {
    var id = "${type.id}_${System.currentTimeMillis()}"
    var suffix = 1
    while (existingIds.contains(id)) {
        id = "${type.id}_${System.currentTimeMillis()}_${suffix++}"
    }
    return id
}
