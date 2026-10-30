package com.daytoday.data.notification

import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalWeekStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round-trip and date-scoping tests for [SummaryStatsCache] using an in-memory store. */
class SummaryStatsCacheTest {

    private val store = InMemorySummaryStatsStore()
    private val cache = SummaryStatsCache(store)

    private val day = LocalDate.of(2026, 10, 20)
    private val stats = LocalDayStats(
        steps = 7_639,
        stepsCalories = 214,
        workoutCalories = 120,
        totalCalories = 334,
        completedSessions = 1,
        durationMinutes = 42,
        totalVolume = 2_500.0,
    )

    private val week = LocalWeekStats(
        weekStart = 1_000L,
        workouts = 3,
        durationMinutes = 90,
        caloriesBurned = 1_200,
        volume = 9_500.0,
        steps = 30_000,
        stepCalories = 840,
        workoutCalories = 360,
    )

    @Test
    fun `day snapshot round-trips for the same date`() {
        cache.saveDay(stats, date = day, capturedAtMs = 1_234L)

        val cached = cache.dayFor(day)

        assertEquals(stats, cached?.stats)
        assertEquals(1_234L, cached?.capturedAtMs)
        assertEquals(day, cached?.date)
    }

    @Test
    fun `day snapshot is a miss for any other date`() {
        cache.saveDay(stats, date = day, capturedAtMs = 1_234L)

        assertNull(cache.dayFor(day.minusDays(1)))
        assertNull(cache.dayFor(day.plusDays(1)))
    }

    @Test
    fun `week snapshot matches its Monday and misses another week`() {
        cache.saveWeek(week, weekStart = day, capturedAtMs = 9_999L)

        val cached = cache.weekFor(day)

        assertEquals(week, cached?.stats)
        assertEquals(day, cached?.weekStart)
        assertEquals(9_999L, cached?.capturedAtMs)
        assertNull(cache.weekFor(day.minusWeeks(1)))
        assertNull(cache.weekFor(day.plusWeeks(1)))
    }

    @Test
    fun `day and week snapshots do not overwrite each other`() {
        cache.saveDay(stats, date = day, capturedAtMs = 1_234L)
        cache.saveWeek(week, weekStart = day, capturedAtMs = 9_999L)

        assertEquals(stats, cache.dayFor(day)?.stats)
        assertEquals(week, cache.weekFor(day)?.stats)
    }

    @Test
    fun `unreadable payload is treated as a cache miss`() {
        store.write("day", "{not json")
        store.write("week", "")

        assertNull(cache.dayFor(day))
        assertNull(cache.weekFor(day))
    }

    private class InMemorySummaryStatsStore : SummaryStatsStore {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]

        override fun write(key: String, value: String) {
            values[key] = value
        }
    }
}
