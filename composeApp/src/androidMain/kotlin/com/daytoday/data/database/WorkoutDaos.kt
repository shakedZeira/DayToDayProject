package com.daytoday.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExerciseEntity)

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun getExerciseById(exerciseId: String): ExerciseEntity?

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

@Dao
interface SetRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SetRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SetRecordEntity)

    @Query("SELECT * FROM set_records WHERE sessionId = :sessionId")
    suspend fun getSetRecordsBySessionId(sessionId: String): List<SetRecordEntity>

    @Query("DELETE FROM set_records WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM set_records")
    suspend fun deleteAll()
}

@Dao
interface DailyProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DailyProgressEntity)

    @Query("SELECT * FROM daily_progress WHERE date = :date")
    suspend fun getProgressByDate(date: Long): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress ORDER BY date DESC")
    suspend fun getAllProgress(): List<DailyProgressEntity>

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAll()
}

@Dao
interface PersonalBestsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PersonalBestsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonalBestsEntity)

    @Query("SELECT * FROM personal_bests")
    suspend fun getAllPersonalBests(): List<PersonalBestsEntity>

    @Query("SELECT * FROM personal_bests WHERE exerciseId = :exerciseId")
    suspend fun getPersonalBestByExercise(exerciseId: String): PersonalBestsEntity?

    @Query("DELETE FROM personal_bests")
    suspend fun deleteAll()
}

@Dao
interface WeeklyProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WeeklyProgressEntity)

    @Query("SELECT * FROM weekly_progress WHERE weekStart = :weekStart")
    suspend fun getProgressByWeekStart(weekStart: Long): WeeklyProgressEntity?

    @Query("SELECT * FROM weekly_progress ORDER BY weekStart DESC")
    suspend fun getAllProgress(): List<WeeklyProgressEntity>

    @Query("DELETE FROM weekly_progress")
    suspend fun deleteAll()
}