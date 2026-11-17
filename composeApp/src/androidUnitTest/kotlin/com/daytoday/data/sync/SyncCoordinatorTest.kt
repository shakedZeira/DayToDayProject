package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.MealsDetailDto
import com.daytoday.network.TaskDto
import com.daytoday.testing.CountingTrigger
import com.daytoday.testing.FakeMealLogDao
import com.daytoday.testing.FakeOutboxDao
import com.daytoday.testing.FakeSyncNoteDao
import com.daytoday.testing.FakeTaskDao
import com.daytoday.testing.FakeWeeklyGoalDao
import com.daytoday.testing.ScriptedSender
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorTest {

    val outbox = FakeOutboxDao()
    val notes = FakeSyncNoteDao()
    val api: DayTodayApi = mockk()
    val trigger = CountingTrigger()
    val tasks = TasksRepository(FakeTaskDao(), outbox, api, { "tok" }, trigger)
    val goals = WeeklyGoalsRepository(FakeWeeklyGoalDao(), outbox, api, { "tok" }, trigger)
    val meals = MealsRepository(FakeMealLogDao(), outbox, api, { "tok" }, trigger, today = { "2026-11-17" })
    val sender = ScriptedSender()
    val processor = OutboxProcessor(outbox, notes, sender, { "tok" }, listOf(tasks, goals, meals))
    val coordinator = SyncCoordinator(processor, outbox, notes, tasks, goals, meals)

    private fun stubRefresh() {
        coEvery { api.getTasks(any()) } returns listOf(TaskDto("s1", "server"))
        coEvery { api.getWeeklyGoals(any()) } returns emptyList()
        coEvery { api.getMeals(any(), any()) } returns MealsDetailDto("2026-11-17")
    }

    @Test
    fun `after a clean drain the server state is pulled`() = runTest {
        stubRefresh()
        tasks.add("local")
        sender.script += SendOutcome.Ok("{\"id\":\"s1\"}")

        val result = coordinator.syncAll()

        assertEquals(DrainStatus.DONE, result.status)
        assertEquals(listOf("server"), tasks.items.first().map { it.title })
    }

    @Test
    fun `nothing is pulled while deliveries are still failing`() = runTest {
        stubRefresh()
        tasks.add("local")
        sender.script += SendOutcome.Network(IOException("offline"))

        assertEquals(DrainStatus.RETRY_LATER, coordinator.syncAll().status)

        coVerify(exactly = 0) { api.getTasks(any()) }
        assertEquals(listOf("local"), tasks.items.first().map { it.title })
    }

    @Test
    fun `a failing refresh does not fail the sync`() = runTest {
        coEvery { api.getTasks(any()) } throws IOException("boom")
        coEvery { api.getWeeklyGoals(any()) } throws IOException("boom")
        coEvery { api.getMeals(any(), any()) } throws IOException("boom")
        assertEquals(DrainStatus.DONE, coordinator.syncAll().status)
    }

    @Test
    fun `pending count and notices are exposed and dismissable`() = runTest {
        tasks.add("x")
        assertEquals(1, coordinator.pendingCount.first())
        sender.script += SendOutcome.Http(400, "nope")
        stubRefresh()
        coordinator.syncAll()
        val note = coordinator.notices.first().single()
        coordinator.dismissNotice(note.id)
        assertTrue(coordinator.notices.first().isEmpty())
    }

    @Test
    fun `auth pause is surfaced and cleared on login`() = runTest {
        tasks.add("x")
        sender.script += SendOutcome.Http(401, "expired")
        coordinator.syncAll()
        assertTrue(coordinator.authPaused.value)
        coordinator.resumeAfterLogin()
        assertTrue(!coordinator.authPaused.value)
    }
}
