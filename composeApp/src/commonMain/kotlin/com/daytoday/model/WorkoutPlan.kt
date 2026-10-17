package com.daytoday.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val exercises: List<PlanExercise>,
    val createdAt: Long = 0L,
    val warmupSeconds: Int = 0,
    val amrapSeconds: Int = 0
)

@Serializable
data class PlanExercise(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String = "",
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetWeightKg: Double = 0.0
)
