package com.daytoday.data.sync

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline-first store for the data the backend owns (tasks, weekly goals, meals) plus
 * the outbox of writes not yet delivered. It is a separate database from
 * [com.daytoday.data.database.AppDatabase] so adding it needs no migration of the
 * existing workout/NBA cache.
 */
@Database(
    entities = [
        OutboxEntity::class,
        SyncNoteEntity::class,
        TaskEntity::class,
        WeeklyGoalEntity::class,
        MealLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun syncNoteDao(): SyncNoteDao
    abstract fun taskDao(): TaskDao
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun mealLogDao(): MealLogDao
}
