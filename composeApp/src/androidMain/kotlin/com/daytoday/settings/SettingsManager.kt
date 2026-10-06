package com.daytoday.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.daytoday.model.UserProfile
import com.daytoday.model.WorkoutPlan
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsManager(private val dataStore: DataStore<Preferences>) {

    companion object {
        private const val TOKEN_KEY = "auth_token"
        private const val THEME_KEY = "theme_preference"
        private const val NOTIFICATIONS_KEY = "notifications_enabled"
        private const val WEIGHT_KG_KEY = "user_profile_weight_kg"
        private const val HEIGHT_CM_KEY = "user_profile_height_cm"
        private const val AGE_YEARS_KEY = "user_profile_age_years"
        private const val IS_MALE_KEY = "user_profile_is_male"
        private const val MIN_AGE_YEARS = 1
        private const val MAX_AGE_YEARS = 120

        private const val SPOTIFY_ACCESS_TOKEN_KEY = "spotify_access_token"
        private const val SPOTIFY_REFRESH_TOKEN_KEY = "spotify_refresh_token"
        private const val SPOTIFY_TOKEN_EXPIRES_AT_KEY = "spotify_token_expires_at"
        private const val SPOTIFY_LOGGED_IN_KEY = "spotify_logged_in"
        private const val SPOTIFY_PLAYLIST_URI_KEY = "spotify_playlist_uri"
        private const val SPOTIFY_PLAYLIST_NAME_KEY = "spotify_playlist_name"
        private const val SAVED_WORKOUT_PLANS_KEY = "saved_workout_plans"
    }

    private val tokenKey = stringPreferencesKey(TOKEN_KEY)
    private val themeKey = stringPreferencesKey(THEME_KEY)
    private val notificationsKey = booleanPreferencesKey(NOTIFICATIONS_KEY)
    private val weightKgKey = doublePreferencesKey(WEIGHT_KG_KEY)
    private val heightCmKey = doublePreferencesKey(HEIGHT_CM_KEY)
    private val ageYearsKey = intPreferencesKey(AGE_YEARS_KEY)
    private val isMaleKey = booleanPreferencesKey(IS_MALE_KEY)
    private val workoutPlansKey = stringPreferencesKey(SAVED_WORKOUT_PLANS_KEY)

    private val spotifyAccessTokenKey = stringPreferencesKey(SPOTIFY_ACCESS_TOKEN_KEY)
    private val spotifyRefreshTokenKey = stringPreferencesKey(SPOTIFY_REFRESH_TOKEN_KEY)
    private val spotifyTokenExpiresAtKey = longPreferencesKey(SPOTIFY_TOKEN_EXPIRES_AT_KEY)
    private val spotifyLoggedInKey = booleanPreferencesKey(SPOTIFY_LOGGED_IN_KEY)
    private val spotifyPlaylistUriKey = stringPreferencesKey(SPOTIFY_PLAYLIST_URI_KEY)
    private val spotifyPlaylistNameKey = stringPreferencesKey(SPOTIFY_PLAYLIST_NAME_KEY)

    private val defaultProfile = UserProfile()

    val authToken: kotlinx.coroutines.flow.Flow<String> = dataStore.data
        .map { it[tokenKey] ?: "" }
        .distinctUntilChanged()

    val theme: kotlinx.coroutines.flow.Flow<String> = dataStore.data
        .map { it[themeKey] ?: "system" }
        .distinctUntilChanged()

    val notificationsEnabled: kotlinx.coroutines.flow.Flow<Boolean> = dataStore.data
        .map { it[notificationsKey] ?: true }
        .distinctUntilChanged()

    val userProfile: kotlinx.coroutines.flow.Flow<UserProfile> = dataStore.data
        .map { prefs ->
            UserProfile(
                weightKg = prefs.readWeightKg(),
                heightCm = prefs.readHeightCm(),
                ageYears = prefs.readAgeYears(),
                isMale = prefs[isMaleKey] ?: defaultProfile.isMale,
            )
        }
        .distinctUntilChanged()

    val spotifyAccessToken: kotlinx.coroutines.flow.Flow<String> = dataStore.data
        .map { it[spotifyAccessTokenKey] ?: "" }
        .distinctUntilChanged()

    val spotifyRefreshToken: kotlinx.coroutines.flow.Flow<String> = dataStore.data
        .map { it[spotifyRefreshTokenKey] ?: "" }
        .distinctUntilChanged()

    val spotifyTokenExpiresAtMs: kotlinx.coroutines.flow.Flow<Long> = dataStore.data
        .map { it[spotifyTokenExpiresAtKey] ?: 0L }
        .distinctUntilChanged()

    val spotifyLoggedIn: kotlinx.coroutines.flow.Flow<Boolean> = dataStore.data
        .map { it[spotifyLoggedInKey] ?: false }
        .distinctUntilChanged()

    val spotifyPlaylistUri: kotlinx.coroutines.flow.Flow<String?> = dataStore.data
        .map { it[spotifyPlaylistUriKey] }
        .distinctUntilChanged()

    val spotifyPlaylistName: kotlinx.coroutines.flow.Flow<String?> = dataStore.data
        .map { it[spotifyPlaylistNameKey] }
        .distinctUntilChanged()

    val savedWorkoutPlans: kotlinx.coroutines.flow.Flow<List<WorkoutPlan>> = dataStore.data
        .map { prefs ->
            val json = prefs[workoutPlansKey] ?: "[]"
            runCatching {
                Json.decodeFromString<List<WorkoutPlan>>(json)
            }.getOrDefault(emptyList())
        }
        .distinctUntilChanged()

    suspend fun saveWorkoutPlan(plan: WorkoutPlan) {
        dataStore.edit { prefs ->
            val json = prefs[workoutPlansKey] ?: "[]"
            val current = runCatching {
                Json.decodeFromString<List<WorkoutPlan>>(json)
            }.getOrDefault(emptyList())
            val updated = current.filterNot { it.id == plan.id } + plan
            prefs[workoutPlansKey] = Json.encodeToString(updated)
        }
    }

    suspend fun deleteWorkoutPlan(planId: String) {
        dataStore.edit { prefs ->
            val json = prefs[workoutPlansKey] ?: "[]"
            val current = runCatching {
                Json.decodeFromString<List<WorkoutPlan>>(json)
            }.getOrDefault(emptyList())
            val updated = current.filterNot { it.id == planId }
            prefs[workoutPlansKey] = Json.encodeToString(updated)
        }
    }

    suspend fun saveAuthToken(token: String) {
        dataStore.edit { it[tokenKey] = token }
    }

    suspend fun clearAuthToken() {
        dataStore.edit { it[tokenKey] = "" }
    }

    suspend fun saveTheme(theme: String) {
        dataStore.edit { it[themeKey] = theme }
    }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[notificationsKey] = enabled }
    }

    suspend fun updateWeightKg(kg: Double) {
        val safe = if (kg.isFinite() && kg > 0.0) kg else defaultProfile.weightKg
        dataStore.edit { it[weightKgKey] = safe }
    }

    suspend fun updateHeightCm(cm: Double) {
        val safe = if (cm.isFinite() && cm > 0.0) cm else defaultProfile.heightCm
        dataStore.edit { it[heightCmKey] = safe }
    }

    suspend fun updateAgeYears(years: Int) {
        val safe = years.coerceIn(MIN_AGE_YEARS, MAX_AGE_YEARS)
        dataStore.edit { it[ageYearsKey] = safe }
    }

    suspend fun updateGender(isMale: Boolean) {
        dataStore.edit { it[isMaleKey] = isMale }
    }

    suspend fun saveSpotifyTokens(
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
    ) {
        dataStore.edit {
            it[spotifyAccessTokenKey] = accessToken
            it[spotifyRefreshTokenKey] = refreshToken ?: ""
            it[spotifyTokenExpiresAtKey] = expiresAtMs
        }
    }

    suspend fun clearSpotifyTokens() {
        dataStore.edit {
            it[spotifyAccessTokenKey] = ""
            it[spotifyRefreshTokenKey] = ""
            it[spotifyTokenExpiresAtKey] = 0L
            it[spotifyLoggedInKey] = false
        }
    }

    suspend fun setSpotifyLoggedIn(loggedIn: Boolean) {
        dataStore.edit { it[spotifyLoggedInKey] = loggedIn }
    }

    suspend fun setSpotifyPlaylist(uri: String, name: String) {
        dataStore.edit {
            it[spotifyPlaylistUriKey] = uri
            it[spotifyPlaylistNameKey] = name
        }
    }

    private fun Preferences.readWeightKg(): Double =
        this[weightKgKey]?.takeIf { it.isFinite() && it > 0.0 } ?: defaultProfile.weightKg

    private fun Preferences.readHeightCm(): Double =
        this[heightCmKey]?.takeIf { it.isFinite() && it > 0.0 } ?: defaultProfile.heightCm

    private fun Preferences.readAgeYears(): Int =
        this[ageYearsKey]?.takeIf { it in MIN_AGE_YEARS..MAX_AGE_YEARS } ?: defaultProfile.ageYears
}
