package com.daytoday.ui.goals

import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalPeriod
import com.daytoday.settings.GoalType
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.newSettingsManager
import com.daytoday.ui.screen.goals.GoalProgress
import com.daytoday.ui.screen.goals.GoalsViewModel
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import io.mockk.coEvery
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = newSettingsManager()
    private val stats: LocalSummaryUseCases = mockk()

    private fun vm(): GoalsViewModel {
        coEvery { stats.todayStats() } returns LocalDayStats(
            steps = 4_321, totalCalories = 321, durationMinutes = 25,
        )
        coEvery { stats.weekStats() } returns LocalWeekStats(
            workouts = 3, durationMinutes = 120, caloriesBurned = 900, volume = 5_000.0,
        )
        return GoalsViewModel(settings, stats)
    }

    private fun GoalsViewModel.goals(): List<GoalProgress> =
        (uiState.value as UiState.Success).data.goals

    @Test
    fun `fresh install shows the two default goals with live stats`() = runTest {
        val vm = vm()
        val goals = vm.goals()
        assertEquals(
            listOf(GoalType.CALORIES_TODAY, GoalType.WORKOUTS_PER_WEEK),
            goals.map { it.entry.type },
        )
        assertEquals(321.0, goals[0].current, 0.0)
        assertEquals(3.0, goals[1].current, 0.0)
    }

    @Test
    fun `stats failure produces an error state`() = runTest {
        coEvery { stats.todayStats() } throws IllegalStateException("no data")
        val vm = GoalsViewModel(settings, stats)
        assertEquals("no data", (vm.uiState.value as UiState.Error).message)
    }

    @Test
    fun `addGoal appends a typed goal and reads its current value from stats`() = runTest {
        val vm = vm()
        vm.addGoal(GoalType.STEPS_TODAY, 9_000.0)
        val goals = vm.goals()
        assertEquals(3, goals.size)
        val steps = goals.last()
        assertEquals(GoalType.STEPS_TODAY, steps.entry.type)
        assertEquals(9_000.0, steps.entry.target, 0.0)
        assertEquals(4_321.0, steps.current, 0.0)
    }

    @Test
    fun `addGoal ignores the custom type`() = runTest {
        val vm = vm()
        vm.addGoal(GoalType.CUSTOM, 5.0)
        assertEquals(2, vm.goals().size)
    }

    @Test
    fun `custom goal is trimmed and clamped`() = runTest {
        val vm = vm()
        vm.addCustomGoal("  Read a book  ", 500.0, GoalPeriod.WEEK)
        val entry = vm.goals().last().entry
        assertEquals("Read a book", entry.label)
        assertEquals(99.0, entry.target, 0.0)
        assertTrue(entry.lastResetWeekStart.isNotEmpty())
    }

    @Test
    fun `daily custom goal does not record a week anchor`() = runTest {
        val vm = vm()
        vm.addCustomGoal("Stretch", 1.0, GoalPeriod.DAY)
        assertEquals("", vm.goals().last().entry.lastResetWeekStart)
    }

    @Test
    fun `blank or over-long custom labels are rejected`() = runTest {
        val vm = vm()
        vm.addCustomGoal("   ", 3.0, GoalPeriod.WEEK)
        vm.addCustomGoal("x".repeat(81), 3.0, GoalPeriod.WEEK)
        assertEquals(2, vm.goals().size)
    }

    @Test
    fun `custom progress increments, never below zero, and caps at twice the target`() = runTest {
        val vm = vm()
        vm.addCustomGoal("Water", 2.0, GoalPeriod.DAY)
        val id = vm.goals().last().entry.id

        vm.decrementCustomProgress(id)
        assertEquals(0.0, vm.goals().last().current, 0.0)

        repeat(10) { vm.incrementCustomProgress(id) }
        assertEquals(4.0, vm.goals().last().current, 0.0)
    }

    @Test
    fun `updateGoalTarget changes only the targeted goal`() = runTest {
        val vm = vm()
        vm.updateGoalTarget(GoalType.WORKOUTS_PER_WEEK.id, 5.0)
        val goals = vm.goals()
        assertEquals(500.0, goals[0].entry.target, 0.0)
        assertEquals(5.0, goals[1].entry.target, 0.0)
    }

    @Test
    fun `removeGoal drops the goal`() = runTest {
        val vm = vm()
        vm.removeGoal(GoalType.CALORIES_TODAY.id)
        assertEquals(listOf(GoalType.WORKOUTS_PER_WEEK), vm.goals().map { it.entry.type })
    }

    @Test
    fun `weekly custom counter resets when the stored week is stale`() = runTest {
        settings.setGoals(
            listOf(
                GoalEntry(
                    id = "custom_1", type = GoalType.CUSTOM, target = 3.0, label = "Run",
                    period = GoalPeriod.WEEK, progress = 2.0, lastResetWeekStart = "2000-01-03",
                ),
            ),
        )
        val vm = vm()
        val goal = vm.goals().single()
        assertEquals(0.0, goal.current, 0.0)
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        assertEquals(monday, settings.goals.first().single().lastResetWeekStart)
    }

    @Test
    fun `legacy weekly goal without an anchor keeps its progress`() = runTest {
        settings.setGoals(
            listOf(
                GoalEntry(
                    id = "custom_1", type = GoalType.CUSTOM, target = 3.0, label = "Run",
                    period = GoalPeriod.WEEK, progress = 2.0, lastResetWeekStart = "",
                ),
            ),
        )
        val vm = vm()
        assertEquals(2.0, vm.goals().single().current, 0.0)
    }

    @Test
    fun `updateCustomGoal rewrites label target and period`() = runTest {
        val vm = vm()
        vm.addCustomGoal("Read", 2.0, GoalPeriod.DAY)
        val id = vm.goals().last().entry.id
        vm.updateCustomGoal(id, " Read more ", 4.0, GoalPeriod.WEEK)
        val entry = vm.goals().last().entry
        assertEquals("Read more", entry.label)
        assertEquals(4.0, entry.target, 0.0)
        assertEquals(GoalPeriod.WEEK, entry.period)
    }
}
