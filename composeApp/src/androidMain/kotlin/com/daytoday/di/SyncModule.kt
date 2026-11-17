package com.daytoday.di

import android.content.Context
import androidx.room.Room
import com.daytoday.data.sync.KtorOutboxSender
import com.daytoday.data.sync.MealsRepository
import com.daytoday.data.sync.OutboxDao
import com.daytoday.data.sync.OutboxProcessor
import com.daytoday.data.sync.SyncCoordinator
import com.daytoday.data.sync.SyncDatabase
import com.daytoday.data.sync.SyncNoteDao
import com.daytoday.data.sync.SyncTrigger
import com.daytoday.data.sync.TasksRepository
import com.daytoday.data.sync.WeeklyGoalsRepository
import com.daytoday.data.sync.WorkManagerSyncTrigger
import com.daytoday.network.DayTodayApi
import com.daytoday.network.buildApiClient
import com.daytoday.settings.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncDatabase(@ApplicationContext context: Context): SyncDatabase =
        Room.databaseBuilder(context, SyncDatabase::class.java, "daytoday_sync.db").build()

    @Provides fun provideOutboxDao(db: SyncDatabase): OutboxDao = db.outboxDao()

    @Provides fun provideSyncNoteDao(db: SyncDatabase): SyncNoteDao = db.syncNoteDao()

    @Provides
    @Singleton
    fun provideSyncTrigger(@ApplicationContext context: Context): SyncTrigger =
        WorkManagerSyncTrigger(context)

    @Provides
    @Singleton
    fun provideTasksRepository(
        db: SyncDatabase,
        api: DayTodayApi,
        settings: SettingsManager,
        trigger: SyncTrigger,
    ): TasksRepository = TasksRepository(db.taskDao(), db.outboxDao(), api, { settings.authToken.first() }, trigger)

    @Provides
    @Singleton
    fun provideWeeklyGoalsRepository(
        db: SyncDatabase,
        api: DayTodayApi,
        settings: SettingsManager,
        trigger: SyncTrigger,
    ): WeeklyGoalsRepository = WeeklyGoalsRepository(db.weeklyGoalDao(), db.outboxDao(), api, { settings.authToken.first() }, trigger)

    @Provides
    @Singleton
    fun provideMealsRepository(
        db: SyncDatabase,
        api: DayTodayApi,
        settings: SettingsManager,
        trigger: SyncTrigger,
    ): MealsRepository = MealsRepository(db.mealLogDao(), db.outboxDao(), api, { settings.authToken.first() }, trigger)

    @Provides
    @Singleton
    fun provideOutboxProcessor(
        db: SyncDatabase,
        settings: SettingsManager,
        tasks: TasksRepository,
        goals: WeeklyGoalsRepository,
        meals: MealsRepository,
    ): OutboxProcessor = OutboxProcessor(
        outbox = db.outboxDao(),
        notes = db.syncNoteDao(),
        sender = KtorOutboxSender(buildApiClient()),
        token = { settings.authToken.first() },
        handlers = listOf(tasks, goals, meals),
    )

    @Provides
    @Singleton
    fun provideSyncCoordinator(
        db: SyncDatabase,
        processor: OutboxProcessor,
        tasks: TasksRepository,
        goals: WeeklyGoalsRepository,
        meals: MealsRepository,
    ): SyncCoordinator = SyncCoordinator(processor, db.outboxDao(), db.syncNoteDao(), tasks, goals, meals)
}
