package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class FitnessSummary(
    @SerialName("totalWorkouts") val totalWorkouts: Int,
    @SerialName("totalDurationMinutes") val totalDurationMinutes: Int,
    @SerialName("totalCaloriesBurned") val totalCaloriesBurned: Int,
    @SerialName("totalSets") val totalSets: Int,
    @SerialName("totalVolume") val totalVolume: Double,
    @SerialName("averageRpe") val averageRpe: Double,
    @SerialName("currentStreak") val currentStreak: Int,
    @SerialName("longestStreak") val longestStreak: Int,
    @SerialName("favoriteExercise") val favoriteExercise: String?,
    @SerialName("strongestExercise") val strongestExercise: String?,
    @SerialName("weeklyProgress") val weeklyProgress: List<WeeklyProgress>,
    @SerialName("monthlyProgress") val monthlyProgress: List<MonthlyProgress>
)

@Serializable
data class WeeklyProgress(
    @SerialName("weekStart") val weekStart: Long,
    @SerialName("workouts") val workouts: Int,
    @SerialName("durationMinutes") val durationMinutes: Int,
    @SerialName("caloriesBurned") val caloriesBurned: Int,
    @SerialName("volume") val volume: Double
)

@Serializable
data class MonthlyProgress(
    @SerialName("month") val month: Int,
    @SerialName("year") val year: Int,
    @SerialName("workouts") val workouts: Int,
    @SerialName("durationMinutes") val durationMinutes: Int,
    @SerialName("caloriesBurned") val caloriesBurned: Int,
    @SerialName("volume") val volume: Double
)