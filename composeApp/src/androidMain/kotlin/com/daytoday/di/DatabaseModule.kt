package com.daytoday.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.daytoday.data.database.AppDatabase
import com.daytoday.data.database.DailyProgressDao
import com.daytoday.data.database.ExerciseDao
import com.daytoday.data.database.ExerciseSeed
import com.daytoday.data.database.InjuryDao
import com.daytoday.data.database.NbaGameDao
import com.daytoday.data.database.NbaNewsDao
import com.daytoday.data.database.PersonalBestsDao
import com.daytoday.data.database.SetRecordDao
import com.daytoday.data.database.UserDao
import com.daytoday.data.database.WeeklyProgressDao
import com.daytoday.data.database.WorkoutSessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        lateinit var database: AppDatabase
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // onCreate runs on the database open thread and cannot call suspend functions,
                // so the seed runs on an IO scope. Room opens the database lazily, which means
                // `database` is always assigned by the time this callback fires.
                seedScope.launch {
                    runCatching { ExerciseSeed.seedMissingExercises(database.exerciseDao()) }
                        .onFailure { Log.w(TAG, "Exercise seed failed", it) }
                }
            }
        }

        database = Room.databaseBuilder(context, AppDatabase::class.java, "daytoday_database")
            .addCallback(seedCallback)
            .fallbackToDestructiveMigration()
            .build()
        return database
    }

    @Provides
    fun provideNbaGameDao(db: AppDatabase): NbaGameDao = db.nbaGameDao()

    @Provides
    fun provideNbaNewsDao(db: AppDatabase): NbaNewsDao = db.nbaNewsDao()

    @Provides
    fun provideInjuryDao(db: AppDatabase): InjuryDao = db.injuryDao()

    @Provides
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideSetRecordDao(db: AppDatabase): SetRecordDao = db.setRecordDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideDailyProgressDao(db: AppDatabase): DailyProgressDao = db.dailyProgressDao()

    @Provides
    fun providePersonalBestsDao(db: AppDatabase): PersonalBestsDao = db.personalBestsDao()

    @Provides
    fun provideWeeklyProgressDao(db: AppDatabase): WeeklyProgressDao = db.weeklyProgressDao()

    private const val TAG = "DayTodayDatabase"
}