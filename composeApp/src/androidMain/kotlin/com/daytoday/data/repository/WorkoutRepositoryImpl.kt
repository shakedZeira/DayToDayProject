package com.daytoday.data.repository

import android.util.Log
import com.daytoday.data.database.AppDatabase
import com.daytoday.data.database.DailyProgressDao
import com.daytoday.data.database.ExerciseDao
import com.daytoday.data.database.PersonalBestsDao
import com.daytoday.data.database.SetRecordDao
import com.daytoday.data.database.WeeklyProgressDao
import com.daytoday.data.database.WorkoutSessionDao
import com.daytoday.data.database.WorkoutSessionEntity
import com.daytoday.data.database.ExerciseSeed
import com.daytoday.data.database.SetRecordEntity
import com.daytoday.data.database.DailyProgressEntity
import com.daytoday.data.database.PersonalBestsEntity
import com.daytoday.data.database.WeeklyProgressEntity
import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession
import com.daytoday.model.SetRecord
import com.daytoday.network.DayTodayApi
import com.daytoday.repository.WorkoutRepository
import com.daytoday.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val sessionDao: WorkoutSessionDao,
    private val exerciseDao: ExerciseDao,
    private val setRecordDao: SetRecordDao,
    private val dailyProgressDao: DailyProgressDao,
    private val personalBestsDao: PersonalBestsDao,
    private val weeklyProgressDao: WeeklyProgressDao,
    private val api: DayTodayApi
) : WorkoutRepository {

    companion object {
        private const val TAG = "WorkoutRepository"
    }

    override suspend fun getSessions(limit: Int, offset: Int): Result<List<WorkoutSession>> = withContext(Dispatchers.IO) {
        val cached = sessionDao.getSessionsPaged(limit, offset).map { it.toModel() }
        try {
            val response = api.getSessions(limit, offset)
            val entities = response.map { WorkoutSessionEntity.fromModel(it) }
            sessionDao.insertAll(entities)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch sessions failed, returning cached data", e)
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e.message ?: "Failed to fetch sessions", e)
        }
    }

    override suspend fun getSession(sessionId: String): Result<WorkoutSession> = withContext(Dispatchers.IO) {
        val cachedEntity = sessionDao.getSessionById(sessionId)
        try {
            val response = api.getSession(sessionId)
            val entity = WorkoutSessionEntity.fromModel(response)
            sessionDao.insert(entity)
            val sets = response.sets.map { SetRecordEntity.fromModel(it) }
            setRecordDao.deleteBySessionId(sessionId)
            setRecordDao.insertAll(sets)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch session failed, returning cached data", e)
            cachedEntity?.let { entity ->
                val sets = setRecordDao.getSetRecordsBySessionId(sessionId).map { it.toModel() }
                Result.success(entity.toModel().copy(sets = sets))
            } ?: Result.failure(e.message ?: "Failed to fetch session", e)
        }
    }

    override suspend fun saveSession(session: WorkoutSession): Result<WorkoutSession> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val sessionEntity = WorkoutSessionEntity.fromModel(session)
            sessionDao.insert(sessionEntity)

            val setEntities = session.sets.map { SetRecordEntity.fromModel(it) }
            setRecordDao.deleteBySessionId(session.id)
            setRecordDao.insertAll(setEntities)
        }

        try {
            val saved = api.saveSession(session)
            Result.success(saved)
        } catch (e: Exception) {
            Log.w(TAG, "Network save session failed (best-effort), local save succeeded", e)
            Result.success(session)
        }
    }

    override suspend fun saveSessions(sessions: List<WorkoutSession>): Result<List<WorkoutSession>> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val sessionEntities = sessions.map { WorkoutSessionEntity.fromModel(it) }
            sessionDao.insertAll(sessionEntities)

            sessions.forEach { session ->
                val setEntities = session.sets.map { SetRecordEntity.fromModel(it) }
                setRecordDao.deleteBySessionId(session.id)
                setRecordDao.insertAll(setEntities)
            }
        }

        try {
            val saved = api.saveSessions(sessions)
            Result.success(saved)
        } catch (e: Exception) {
            Log.w(TAG, "Network save sessions failed (best-effort), local save succeeded", e)
            Result.success(sessions)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        database.withTransaction {
            sessionDao.deleteBySessionId(sessionId)
            setRecordDao.deleteBySessionId(sessionId)
        }

        try {
            api.deleteSession(sessionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Network delete session failed (best-effort), local delete succeeded", e)
            Result.success(Unit)
        }
    }

    override suspend fun getExercises(): Result<List<Exercise>> = withContext(Dispatchers.IO) {
        try {
            runCatching { ExerciseSeed.seedMissingExercises(exerciseDao) }
            val cached = exerciseDao.getAllExercises().map { it.toModel() }
            Result.success(cached)
        } catch (e: Exception) {
            Result.failure(e.message ?: "Failed to load exercises", e)
        }
    }

    override suspend fun getDailyProgress(date: String): Result<DailyProgress> = withContext(Dispatchers.IO) {
        val dateLong = parseDateToEpochMillis(date) ?: return@withContext Result.failure("Invalid date format")
        val cached = dailyProgressDao.getProgressByDate(dateLong)?.toModel()
        try {
            val response = api.getDailyProgress(date)
            val entity = DailyProgressEntity.fromModel(response)
            dailyProgressDao.insert(entity)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch daily progress failed, returning cached data", e)
            cached?.let { Result.success(it) } ?: Result.failure(e.message ?: "Failed to fetch daily progress", e)
        }
    }

    override suspend fun getPersonalBests(): Result<List<PersonalBests>> = withContext(Dispatchers.IO) {
        val cached = personalBestsDao.getAllPersonalBests().map { it.toModel() }
        try {
            val response = api.getPersonalBests()
            val entities = response.map { PersonalBestsEntity.fromModel(it) }
            personalBestsDao.insertAll(entities)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch personal bests failed, returning cached data", e)
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e.message ?: "Failed to fetch personal bests", e)
        }
    }

    override suspend fun getWeeklyProgress(weekStart: String): Result<WeeklyProgress> = withContext(Dispatchers.IO) {
        val weekStartLong = parseDateToEpochMillis(weekStart) ?: return@withContext Result.failure("Invalid weekStart format")
        val cached = weeklyProgressDao.getProgressByWeekStart(weekStartLong)?.toModel()
        try {
            val response = api.getWeeklyProgress(weekStart)
            val entity = WeeklyProgressEntity.fromModel(response)
            weeklyProgressDao.insert(entity)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch weekly progress failed, returning cached data", e)
            cached?.let { Result.success(it) } ?: Result.failure(e.message ?: "Failed to fetch weekly progress", e)
        }
    }

    private fun parseDateToEpochMillis(dateStr: String): Long? {
        return try {
            LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            dateStr.toLongOrNull()
        }
    }
}