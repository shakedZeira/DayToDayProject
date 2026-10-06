package com.daytoday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NbaGameEntity::class,
        NbaNewsEntity::class,
        InjuryEntity::class,
        WorkoutSessionEntity::class,
        ExerciseEntity::class,
        SetRecordEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nbaGameDao(): NbaGameDao
    abstract fun nbaNewsDao(): NbaNewsDao
    abstract fun injuryDao(): InjuryDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun setRecordDao(): SetRecordDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daytoday.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}