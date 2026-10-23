package com.daytoday.usecase

import com.daytoday.util.CalorieCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class StepMetricsTest {
    @Test
    fun strideMale175() {
        val stride = StepMetrics.strideMeters(175.0, true)
        assertEquals(0.72625, stride, 1e-6)
        val dist = StepMetrics.distanceKm(10000L, 175.0, true)
        assertEquals(7.2625, dist, 1e-6)
    }

    @Test
    fun strideFemale165() {
        val stride = StepMetrics.strideMeters(165.0, false)
        assertEquals(0.68145, stride, 1e-6)
    }

    @Test
    fun invalidHeight() {
        assertEquals(0.0, StepMetrics.strideMeters(0.0, true), 1e-6)
        assertEquals(0.0, StepMetrics.distanceKm(1000L, 0.0, true), 1e-6)
        assertEquals(0.0, StepMetrics.distanceKm(-5L, 175.0, true), 1e-6)
    }

    @Test
    fun calories() {
        val cals = StepMetrics.calories(10000L, 70.0)
        assertEquals(CalorieCalculator.stepsCalories(10000L, 70.0).roundToInt(), cals)
        assertEquals(400, cals)
        assertEquals(0, StepMetrics.calories(10000L, 0.0))
        assertEquals(0, StepMetrics.calories(0L, 70.0))
    }
}
