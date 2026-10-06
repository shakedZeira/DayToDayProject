package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class WorkoutSession(
    @SerialName("id") val id: String,
    @SerialName("exerciseId") val exerciseId: String,
    @SerialName("exerciseName") val exerciseName: String,
    @SerialName("startTime") val startTime: Long,
    @SerialName("endTime") val endTime: Long?,
    @SerialName("durationMinutes") val durationMinutes: Int,
    @SerialName("caloriesBurned") val caloriesBurned: Int,
    @SerialName("sets") val sets: List<SetRecord>,
    @SerialName("isCompleted") val isCompleted: Boolean
)

@Serializable
data class SetRecord(
    @SerialName("id") val id: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("setNumber") val setNumber: Int,
    @SerialName("weight") val weight: Double,
    @SerialName("reps") val reps: Int,
    @SerialName("rpe") val rpe: Double?,
    @SerialName("isCompleted") val isCompleted: Boolean,
    @SerialName("completedAt") val completedAt: Long?
)

@Serializable
data class Exercise(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("muscleGroup") val muscleGroup: String,
    @SerialName("equipment") val equipment: String,
    @SerialName("instructions") val instructions: String,
    @SerialName("demoVideoUrl") val demoVideoUrl: String?
)