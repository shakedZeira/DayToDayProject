package com.daytoday.util

import com.daytoday.model.WorkoutSession
import kotlin.math.roundToInt

/**
 * Offline calorie math. Everything the dashboard shows for steps, workouts and the
 * daily burn goal is derived here, so no network round trip is required.
 */
object CalorieCalculator {

    const val DEFAULT_WEIGHT_KG: Double = 70.0
    const val STEPS_CALORIE_FACTOR: Double = 0.04

    private const val REFERENCE_WEIGHT_KG: Double = DEFAULT_WEIGHT_KG
    private const val ACTIVITY_BURN_FACTOR: Double = 0.20
    private const val PULL_UP_KCAL_PER_REP_REF: Double = 0.60
    private const val PUSH_UP_KCAL_PER_REP_REF: Double = 0.45
    private const val AIR_SQUAT_KCAL_PER_REP_REF: Double = 0.30
    private const val DEFAULT_RESISTANCE_KCAL_PER_REP_REF: Double = 0.30

    fun stepsCalories(steps: Long, weightKg: Double): Double {
        if (weightKg <= 0) return 0.0
        return steps * STEPS_CALORIE_FACTOR * (weightKg / REFERENCE_WEIGHT_KG)
    }

    fun bmrKcal(weightKg: Double, heightCm: Double, ageYears: Int, isMale: Boolean): Double {
        if (weightKg <= 0 || heightCm <= 0 || ageYears <= 0) return 0.0
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return base + if (isMale) 5.0 else -161.0
    }

    fun dailyBurnGoalKcal(weightKg: Double, heightCm: Double, ageYears: Int, isMale: Boolean): Int =
        (bmrKcal(weightKg, heightCm, ageYears, isMale) * ACTIVITY_BURN_FACTOR).roundToInt()

    fun workoutCalories(weightKg: Double, sessions: List<WorkoutSession>): Double {
        if (weightKg <= 0) return 0.0
        var kcal = 0.0
        var completedSets = 0
        sessions.forEach { session ->
            val kcalPerRep = kcalPerRepFor(session.exerciseName)
            session.sets.forEach { set ->
                if (!set.isCompleted) return@forEach
                completedSets++
                kcal += kcalPerRep * set.reps
            }
        }
        if (completedSets == 0) return 0.0
        return kcal * (weightKg / REFERENCE_WEIGHT_KG)
    }

    fun progressFraction(burnedKcal: Double, targetKcal: Int): Float {
        if (targetKcal <= 0) return 0f
        val fraction = burnedKcal / targetKcal
        if (fraction.isNaN() || fraction <= 0.0) return 0f
        return if (fraction >= 1.0) 1f else fraction.toFloat()
    }

    // The reference used a closed Exercise enum; here the name is free text, so the
    // kcal-per-rep coefficient is resolved by a case-insensitive substring match instead.
    private fun kcalPerRepFor(exerciseName: String): Double {
        val name = exerciseName.lowercase()
        return when {
            name.contains("pull") -> PULL_UP_KCAL_PER_REP_REF
            name.contains("push") -> PUSH_UP_KCAL_PER_REP_REF
            name.contains("squat") -> AIR_SQUAT_KCAL_PER_REP_REF
            else -> DEFAULT_RESISTANCE_KCAL_PER_REP_REF
        }
    }
}