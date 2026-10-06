package com.daytoday.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NbaGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: NbaGameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<NbaGameEntity>)

    @Query("SELECT * FROM nba_games WHERE gameId = :gameId")
    fun getGameById(gameId: String): Flow<NbaGameEntity?>

    @Query("SELECT * FROM nba_games WHERE gameId = :gameId")
    suspend fun getGameByIdOnce(gameId: String): NbaGameEntity?

    @Query("SELECT * FROM nba_games WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime ASC")
    fun getGamesInRange(startTime: Long, endTime: Long): Flow<List<NbaGameEntity>>

    @Query("SELECT * FROM nba_games WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime ASC")
    suspend fun getGamesInRangeOnce(startTime: Long, endTime: Long): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games WHERE isCompleted = 0 ORDER BY startTime ASC")
    fun getUpcomingGames(): Flow<List<NbaGameEntity>>

    @Query("SELECT * FROM nba_games WHERE isCompleted = 0 ORDER BY startTime ASC")
    suspend fun getUpcomingGamesOnce(): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games WHERE isCompleted = 1 ORDER BY startTime DESC LIMIT :limit")
    fun getRecentCompletedGames(limit: Int): Flow<List<NbaGameEntity>>

    @Query("SELECT * FROM nba_games WHERE isCompleted = 1 ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentCompletedGamesOnce(limit: Int): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games WHERE homeTeam = :team OR awayTeam = :team ORDER BY startTime DESC")
    fun getGamesByTeam(team: String): Flow<List<NbaGameEntity>>

    @Query("SELECT * FROM nba_games WHERE homeTeam = :team OR awayTeam = :team ORDER BY startTime DESC")
    suspend fun getGamesByTeamOnce(team: String): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecentCachedGames(limit: Int): List<NbaGameEntity>

    @Update
    suspend fun update(game: NbaGameEntity)

    @Delete
    suspend fun delete(game: NbaGameEntity)

    @Query("DELETE FROM nba_games WHERE cachedAt < :threshold")
    suspend fun deleteOldCachedGames(threshold: Long): Int

    @Query("DELETE FROM nba_games")
    suspend fun clearAllGames()
}

@Dao
interface NbaNewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(news: NbaNewsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(newsList: List<NbaNewsEntity>)

    @Query("SELECT * FROM nba_news WHERE newsId = :newsId")
    fun getNewsById(newsId: String): Flow<NbaNewsEntity?>

    @Query("SELECT * FROM nba_news WHERE newsId = :newsId")
    suspend fun getNewsByIdOnce(newsId: String): NbaNewsEntity?

    @Query("SELECT * FROM nba_news ORDER BY publishedAt DESC LIMIT :limit")
    fun getLatestNews(limit: Int): Flow<List<NbaNewsEntity>>

    @Query("SELECT * FROM nba_news ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getLatestNewsOnce(limit: Int): List<NbaNewsEntity>

    @Query("SELECT * FROM nba_news WHERE teamIdsJson LIKE '%' || :teamId || '%' ORDER BY publishedAt DESC LIMIT :limit")
    fun getNewsByTeam(teamId: String, limit: Int): Flow<List<NbaNewsEntity>>

    @Query("SELECT * FROM nba_news WHERE teamIdsJson LIKE '%' || :teamId || '%' ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getNewsByTeamOnce(teamId: String, limit: Int): List<NbaNewsEntity>

    @Query("SELECT * FROM nba_news ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecentCachedNews(limit: Int): List<NbaNewsEntity>

    @Update
    suspend fun update(news: NbaNewsEntity)

    @Delete
    suspend fun delete(news: NbaNewsEntity)

    @Query("DELETE FROM nba_news WHERE cachedAt < :threshold")
    suspend fun deleteOldCachedNews(threshold: Long): Int

    @Query("DELETE FROM nba_news")
    suspend fun clearAllNews()
}

@Dao
interface InjuryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(injury: InjuryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(injuries: List<InjuryEntity>)

    @Query("SELECT * FROM injuries WHERE injuryId = :injuryId")
    fun getInjuryById(injuryId: String): Flow<InjuryEntity?>

    @Query("SELECT * FROM injuries WHERE injuryId = :injuryId")
    suspend fun getInjuryByIdOnce(injuryId: String): InjuryEntity?

    @Query("SELECT * FROM injuries WHERE teamId = :teamId ORDER BY lastUpdated DESC")
    fun getInjuriesByTeam(teamId: String): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injuries WHERE teamId = :teamId ORDER BY lastUpdated DESC")
    suspend fun getInjuriesByTeamOnce(teamId: String): List<InjuryEntity>

    @Query("SELECT * FROM injuries WHERE playerId = :playerId")
    fun getInjuriesByPlayer(playerId: String): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injuries WHERE playerId = :playerId")
    suspend fun getInjuriesByPlayerOnce(playerId: String): List<InjuryEntity>

    @Query("SELECT * FROM injuries WHERE status = :status ORDER BY lastUpdated DESC")
    fun getInjuriesByStatus(status: String): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injuries WHERE status = :status ORDER BY lastUpdated DESC")
    suspend fun getInjuriesByStatusOnce(status: String): List<InjuryEntity>

    @Query("SELECT * FROM injuries ORDER BY lastUpdated DESC LIMIT :limit")
    suspend fun getRecentInjuries(limit: Int): List<InjuryEntity>

    @Query("SELECT * FROM injuries WHERE endDate IS NULL OR endDate > :now ORDER BY startDate ASC")
    fun getActiveInjuries(now: Long): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injuries WHERE endDate IS NULL OR endDate > :now ORDER BY startDate ASC")
    suspend fun getActiveInjuriesOnce(now: Long): List<InjuryEntity>

    @Update
    suspend fun update(injury: InjuryEntity)

    @Delete
    suspend fun delete(injury: InjuryEntity)

    @Query("DELETE FROM injuries WHERE cachedAt < :threshold")
    suspend fun deleteOldCachedInjuries(threshold: Long): Int

    @Query("DELETE FROM injuries")
    suspend fun clearAllInjuries()
}

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<WorkoutSessionEntity>)

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :sessionId")
    fun getSessionById(sessionId: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionByIdOnce(sessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessionsOnce(limit: Int): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE exerciseId = :exerciseId ORDER BY startTime DESC")
    fun getSessionsByExercise(exerciseId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE exerciseId = :exerciseId ORDER BY startTime DESC")
    suspend fun getSessionsByExerciseOnce(exerciseId: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getSessionsInRange(startTime: Long, endTime: Long): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    suspend fun getSessionsInRangeOnce(startTime: Long, endTime: Long): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY startTime ASC")
    fun getIncompleteSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY startTime ASC")
    suspend fun getIncompleteSessionsOnce(): List<WorkoutSessionEntity>

    @Query("SELECT SUM(caloriesBurned) FROM workout_sessions WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun getTotalCaloriesInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT SUM(durationMinutes) FROM workout_sessions WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun getTotalDurationInRange(startTime: Long, endTime: Long): Int

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Delete
    suspend fun delete(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE cachedAt < :threshold")
    suspend fun deleteOldCachedSessions(threshold: Long): Int

    @Query("DELETE FROM workout_sessions")
    suspend fun clearAllSessions()
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercises WHERE exerciseId = :exerciseId")
    fun getExerciseById(exerciseId: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE exerciseId = :exerciseId")
    suspend fun getExerciseByIdOnce(exerciseId: String): ExerciseEntity?

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllExercisesOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup ORDER BY name ASC")
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup ORDER BY name ASC")
    suspend fun getExercisesByMuscleGroupOnce(muscleGroup: String): List<ExerciseEntity>

    @Query("SELECT DISTINCT muscleGroup FROM exercises ORDER BY muscleGroup ASC")
    suspend fun getAllMuscleGroups(): List<String>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchExercisesOnce(query: String): List<ExerciseEntity>

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE cachedAt < :threshold")
    suspend fun deleteOldCachedExercises(threshold: Long): Int

    @Query("DELETE FROM exercises")
    suspend fun clearAllExercises()
}

@Dao
interface SetRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SetRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SetRecordEntity>)

    @Query("SELECT * FROM set_records WHERE sessionId = :sessionId ORDER BY setNumber ASC")
    fun getRecordsBySession(sessionId: String): Flow<List<SetRecordEntity>>

    @Query("SELECT * FROM set_records WHERE sessionId = :sessionId ORDER BY setNumber ASC")
    suspend fun getRecordsBySessionOnce(sessionId: String): List<SetRecordEntity>

    @Query("SELECT * FROM set_records WHERE sessionId = :sessionId AND setNumber = :setNumber")
    suspend fun getRecord(sessionId: String, setNumber: Int): SetRecordEntity?

    @Query("SELECT MAX(weight) FROM set_records WHERE sessionId = :sessionId")
    suspend fun getMaxWeightForSession(sessionId: String): Double?

    @Query("SELECT SUM(weight * reps) FROM set_records WHERE sessionId = :sessionId AND isCompleted = 1")
    suspend fun getTotalVolumeForSession(sessionId: String): Double?

    @Query("SELECT AVG(rpe) FROM set_records WHERE sessionId = :sessionId AND isCompleted = 1 AND rpe IS NOT NULL")
    suspend fun getAverageRpeForSession(sessionId: String): Double?

    @Update
    suspend fun update(record: SetRecordEntity)

    @Delete
    suspend fun delete(record: SetRecordEntity)

    @Query("DELETE FROM set_records WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int

    @Query("DELETE FROM set_records")
    suspend fun clearAllRecords()
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByIdOnce(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email")
    fun getUserByEmail(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmailOnce(email: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY createdAt DESC LIMIT 1")
    fun getLatestUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestUserOnce(): UserEntity?

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET token = :token WHERE userId = :userId")
    suspend fun updateToken(userId: String, token: String?): Int

    @Delete
    suspend fun delete(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}