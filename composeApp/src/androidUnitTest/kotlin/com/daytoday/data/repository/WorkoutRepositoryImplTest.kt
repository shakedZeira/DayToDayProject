package com.daytoday.data.repository

import androidx.room.withTransaction
import com.daytoday.data.database.AppDatabase
import com.daytoday.data.database.DailyProgressDao
import com.daytoday.data.database.ExerciseDao
import com.daytoday.data.database.PersonalBestsDao
import com.daytoday.data.database.SetRecordDao
import com.daytoday.data.database.WeeklyProgressDao
import com.daytoday.data.database.WorkoutSessionDao
import com.daytoday.data.database.WorkoutSessionEntity
import com.daytoday.model.SetRecord
import com.daytoday.model.WorkoutSession
import com.daytoday.network.DayTodayApi
import com.daytoday.repository.Result
import com.daytoday.testing.newSettingsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutRepositoryImplTest {

    private val database: AppDatabase = mockk(relaxed = true)
    private val sessionDao: WorkoutSessionDao = mockk(relaxed = true)
    private val exerciseDao: ExerciseDao = mockk(relaxed = true)
    private val setDao: SetRecordDao = mockk(relaxed = true)
    private val dailyDao: DailyProgressDao = mockk(relaxed = true)
    private val bestsDao: PersonalBestsDao = mockk(relaxed = true)
    private val weeklyDao: WeeklyProgressDao = mockk(relaxed = true)
    private val api: DayTodayApi = mockk()

    private val repo = WorkoutRepositoryImpl(
        database, sessionDao, exerciseDao, setDao, dailyDao, bestsDao, weeklyDao, api, newSettingsManager(),
    )

    private fun session(id: String) = WorkoutSession(
        id = id, exerciseId = "ex", exerciseName = "Push-Up", startTime = 1_000L, endTime = 2_000L,
        durationMinutes = 1, caloriesBurned = 10,
        sets = listOf(SetRecord("set-$id", id, 1, 0.0, 10, null, true, 1_500L)),
        isCompleted = true,
    )

    @Before
    fun setUp() {
        // Room's withTransaction needs a real database; run the block inline instead.
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            arg<suspend () -> Any>(1).invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `getSessions returns network data and refreshes the cache`() = runTest {
        coEvery { sessionDao.getSessionsPaged(20, 0) } returns emptyList()
        coEvery { api.getSessions(20, 0) } returns listOf(session("a"))
        val result = repo.getSessions(20, 0)
        assertEquals(listOf("a"), (result as Result.Success).data.map { it.id })
        coVerify { sessionDao.insertAll(match { it.single().id == "a" }) }
    }

    @Test
    fun `getSessions falls back to the cache when offline`() = runTest {
        coEvery { sessionDao.getSessionsPaged(20, 0) } returns listOf(WorkoutSessionEntity.fromModel(session("cached")))
        coEvery { api.getSessions(any(), any()) } throws IOException("offline")
        val result = repo.getSessions(20, 0)
        assertEquals(listOf("cached"), (result as Result.Success).data.map { it.id })
    }

    @Test
    fun `getSessions fails when offline with an empty cache`() = runTest {
        coEvery { sessionDao.getSessionsPaged(any(), any()) } returns emptyList()
        coEvery { api.getSessions(any(), any()) } throws IOException("offline")
        assertEquals("offline", (repo.getSessions(20, 0) as Result.Failure).error)
    }

    @Test
    fun `getCachedSessions never touches the network`() = runTest {
        coEvery { sessionDao.getSessionsPaged(5, 0) } returns listOf(WorkoutSessionEntity.fromModel(session("c")))
        val result = repo.getCachedSessions(5, 0)
        assertEquals(listOf("c"), (result as Result.Success).data.map { it.id })
        coVerify(exactly = 0) { api.getSessions(any(), any()) }
    }

    @Test
    fun `getCachedSessions maps database errors to failure`() = runTest {
        coEvery { sessionDao.getSessionsPaged(any(), any()) } throws IllegalStateException("corrupt")
        assertEquals("corrupt", (repo.getCachedSessions(5, 0) as Result.Failure).error)
    }

    @Test
    fun `saveSession stores locally first and succeeds even if the upload fails`() = runTest {
        coEvery { api.saveSession(any()) } throws IOException("offline")
        val s = session("a")
        val result = repo.saveSession(s)
        assertEquals(s, (result as Result.Success).data)
        coVerify { sessionDao.insert(match { it.id == "a" }) }
        coVerify { setDao.deleteBySessionId("a") }
        coVerify { setDao.insertAll(match { it.size == 1 }) }
    }

    @Test
    fun `saveSession returns the server copy when online`() = runTest {
        val server = session("a").copy(caloriesBurned = 99)
        coEvery { api.saveSession(any()) } returns server
        assertEquals(99, (repo.saveSession(session("a")) as Result.Success).data.caloriesBurned)
    }

    @Test
    fun `saveSessions persists every session locally`() = runTest {
        coEvery { api.saveSessions(any()) } throws IOException("offline")
        val result = repo.saveSessions(listOf(session("a"), session("b")))
        assertEquals(2, (result as Result.Success).data.size)
        coVerify(exactly = 2) { setDao.deleteBySessionId(any()) }
    }

    @Test
    fun `deleteSession removes local rows even if the server call fails`() = runTest {
        coEvery { api.deleteSession("a") } throws IOException("offline")
        assertTrue(repo.deleteSession("a") is Result.Success)
        coVerify { sessionDao.deleteBySessionId("a") }
        coVerify { setDao.deleteBySessionId("a") }
    }

    @Test
    fun `getDailyProgress rejects an unparsable date`() = runTest {
        val result = repo.getDailyProgress("not-a-date")
        assertEquals("Invalid date format", (result as Result.Failure).error)
    }

    @Test
    fun `getWeeklyProgress rejects an unparsable week`() = runTest {
        val result = repo.getWeeklyProgress("???")
        assertEquals("Invalid weekStart format", (result as Result.Failure).error)
    }

    @Test
    fun `getPersonalBests fails with no cache and no network`() = runTest {
        coEvery { bestsDao.getAllPersonalBests() } returns emptyList()
        coEvery { api.getPersonalBests() } throws IOException("offline")
        assertTrue(repo.getPersonalBests() is Result.Failure)
    }

    @Test
    fun `getExercises returns the cached exercise list`() = runTest {
        coEvery { exerciseDao.getAllExercises() } returns emptyList()
        assertTrue((repo.getExercises() as Result.Success).data.isEmpty())
    }
}
