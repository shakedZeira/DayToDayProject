package com.daytoday.di

import com.daytoday.data.spotify.BearerAuthInterceptor
import com.daytoday.data.spotify.SpotifyApi
import com.daytoday.data.spotify.SpotifyAuthClient
import com.daytoday.data.spotify.SpotifyAuthClientImpl
import com.daytoday.data.spotify.SpotifyAuthManager
import com.daytoday.data.spotify.SpotifyAuthService
import com.daytoday.data.spotify.SpotifyRepository
import com.daytoday.data.spotify.SpotifyRepositoryImpl
import com.daytoday.settings.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/v1/"
    private const val SPOTIFY_AUTH_BASE_URL = "https://accounts.spotify.com/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideSpotifyOkHttpClient(
        bearerAuthInterceptor: BearerAuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(bearerAuthInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideSpotifyApi(okHttpClient: OkHttpClient, moshi: Moshi): SpotifyApi = Retrofit.Builder()
        .baseUrl(SPOTIFY_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyApi::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthService(): SpotifyAuthService = Retrofit.Builder()
        .baseUrl(SPOTIFY_AUTH_BASE_URL)
        .client(OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(provideMoshi()))
        .build()
        .create(SpotifyAuthService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthClient(authService: SpotifyAuthService): SpotifyAuthClient =
        SpotifyAuthClientImpl(authService)

    @Provides
    @Singleton
    fun provideSpotifyAuthManager(
        settingsManager: SettingsManager,
        authClient: SpotifyAuthClient,
    ): SpotifyAuthManager = SpotifyAuthManager(settingsManager, authClient)

    @Provides
    @Singleton
    fun provideBearerAuthInterceptor(settingsManager: SettingsManager): BearerAuthInterceptor {
        val tokenCache = MutableStateFlow<String?>(null)
        scope.launch {
            settingsManager.spotifyAccessToken
                .onEach { tokenCache.value = it }
                .launchIn(scope)
        }
        return BearerAuthInterceptor { tokenCache.value }
    }

    @Provides
    @Singleton
    fun provideSpotifyCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideSpotifyRepository(
        spotifyCoroutineScope: CoroutineScope,
        authManager: SpotifyAuthManager,
        api: SpotifyApi,
        settingsManager: SettingsManager,
    ): SpotifyRepository = SpotifyRepositoryImpl(authManager, api, settingsManager, spotifyCoroutineScope)
}