package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.MealLogDto
import com.daytoday.network.MealsDetailDto
import com.daytoday.network.WeeklyGoalDto
import com.daytoday.testing.CountingTrigger
import com.daytoday.testing.FakeMealLogDao
import com.daytoday.testing.FakeOutboxDao
import com.daytoday.testing.FakeWeeklyGoalDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGoalsRepositoryTest {

    private val goals = FakeWeeklyGoalDao()
    private val outbox = FakeOutboxDao()
    private val api: DayTodayApi = mockk()
    private val trigger = CountingTrigger()
    private var now = 1L
    private val repo = WeeklyGoalsRepository(
        goals, outbox, api, { "tok" }, trigger, clock = { now++ }, newId = { "local-g" },
    )

    @Test
    fun `add shows the goal and queues a create with a clamped target`() = runTest {
        repo.add(" Run ", 0, "km")
        val g = repo.items.first().single()
        assertEquals("Run", g.title)
        assertEquals(1, g.targetCount)
        assertTrue(g.unsynced)
        val e = outbox.snapshot.single()
        assertEquals("api/goals", e.path)
        assertEquals("local-g", e.tempId)
        assertTrue(e.body!!.contains("\"unit\":\"km\""))
    }

    @Test
    fun `blank goal title is ignored`() = runTest {
        assertNull(repo.add("", 3))
        assertTrue(outbox.snapshot.isEmpty())
    }

    @Test
    fun `check off increments and undo decrements but never below zero`() = runTest {
        goals.upsert(WeeklyGoalEntity("g1", "Run", 3, null, null, 0, 1))
        repo.checkOff("g1")
        repo.checkOff("g1")
        assertEquals(2, repo.items.first().single().thisWeekCount)
        repo.undo("g1")
        repo.undo("g1")
        repo.undo("g1")
        assertEquals(0, repo.items.first().single().thisWeekCount)
        assertEquals(
            listOf("api/goals/g1/checkoff", "api/goals/g1/checkoff", "api/goals/g1/undo", "api/goals/g1/undo"),
            outbox.snapshot.map { it.path },
        )
    }

    @Test
    fun `delete of a synced goal queues a DELETE and of a local goal cancels the queue`() = runTest {
        goals.upsert(WeeklyGoalEntity("g1", "Run", 3, null, null, 0, 1))
        repo.delete("g1")
        assertEquals("DELETE", outbox.snapshot.single().method)

        val local = repo.add("Temp", 2)!!
        repo.checkOff(local)
        repo.delete(local)
        assertEquals(1, outbox.snapshot.size)
    }

    @Test
    fun `refresh pulls server goals only when idle`() = runTest {
        coEvery { api.getWeeklyGoals("tok") } returns listOf(WeeklyGoalDto("s1", "Read", 5, thisWeekCount = 2))
        assertTrue(repo.refresh())
        assertEquals(2, repo.items.first().single().thisWeekCount)
        repo.checkOff("s1")
        assertFalse(repo.refresh())
        assertEquals(3, repo.items.first().single().thisWeekCount)
    }
}

class MealsRepositoryTest {

    private val meals = FakeMealLogDao()
    private val outbox = FakeOutboxDao()
    private val api: DayTodayApi = mockk()
    private val trigger = CountingTrigger()
    private var now = 1_000L
    private var n = 0
    private val repo = MealsRepository(
        meals, outbox, api, { "tok" }, trigger,
        clock = { now++ }, newId = { "local-m${++n}" }, today = { "2026-11-17" },
    )

    @Test
    fun `log adds the meal to today and queues a create`() = runTest {
        repo.log("food1", "Rice", 150.0, 195)
        val list = repo.mealsFor("2026-11-17").first()
        assertEquals(listOf("Rice"), list.map { it.foodName })
        assertEquals(195, list.single().calories)
        val e = outbox.snapshot.single()
        assertEquals("POST", e.method)
        assertEquals("api/foods/meals", e.path)
        assertTrue(e.body!!.contains("\"foodId\":\"food1\""))
        assertTrue(e.body!!.contains("\"grams\":150.0"))
        assertEquals(1, trigger.requests)
    }

    @Test
    fun `invalid meals are rejected`() = runTest {
        assertNull(repo.log("", "x", 100.0, 1))
        assertNull(repo.log("f", "x", 0.0, 1))
        assertNull(repo.log("f", "x", Double.NaN, 1))
        assertTrue(outbox.snapshot.isEmpty())
    }

    @Test
    fun `deleting a meal that never synced removes it without a request`() = runTest {
        val id = repo.log("f", "Rice", 100.0, 130)!!
        repo.delete(id)
        assertTrue(outbox.snapshot.isEmpty())
        assertTrue(repo.mealsFor("2026-11-17").first().isEmpty())
    }

    @Test
    fun `deleting a synced meal queues a DELETE`() = runTest {
        meals.upsert(MealLogEntity("m1", "f", "Rice", 100.0, 130, "2026-11-17", 1))
        repo.delete("m1")
        assertEquals("api/foods/meals/m1", outbox.snapshot.single().path)
    }

    @Test
    fun `refresh replaces only the requested day`() = runTest {
        meals.upsert(MealLogEntity("old", "f", "Old", 1.0, 1, "2026-11-17", 1))
        meals.upsert(MealLogEntity("other", "f", "Other", 1.0, 1, "2026-11-16", 1))
        coEvery { api.getMeals("tok", "2026-11-17") } returns MealsDetailDto(
            "2026-11-17", listOf(MealLogDto("m9", "f", "Pasta", 200.0, 300)),
        )
        assertTrue(repo.refresh())
        assertEquals(listOf("Pasta"), repo.mealsFor("2026-11-17").first().map { it.foodName })
        assertEquals(listOf("Other"), repo.mealsFor("2026-11-16").first().map { it.foodName })
    }

    @Test
    fun `refresh does not run while meal writes are pending`() = runTest {
        repo.log("f", "Rice", 100.0, 130)
        assertFalse(repo.refresh())
    }
}
