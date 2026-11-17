package com.daytoday.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Entity types an outbox row can belong to. */
object SyncEntity {
    const val TASK = "task"
    const val GOAL = "goal"
    const val MEAL = "meal"
}

/**
 * One pending write against the backend. Rows are replayed in [id] order, which is
 * the order the user made the changes in.
 *
 * [tempId] is set on create requests: the row was shown locally under that
 * client-generated id, and every later request that mentions it in its path is
 * rewritten to the server id once the create succeeds.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: String,
    val path: String,
    val body: String?,
    val createdAt: Long,
    val entityType: String,
    val tempId: String? = null,
    val attempts: Int = 0,
)

/** A message for the user, e.g. "a change was rejected by the server and dropped". */
@Entity(tableName = "sync_notes")
data class SyncNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val createdAt: Long,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val status: String,
    val dueAt: String?,
    val recurrence: String?,
    val category: String?,
    val remindAt: String?,
    val createdAt: Long,
)

@Entity(tableName = "weekly_goals")
data class WeeklyGoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val targetCount: Int,
    val unit: String?,
    val category: String?,
    val thisWeekCount: Int,
    val createdAt: Long,
)

@Entity(tableName = "meal_logs")
data class MealLogEntity(
    @PrimaryKey val id: String,
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val calories: Int,
    /** ISO local date (yyyy-MM-dd) the meal belongs to. */
    val date: String,
    val createdAt: Long,
)

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun all(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE entityType = :entityType")
    suspend fun countFor(entityType: String): Int

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("UPDATE outbox SET path = :path, body = :body WHERE id = :id")
    suspend fun updateRequest(id: Long, path: String, body: String?)
}

@Dao
interface SyncNoteDao {
    @Insert
    suspend fun insert(entity: SyncNoteEntity): Long

    @Query("SELECT * FROM sync_notes ORDER BY id ASC")
    fun observeAll(): Flow<List<SyncNoteEntity>>

    @Query("SELECT * FROM sync_notes ORDER BY id ASC")
    suspend fun all(): List<SyncNoteEntity>

    @Query("DELETE FROM sync_notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sync_notes")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface WeeklyGoalDao {
    @Query("SELECT * FROM weekly_goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WeeklyGoalEntity>>

    @Query("SELECT * FROM weekly_goals WHERE id = :id")
    suspend fun get(id: String): WeeklyGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeeklyGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WeeklyGoalEntity>)

    @Query("DELETE FROM weekly_goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM weekly_goals")
    suspend fun deleteAll()
}

@Dao
interface MealLogDao {
    @Query("SELECT * FROM meal_logs WHERE date = :date ORDER BY createdAt ASC")
    fun observeForDate(date: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE id = :id")
    suspend fun get(id: String): MealLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MealLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MealLogEntity>)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM meal_logs WHERE date = :date")
    suspend fun deleteForDate(date: String)
}
