package com.daytoday.usecase

import android.content.Context
import com.daytoday.data.health.HealthPermissionState
import com.daytoday.data.health.HealthRepository
import com.daytoday.model.UserProfile
import com.daytoday.repository.Result
import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StepsHistoryUseCasesTest {
    private val context: Context = mockk(relaxed = true)
    private val healthRepository: HealthRepository = mockk()
    private val settingsManager: SettingsManager = mockk()

    private val today: LocalDate = LocalDate.now(ZoneId.systemDefault())

    private lateinit var useCases: StepsHistoryUseCases

    @Before
    fun setUp() {
        useCases = StepsHistoryUseCases(healthRepository, settingsManager, context)
        coEvery { healthRepository.getHealthPermissionState(any()) } returns
            Result.success(HealthPermissionState.GRANTED)
        every { settingsManager.goals } returns flowOf(listOf(GoalEntry("g", GoalType.STEPS_TODAY, 8_000.0)))
        every { settingsManager.userProfile } returns flowOf(UserProfile())
        coEvery { healthRepository.totalSteps(any(), any(), any()) } returns 0L
        useCases.cacheTtlMs = 0L
    }

    @Test
    fun permissionDeniedReturnsPermissionMissing() = runBlocking {
        coEvery { healthRepository.getHealthPermissionState(any()) } returns
            Result.success(HealthPermissionState.NOT_GRANTED)
        val outcome = useCases.load(StepsPeriod.DAY)
        assertTrue(outcome is StepsLoadOutcome.PermissionMissing)
        coVerify(exactly = 0) { healthRepository.stepsInRange(any(), any(), any()) }
    }

    @Test
    fun daySuccess() = runBlocking {
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 8_432L)
        val outcome = useCases.load(StepsPeriod.DAY)
        assertTrue(outcome is StepsLoadOutcome.Success)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertEquals(8_432L, history.todaySteps)
        assertEquals(8_000L, history.paceTarget)
        assertEquals(1, history.buckets.size)
    }

    @Test
    fun weekSuccess() = runBlocking {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val map = mutableMapOf<LocalDate, Long>()
        var d = monday
        while (!d.isAfter(today)) {
            map[d] = 5_000L
            d = d.plusDays(1)
        }
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } answers {
            map.filterKeys { !it.isBefore(secondArg()) && !it.isAfter(thirdArg()) }
        }
        val outcome = useCases.load(StepsPeriod.WEEK)
        assertTrue(outcome.toString(), outcome is StepsLoadOutcome.Success)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertEquals(7, history.days.size)
        assertEquals(7 - (today.dayOfWeek.value - 1) - 1, history.days.count { it.isFuture })
    }

    @Test
    fun weekEmptyMapIsFailure() = runBlocking {
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns emptyMap()
        val outcome = useCases.load(StepsPeriod.WEEK)
        assertTrue(outcome is StepsLoadOutcome.Failure)
    }

    @Test
    fun goalFallback() = runBlocking {
        every { settingsManager.goals } returns flowOf(emptyList())
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 1_000L)
        val outcome = useCases.load(StepsPeriod.DAY)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertEquals(8_000L, history.goal)
    }

    @Test
    fun customGoal() = runBlocking {
        every { settingsManager.goals } returns flowOf(listOf(GoalEntry("g", GoalType.STEPS_TODAY, 12_000.0)))
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 1_000L)
        val outcome = useCases.load(StepsPeriod.DAY)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertEquals(12_000L, history.goal)
        assertEquals(12_000L * history.stats.elapsedDays, history.paceTarget)
    }

    @Test
    fun yearBuckets() = runBlocking {
        val year = today.year
        coEvery { healthRepository.totalSteps(any(), any(), any()) } answers {
            val from = secondArg<LocalDate>()
            if (from.year == year) from.monthValue * 1_000L else 0L
        }
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 1_234L)
        val outcome = useCases.load(StepsPeriod.YEAR)
        assertTrue(outcome.toString(), outcome is StepsLoadOutcome.Success)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertEquals(12, history.buckets.size)
        assertEquals(null, history.stats.daysGoalMet)
        assertEquals(1_234L, history.todaySteps)
        val current = history.buckets[today.monthValue - 1]
        assertEquals(8_000L * today.dayOfMonth, current.goal)
        assertTrue(current.isCurrent)
    }

    @Test
    fun cacheServesRepeatedLoads() = runBlocking {
        useCases.cacheTtlMs = 60_000L
        var clock = 1_000L
        useCases.nowMs = { clock }
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 2_000L)

        useCases.load(StepsPeriod.DAY)
        useCases.load(StepsPeriod.DAY)
        coVerify(exactly = 1) { healthRepository.stepsInRange(any(), any(), any()) }

        useCases.load(StepsPeriod.DAY, forceRefresh = true)
        coVerify(exactly = 2) { healthRepository.stepsInRange(any(), any(), any()) }

        clock += 61_000L
        useCases.load(StepsPeriod.DAY)
        coVerify(exactly = 3) { healthRepository.stepsInRange(any(), any(), any()) }
    }

    @Test
    fun timeoutReturnsFailure() = runBlocking {
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } coAnswers {
            delay(10_000L)
            mapOf(today to 1_000L)
        }
        val started = System.currentTimeMillis()
        val outcome = useCases.load(StepsPeriod.DAY)
        val elapsed = System.currentTimeMillis() - started
        assertTrue(outcome is StepsLoadOutcome.Failure)
        assertTrue(elapsed < 9_000L)
    }

    @Test
    fun cancellationIsRethrown() {
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            runBlocking { useCases.load(StepsPeriod.DAY) }
        }
    }

    @Test
    fun zeroPreviousTotalHasNoPercent() = runBlocking {
        coEvery { healthRepository.stepsInRange(any(), any(), any()) } returns mapOf(today to 4_000L)
        coEvery { healthRepository.totalSteps(any(), any(), any()) } returns 0L
        val outcome = useCases.load(StepsPeriod.DAY)
        val history = (outcome as StepsLoadOutcome.Success).history
        assertNull(history.changePercent)
    }
}
