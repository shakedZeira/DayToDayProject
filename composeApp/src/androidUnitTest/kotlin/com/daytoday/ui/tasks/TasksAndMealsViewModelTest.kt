package com.daytoday.ui.tasks

import com.daytoday.data.sync.MealsRepository
import com.daytoday.data.sync.OutboxProcessor
import com.daytoday.data.sync.SendOutcome
import com.daytoday.data.sync.SyncCoordinator
import com.daytoday.data.sync.TasksRepository
import com.daytoday.data.sync.WeeklyGoalsRepository
import com.daytoday.network.DayTodayApi
import com.daytoday.network.TaskDto
import com.daytoday.testing.CountingTrigger
import com.daytoday.testing.FakeMealLogDao
import com.daytoday.testing.FakeOutboxDao
import com.daytoday.testing.FakeSyncNoteDao
import com.daytoday.testing.FakeTaskDao
import com.daytoday.testing.FakeWeeklyGoalDao
import com.daytoday.testing.MainDispatcherRule
import com.daytoday.testing.ScriptedSender
import com.daytoday.ui.screen.tasks.MealsViewModel
import com.daytoday.ui.screen.tasks.TasksViewModel
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TasksAndMealsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val outbox = FakeOutboxDao()
    private val notes = FakeSyncNoteDao()
    private val api: DayTodayApi = mockk()
    private val trigger = CountingTrigger()
    private val sender = ScriptedSender()
    private val today = LocalDate.now().toString()
    private val tasks = TasksRepository(FakeTaskDao(), outbox, api, { "tok" }, trigger)
    private val goals = WeeklyGoalsRepository(FakeWeeklyGoalDao(), outbox, api, { "tok" }, trigger)
    private val meals = MealsRepository(FakeMealLogDao(), outbox, api, { "tok" }, trigger)
    private val processor = OutboxProcessor(outbox, notes, sender, { "tok" }, listOf(tasks, goals, meals))
    private val coordinator = SyncCoordinator(processor, outbox, notes, tasks, goals, meals)

    private fun offline() {
        sender.default = SendOutcome.Network(IOException("offline"))
        coEvery { api.getTasks(any()) } throws IOException("offline")
        coEvery { api.getWeeklyGoals(any()) } throws IOException("offline")
        coEvery { api.getMeals(any(), any()) } throws IOException("offline")
    }

    @Test
    fun `tasks added offline show up immediately with a pending count`() = runTest {
        offline()
        val vm = TasksViewModel(tasks, coordinator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.add("Water plants")

        val state = vm.uiState.value
        assertEquals(listOf("Water plants"), state.tasks.map { it.title })
        assertTrue(state.tasks.single().unsynced)
        assertEquals(1, state.pendingChanges)
        assertFalse(state.authPaused)
    }

    @Test
    fun `toggling and deleting go through the repository`() = runTest {
        offline()
        val vm = TasksViewModel(tasks, coordinator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.add("A")
        val task = vm.uiState.value.tasks.single()

        vm.setDone(task, true)
        assertTrue(vm.uiState.value.tasks.single().done)

        vm.delete(task)
        assertTrue(vm.uiState.value.tasks.isEmpty())
        assertEquals(0, vm.uiState.value.pendingChanges)
    }

    @Test
    fun `starting up syncs and pulls the server list`() = runTest {
        coEvery { api.getTasks(any()) } returns listOf(TaskDto("s1", "From server"))
        coEvery { api.getWeeklyGoals(any()) } returns emptyList()
        coEvery { api.getMeals(any(), any()) } returns com.daytoday.network.MealsDetailDto(today)
        val vm = TasksViewModel(tasks, coordinator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("From server"), vm.uiState.value.tasks.map { it.title })
    }

    @Test
    fun `rejected changes are shown as dismissable notices`() = runTest {
        coEvery { api.getTasks(any()) } returns emptyList()
        coEvery { api.getWeeklyGoals(any()) } returns emptyList()
        coEvery { api.getMeals(any(), any()) } returns com.daytoday.network.MealsDetailDto(today)
        sender.default = SendOutcome.Http(400, "title required")
        val vm = TasksViewModel(tasks, coordinator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.add("x")
        vm.sync()

        val note = vm.uiState.value.notices.single()
        assertTrue(note.message.contains("400"))
        vm.dismissNotice(note.id)
        assertTrue(vm.uiState.value.notices.isEmpty())
    }

    @Test
    fun `expired session is flagged while changes are kept`() = runTest {
        sender.default = SendOutcome.Http(401, "expired")
        coEvery { api.getTasks(any()) } returns emptyList()
        val vm = TasksViewModel(tasks, coordinator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.add("keep me")
        vm.sync()
        assertTrue(vm.uiState.value.authPaused)
        assertEquals(1, vm.uiState.value.pendingChanges)
        assertEquals(listOf("keep me"), vm.uiState.value.tasks.map { it.title })
    }

    @Test
    fun `meals total the calories of today's entries`() = runTest {
        coEvery { api.getMeals(any(), any()) } throws IOException("offline")
        val vm = MealsViewModel(meals)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.log("f1", "Rice", 150.0, 195)
        vm.log("f2", "Egg", 50.0, 78)

        val state = vm.uiState.value
        assertEquals(2, state.meals.size)
        assertEquals(273, state.totalCalories)

        vm.delete(state.meals.first())
        assertEquals(78, vm.uiState.value.totalCalories)
    }
}
