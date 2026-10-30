package com.daytoday.usecase

import android.content.Context
import com.daytoday.data.health.DailySteps
import com.daytoday.data.health.HealthRepository
import com.daytoday.model.SetRecord
import com.daytoday.model.UserProfile
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import com.daytoday.settings.SettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that the summary use cases keep workout data when Health Connect is
 * slow or broken, propagate cancellation, and never read sessions from the network.
 */
class LocalSummaryUseCasesTest {

    private val context: Context = mockk(relaxed = true)
    private val healthRepository: HealthRepository = mockk()
    private val workoutRepository: WorkoutRepository = mockk()
    private val settingsManager: SettingsManager = mockk()

    private lateinit var useCases: LocalSummaryUseCases

    @Before
    fun setUp() {
        useCases = LocalSummaryUseCases(
            healthRepository = healthRepository,
            workoutRepository = workoutRepository,
            settingsManager = settingsManager,
            context = context,
        )
        every { settingsManager.userProfile } returns flowOf(UserProfile())
    }

    private fun session(
        id: String,
        startTime: Long,
        completed: Boolean = true,
    ) = WorkoutSession(
        id = id,
        exerciseId = "ex_push_up",
        exerciseName = "Push-Up",
        startTime = startTime,
        endTime = startTime + 30 * 60_000,
        durationMinutes = 30,
        caloriesBurned = 200,
        sets = listOf(
            SetRecord(
                id = "set-$id",
                sessionId = id,
                setNumber = 1,
                weight = 0.0,
                reps = 10,
                rpe = null,
                isCompleted = true,
                completedAt = startTime,
            ),
        ),
        isCompleted = completed,
    )

    @Test
    fun `failing step read still reports today's workouts from the local cache`() {
        val now = System.currentTimeMillis()
        coEvery { workoutRepository.getCachedSessions(any(), any()) } returns
            Result.success(listOf(session("a", now)))
        coEvery { healthRepository.todaySteps(any()) } throws IOException("Health Connect unavailable")

        val stats = runBlocking { useCases.todayStats() }

        assertEquals(1, stats.completedSessions)
        assertEquals(30, stats.durationMinutes)
        assertEquals(5, stats.workoutCalories)
        assertEquals(0L, stats.steps)
        coVerify(exactly = 1) { workoutRepository.getCachedSessions(any(), any()) }
        coVerify(exactly = 0) { workoutRepository.getSessions(any(), any()) }
    }

    @Test
    fun `timed out step read degrades only the step fields`() {
        val now = System.currentTimeMillis()
        coEvery { workoutRepository.getCachedSessions(any(), any()) } returns
            Result.success(listOf(session("a", now)))
        coEvery { healthRepository.todaySteps(any()) } coAnswers {
            delay(10_000)
            DailySteps(date = LocalDate.now(), steps = 8_000, cachedAtMs = now)
        }

        val startedAt = System.currentTimeMillis()
        val stats = runBlocking { useCases.todayStats() }
        val elapsed = System.currentTimeMillis() - startedAt

        assertEquals(1, stats.completedSessions)
        assertEquals(0L, stats.steps)
        assertTrue("step read should have been bounded, took ${elapsed}ms", elapsed < 5_000)
    }

    @Test
    fun `cancellation from the step read is rethrown`() {
        val now = System.currentTimeMillis()
        coEvery { workoutRepository.getCachedSessions(any(), any()) } returns
            Result.success(listOf(session("a", now)))
        coEvery { healthRepository.todaySteps(any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { useCases.todayStats() }
        }
    }

    @Test
    fun `failing weekly step read keeps the weekly workout totals`() {
        val now = System.currentTimeMillis()
        coEvery { workoutRepository.getCachedSessions(any(), any()) } returns
            Result.success(listOf(session("a", now)))
        coEvery { healthRepository.totalSteps(any(), any(), any()) } throws IOException("boom")

        val stats = runBlocking { useCases.weekStats() }

        assertEquals(1, stats.workouts)
        assertEquals(30, stats.durationMinutes)
        assertEquals(0L, stats.steps)
        assertEquals(200, stats.caloriesBurned)
        coVerify(exactly = 1) { workoutRepository.getCachedSessions(any(), any()) }
        coVerify(exactly = 0) { workoutRepository.getSessions(any(), any()) }
    }

    @Test
    fun `empty local cache still returns zeroed workout stats without a network call`() {
        coEvery { workoutRepository.getCachedSessions(any(), any()) } returns Result.success(emptyList())
        coEvery { healthRepository.todaySteps(any()) } returns
            DailySteps(date = LocalDate.now(), steps = 0, cachedAtMs = 0L)

        val stats = runBlocking { useCases.todayStats() }

        assertEquals(0, stats.completedSessions)
        assertEquals(0L, stats.steps)
        coVerify(exactly = 0) { workoutRepository.getSessions(any(), any()) }
    }
}
