package com.daytoday.di

import com.daytoday.data.repository.NbaRepositoryImpl
import com.daytoday.data.repository.PdfRepositoryImpl
import com.daytoday.data.repository.UserRepositoryImpl
import com.daytoday.data.repository.WorkoutRepositoryImpl

import com.daytoday.repository.NbaRepository
import com.daytoday.repository.PdfRepository
import com.daytoday.repository.UserRepository
import com.daytoday.repository.WorkoutRepository
import dagger.Module
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindNbaRepository(impl: NbaRepositoryImpl): NbaRepository

    @Binds
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    abstract fun bindPdfRepository(impl: PdfRepositoryImpl): PdfRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    }