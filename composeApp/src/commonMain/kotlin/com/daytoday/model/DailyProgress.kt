package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DailyProgress(
    @SerialName("date") val date: Long,
    @SerialName("steps") val steps: Int,
    @SerialName("activeCalories") val activeCalories: Int,
    @SerialName("workoutsCompleted") val workoutsCompleted: Int,
    @SerialName("totalDurationMinutes") val totalDurationMinutes: Int,
    @SerialName("caloriesBurned") val caloriesBurned: Int,
    @SerialName("heartRateAvg") val heartRateAvg: Int?,
    @SerialName("personalBests") val personalBests: List<PersonalBests>
)

@Serializable
data class PersonalBests(
    @SerialName("exerciseId") val exerciseId: String,
    @SerialName("exerciseName") val exerciseName: String,
    @SerialName("bestWeight") val bestWeight: Double,
    @SerialName("bestReps") val bestReps: Int,
    @SerialName("bestVolume") val bestVolume: Double,
    @SerialName("bestOneRM") val bestOneRM: Double,
    @SerialName("achievedAt") val achievedAt: Long
)