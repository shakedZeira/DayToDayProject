package com.daytoday.data.health

import android.content.Context
import com.daytoday.repository.Result
import java.time.LocalDate

interface HealthRepository {
    suspend fun isSdkAvailable(context: Context): HealthSdkStatus

    suspend fun getHealthPermissionState(context: Context): Result<HealthPermissionState>

    suspend fun requestStepsPermission(context: Context): Boolean

    suspend fun todaySteps(context: Context): DailySteps

    suspend fun stepsInRange(context: Context, from: LocalDate, to: LocalDate): Map<LocalDate, Long>
}

enum class HealthSdkStatus {
    AVAILABLE,
    MISSING,
    UNAVAILABLE,
}

enum class HealthPermissionState {
    GRANTED,
    NOT_GRANTED,
}

/**
 * Step count for a single local day, as reported by Health Connect.
 *
 * Calories are deliberately NOT read from Health Connect - they are derived locally
 * by [com.daytoday.util.CalorieCalculator] from [steps] plus logged workout sets.
 */
data class DailySteps(
    val date: LocalDate,
    val steps: Long,
    val cachedAtMs: Long,
)

/** Locally derived calorie split for a day. */
data class CalorieBreakdown(
    val stepsCalories: Double,
    val workoutCalories: Double,
    val totalCalories: Double,
)
