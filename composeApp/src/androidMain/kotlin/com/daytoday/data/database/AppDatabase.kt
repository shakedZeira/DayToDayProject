package com.daytoday.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NbaGameEntity::class,
        NbaNewsEntity::class,
        InjuryEntity::class,
        WorkoutSessionEntity::class,
        ExerciseEntity::class,
        SetRecordEntity::class,
        DailyProgressEntity::class,
        PersonalBestsEntity::class,
        WeeklyProgressEntity::class,
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
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun personalBestsDao(): PersonalBestsDao
    abstract fun weeklyProgressDao(): WeeklyProgressDao
    abstract fun userDao(): UserDao
}