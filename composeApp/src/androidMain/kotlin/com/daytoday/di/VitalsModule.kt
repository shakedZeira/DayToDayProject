package com.daytoday.di

import android.content.Context
import com.daytoday.data.health.HealthConnectVitalsSource
import com.daytoday.data.health.VitalsRepository
import com.daytoday.data.health.VitalsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VitalsModule {

    @Provides
    @Singleton
    fun provideVitalsRepository(@ApplicationContext context: Context): VitalsRepository =
        VitalsRepositoryImpl(HealthConnectVitalsSource(context))
}
