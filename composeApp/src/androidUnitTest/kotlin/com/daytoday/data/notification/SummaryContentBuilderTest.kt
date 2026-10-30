package com.daytoday.data.notification

import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.StatsSource
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Content-formatting and cache-fallback tests; the line builders are pure JVM functions. */
class SummaryContentBuilderTest {

    private val originalLocale: Locale = Locale.getDefault()
    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    private fun goal(type: GoalType, target: Double = type.defaultValue) = GoalEntry(
        id = "${type.id}-${target}",
        type = type,
        target = target,
    )

    private fun millis(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, 20, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `populated day renders workout calories and grouped steps`() {
        val day = LocalDayStats(
            steps = 7_639,
            stepsCalories = 214,
            workoutCalories = 120,
            totalCalories = 334,
            completedSessions = 1,
            durationMinutes = 42,
            totalVolume = 2_500.0,
        )

        val lines = SummaryContentBuilder.dailyLines(day, LocalWeekStats(), emptyList())

        assertEquals("1 workout · 42 min · 2,500 kg volume", lines[0])
        assertEquals("Calories burned: 334 kcal (steps 214 + workout 120)", lines[1])
        assertEquals("Steps: 7,639", lines[2])
    }

    @Test
    fun `zero day renders the empty state without inventing workouts`() {
        val lines = SummaryContentBuilder.dailyLines(LocalDayStats(), LocalWeekStats(), emptyList())

        assertEquals("No workouts logged today", lines[0])
        assertEquals("Calories burned: 0 kcal (steps 0 + workout 0)", lines[1])
        assertEquals("Steps: 0", lines[2])
    }

    @Test
    fun `a readable source reporting zero steps is a real zero`() {
        val view = SummaryContentBuilder.resolveDay(LocalDayStats(), cached = null, cacheEnabled = true)

        assertTrue(view.stepsKnown)
    }

    @Test
    fun `unknown steps render as an em dash rather than zero`() {
        val fresh = LocalDayStats(workoutCalories = 120, stepsReadable = false)

        val view = SummaryContentBuilder.resolveDay(fresh, cached = null, cacheEnabled = true)

        assertTrue(!view.stepsKnown)
        assertEquals(
            "Steps: —",
            SummaryContentBuilder.dailyLines(
                view.stats,
                LocalWeekStats(),
                emptyList(),
                view.stepsAsOf,
                view.stepsKnown,
            )[2],
        )
    }

    @Test
    fun `zero fresh steps fall back to the same-day cache and annotate the line`() {
        val cachedStats = LocalDayStats(steps = 7_639, stepsCalories = 214)
        val fresh = LocalDayStats(workoutCalories = 120)
        val cached = CachedDayStats(
            date = LocalDate.of(2026, 10, 20),
            capturedAtMs = millis(15, 42),
            stats = cachedStats,
        )

        val view = SummaryContentBuilder.resolveDay(fresh, cached, cacheEnabled = true)

        assertTrue(view.stepsKnown)
        assertEquals(7_639L, view.stats.steps)
        assertEquals(334, view.stats.totalCalories)
        assertEquals(LocalTime.of(15, 42), view.stepsAsOf)
        assertEquals(
            "Steps: 7,639 (as of 15:42)",
            SummaryContentBuilder.dailyLines(
                view.stats,
                LocalWeekStats(),
                emptyList(),
                view.stepsAsOf,
                view.stepsKnown,
            )[2],
        )
    }

    @Test
    fun `fresh non-zero steps are authoritative over the cache`() {
        val fresh = LocalDayStats(
            steps = 9_000,
            stepsCalories = 250,
            workoutCalories = 100,
            totalCalories = 350,
        )
        val cached = CachedDayStats(
            date = LocalDate.of(2026, 10, 20),
            capturedAtMs = millis(15, 42),
            stats = LocalDayStats(steps = 7_639, stepsCalories = 214),
        )

        val view = SummaryContentBuilder.resolveDay(fresh, cached, cacheEnabled = true)

        assertEquals(9_000L, view.stats.steps)
        assertEquals(350, view.stats.totalCalories)
        assertNull(view.stepsAsOf)
    }

    @Test
    fun `weekly zero steps fall back to the cached step calories`() {
        val fresh = LocalWeekStats(workouts = 2, workoutCalories = 300)
        val cached = CachedWeekStats(
            weekStart = LocalDate.of(2026, 10, 19),
            capturedAtMs = millis(15, 42),
            stats = LocalWeekStats(steps = 30_000, stepCalories = 840),
        )

        val view = SummaryContentBuilder.resolveWeek(fresh, cached, cacheEnabled = true)

        assertEquals(30_000L, view.stats.steps)
        assertEquals(840, view.stats.stepCalories)
        assertEquals(1_140, view.stats.caloriesBurned)
        assertEquals(LocalTime.of(15, 42), view.stepsAsOf)
    }

    @Test
    fun `weekly lines only include WEEK-source goals and mark completed ones`() {
        val week = LocalWeekStats(workouts = 3, workoutCalories = 400)
        val goals = listOf(
            goal(GoalType.CALORIES_TODAY, 500.0),
            goal(GoalType.WORKOUTS_PER_WEEK, 2.0),
            goal(GoalType.STEPS_TODAY, 8_000.0),
        )

        val lines = SummaryContentBuilder.weeklyLines(LocalDayStats(), week, goals)

        assertEquals("Goals:", lines[2])
        assertEquals("• Workouts per week: 3/2 workouts ✓", lines[3])
        assertTrue(lines.none { it.contains("Calories today") || it.contains("Steps today") })
    }

    @Test
    fun `daily lines only include TODAY-source goals`() {
        val day = LocalDayStats(stepsCalories = 214, totalCalories = 334)
        val goals = listOf(
            goal(GoalType.CALORIES_TODAY, 500.0),
            goal(GoalType.WORKOUTS_PER_WEEK, 2.0),
        )

        val lines = SummaryContentBuilder.dailyLines(day, LocalWeekStats(), goals)

        assertEquals(StatsSource.TODAY, GoalType.CALORIES_TODAY.source)
        assertEquals("Goals:", lines[3])
        assertEquals("• Calories today: 334/500 kcal", lines[4])
        assertTrue(lines.none { it.contains("Workouts per week") })
    }

    @Test
    fun `more than five goals are truncated with a remainder line`() {
        val goals = (1..7).map { index ->
            GoalEntry(
                id = "steps-$index",
                type = GoalType.STEPS_TODAY,
                target = index * 1_000.0,
            )
        }

        val lines = SummaryContentBuilder.goalLines(
            goals,
            StatsSource.TODAY,
            LocalDayStats(steps = 2_500),
            LocalWeekStats(),
        )

        // "Goals:" + 5 rendered entries + "+2 more".
        assertEquals(7, lines.size)
        assertEquals("• +2 more", lines.last())
    }

    @Test
    fun `goal sources other than the requested one produce no goal block`() {
        val goals = listOf(goal(GoalType.WORKOUTS_PER_WEEK))

        val lines = SummaryContentBuilder.goalLines(
            goals,
            StatsSource.TODAY,
            LocalDayStats(),
            LocalWeekStats(),
        )

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `step counts use locale grouping`() {
        assertEquals("0", SummaryContentBuilder.formatCount(0))
        assertEquals("999", SummaryContentBuilder.formatCount(999))
        assertEquals("1,000", SummaryContentBuilder.formatCount(1_000))
        assertEquals("7,639", SummaryContentBuilder.formatCount(7_639))
        assertEquals("1,234,567", SummaryContentBuilder.formatCount(1_234_567))
    }

    @Test
    fun `week start is the preceding Monday`() {
        val tuesday = LocalDate.of(2026, 10, 20)

        assertEquals(LocalDate.of(2026, 10, 19), SummaryContentBuilder.weekStart(tuesday))
        assertEquals(LocalDate.of(2026, 10, 19), SummaryContentBuilder.weekStart(LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `daily build reads only today stats even when a week goal exists`() = runBlocking {
        val useCases = mockk<LocalSummaryUseCases>()
        coEvery { useCases.todayStats() } returns
            LocalDayStats(steps = 7_639, stepsCalories = 214, totalCalories = 214)
        val settings = mockk<SettingsManager>()
        every { settings.goals } returns flowOf(listOf(goal(GoalType.WORKOUTS_PER_WEEK, 2.0)))

        SummaryContentBuilder.build(
            kind = SummaryKind.DAILY,
            localSummaryUseCases = useCases,
            settingsManager = settings,
            nowMillis = millis(21, 0),
        )

        coVerify(exactly = 1) { useCases.todayStats() }
        coVerify(exactly = 0) { useCases.weekStats() }
    }

    @Test
    fun `weekly build reads only week stats`() = runBlocking {
        val useCases = mockk<LocalSummaryUseCases>()
        coEvery { useCases.weekStats() } returns LocalWeekStats(workouts = 2, workoutCalories = 300)
        val settings = mockk<SettingsManager>()
        every { settings.goals } returns flowOf(listOf(goal(GoalType.CALORIES_TODAY, 500.0)))

        SummaryContentBuilder.build(
            kind = SummaryKind.WEEKLY,
            localSummaryUseCases = useCases,
            settingsManager = settings,
            nowMillis = millis(20, 0),
        )

        coVerify(exactly = 1) { useCases.weekStats() }
        coVerify(exactly = 0) { useCases.todayStats() }
    }
}
