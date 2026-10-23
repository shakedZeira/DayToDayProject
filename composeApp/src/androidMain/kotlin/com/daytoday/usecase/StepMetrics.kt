package com.daytoday.usecase

import com.daytoday.util.CalorieCalculator
import kotlin.math.roundToInt

object StepMetrics {
    private const val MALE_STRIDE_RATIO = 0.415
    private const val FEMALE_STRIDE_RATIO = 0.413

    fun strideMeters(heightCm: Double, isMale: Boolean): Double {
        if (heightCm <= 0.0 || !heightCm.isFinite()) return 0.0
        val ratio = if (isMale) MALE_STRIDE_RATIO else FEMALE_STRIDE_RATIO
        return heightCm * ratio / 100.0
    }

    fun distanceKm(steps: Long, heightCm: Double, isMale: Boolean): Double {
        if (steps <= 0L) return 0.0
        return steps * strideMeters(heightCm, isMale) / 1000.0
    }

    fun calories(steps: Long, weightKg: Double): Int {
        if (steps <= 0L || weightKg <= 0.0) return 0
        return CalorieCalculator.stepsCalories(steps, weightKg).roundToInt()
    }
}
