package com.daytoday.di

import com.daytoday.network.ApiClient
import com.daytoday.repository.NbaRepository
import com.daytoday.repository.PdfRepository
import com.daytoday.repository.UserRepository
import com.daytoday.repository.WorkoutRepository
import com.daytoday.usecase.NbaUseCases
import com.daytoday.usecase.PdfUseCases
import com.daytoday.usecase.SummaryUseCases
import com.daytoday.usecase.UserUseCases
import com.daytoday.usecase.WorkoutUseCases
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideNbaUseCases(repo: NbaRepository): NbaUseCases =
        NbaUseCases(repo)

    @Provides
    fun provideWorkoutUseCases(repo: WorkoutRepository): WorkoutUseCases =
        WorkoutUseCases(repo)

    @Provides
    fun providePdfUseCases(repo: PdfRepository, api: ApiClient): PdfUseCases =
        PdfUseCases(repo, api)

    @Provides
    fun provideUserUseCases(repo: UserRepository, api: ApiClient): UserUseCases =
        UserUseCases(repo, api)

    @Provides
    fun provideSummaryUseCases(nbaRepo: NbaRepository, workoutRepo: WorkoutRepository): SummaryUseCases =
        SummaryUseCases(nbaRepo, workoutRepo)
}