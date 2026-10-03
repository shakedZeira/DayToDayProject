package com.daytoday.di

import com.daytoday.data.remote.NbaBigBallsClient
import com.daytoday.data.remote.NbaEspnClient
import com.daytoday.network.ApiClient
import com.daytoday.network.DayTodayApi
import com.daytoday.network.DayTodayApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import javax.inject.Singleton
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient

    @Provides
    @Singleton
    fun provideDayTodayApi(): DayTodayApi = DayTodayApiImpl()

    // NBA real-API clients (from the NBA Daily project).
    // BigBallsData needs an API key for live games; ESPN is free.
    @Provides
    @Singleton
    fun provideNbaBigBallsClient(): NbaBigBallsClient = NbaBigBallsClient()

    @Provides
    @Singleton
    fun provideNbaEspnClient(): NbaEspnClient = NbaEspnClient()
}