package com.daytoday.ui.screen.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.data.notification.WorkoutReminderReceiver
import com.daytoday.data.spotify.SpotifyLoginResult
import com.daytoday.data.spotify.SpotifyPlayResult
import com.daytoday.data.spotify.SpotifyPlaylist
import com.daytoday.data.spotify.SpotifyPlaylistsResult
import com.daytoday.data.spotify.SpotifyRepository
import com.daytoday.model.UserProfile
import com.daytoday.settings.SettingsManager
import com.daytoday.usecase.UserUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userUseCases: UserUseCases,
    private val settingsManager: SettingsManager,
    private val application: Application,
    private val spotifyRepository: SpotifyRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _pushEnabled = MutableStateFlow(true)
    val pushEnabled: StateFlow<Boolean> = _pushEnabled.asStateFlow()

    private val _workoutReminders = MutableStateFlow(true)
    val workoutReminders: StateFlow<Boolean> = _workoutReminders.asStateFlow()

    private val _reminderMinutes = MutableStateFlow(18 * 60)
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    private val _nbaAlerts = MutableStateFlow(true)
    val nbaAlerts: StateFlow<Boolean> = _nbaAlerts.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    val spotifyLoggedIn: StateFlow<Boolean> = settingsManager.spotifyLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val spotifyPlaylistName: StateFlow<String?> = settingsManager.spotifyPlaylistName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists.asStateFlow()

    private val _spotifyPlaylistsLoading = MutableStateFlow(false)
    val spotifyPlaylistsLoading: StateFlow<Boolean> = _spotifyPlaylistsLoading.asStateFlow()

    private val _spotifyMessage = MutableStateFlow<String?>(null)
    val spotifyMessage: StateFlow<String?> = _spotifyMessage.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsManager.theme
                .map { theme -> theme == "dark" }
                .distinctUntilChanged()
                .collect { dark -> _isDarkMode.value = dark }
        }

        viewModelScope.launch {
            settingsManager.notificationsEnabled
                .distinctUntilChanged()
                .collect { enabled -> _pushEnabled.value = enabled }
        }

        viewModelScope.launch {
            settingsManager.userProfile
                .distinctUntilChanged()
                .collect { profile -> _userProfile.value = profile }
        }

        viewModelScope.launch {
            settingsManager.workoutReminderMinutes
                .distinctUntilChanged()
                .collect { minutes -> _reminderMinutes.value = minutes }
        }

        viewModelScope.launch {
            settingsManager.spotifyLoginMessage
                .filterNotNull()
                .collect { message ->
                    _spotifyMessage.value = message
                    settingsManager.clearSpotifyLoginMessage()
                }
        }

        _workoutReminders.value = getWorkoutRemindersPref()
        _nbaAlerts.value = getNbaAlertsPref()
    }

    fun updateWeightKg(kg: Double) {
        viewModelScope.launch {
            settingsManager.updateWeightKg(kg)
        }
    }

    fun updateHeightCm(cm: Double) {
        viewModelScope.launch {
            settingsManager.updateHeightCm(cm)
        }
    }

    fun updateAgeYears(years: Int) {
        viewModelScope.launch {
            settingsManager.updateAgeYears(years)
        }
    }

    fun updateGender(isMale: Boolean) {
        viewModelScope.launch {
            settingsManager.updateGender(isMale)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveTheme(if (enabled) "dark" else "light")
        }
    }

    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveNotificationsEnabled(enabled)
        }
    }

    fun setWorkoutReminders(enabled: Boolean) {
        _workoutReminders.value = enabled
        saveWorkoutRemindersPref(enabled)
        if (enabled) {
            WorkoutReminderReceiver.schedule(
                context,
                _reminderMinutes.value / 60,
                _reminderMinutes.value % 60
            )
        } else {
            WorkoutReminderReceiver.cancel(context)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val minutes = hour * 60 + minute
        _reminderMinutes.value = minutes
        viewModelScope.launch {
            settingsManager.setWorkoutReminderMinutes(minutes)
        }
        saveReminderTimePref(minutes)
        if (_workoutReminders.value) {
            WorkoutReminderReceiver.schedule(context, hour, minute)
        }
    }

    fun setNbaAlerts(enabled: Boolean) {
        _nbaAlerts.value = enabled
        saveNbaAlertsPref(enabled)
    }

    fun loginSpotify() {
        viewModelScope.launch {
            val result = spotifyRepository.beginLogin(context)
            if (result is SpotifyLoginResult.ERROR) {
                _spotifyMessage.value = result.message
            }
        }
    }

    fun logoutSpotify() {
        viewModelScope.launch {
            spotifyRepository.logout()
            settingsManager.clearPendingLogin()
            settingsManager.clearSpotifyLoginMessage()
            _spotifyPlaylists.value = emptyList()
            _spotifyMessage.value = "Disconnected from Spotify"
        }
    }

    fun loadSpotifyPlaylists() {
        viewModelScope.launch {
            _spotifyPlaylistsLoading.value = true
            when (val result = spotifyRepository.myPlaylists()) {
                is SpotifyPlaylistsResult.SUCCESS -> {
                    _spotifyPlaylists.value = result.playlists
                    _spotifyPlaylistsLoading.value = false
                    if (result.playlists.isEmpty()) {
                        _spotifyMessage.value = "No playlists found in your Spotify account"
                    }
                }
                SpotifyPlaylistsResult.NOT_LOGGED_IN -> {
                    _spotifyPlaylistsLoading.value = false
                    _spotifyMessage.value = "Log in to Spotify first"
                }
                is SpotifyPlaylistsResult.ERROR -> {
                    _spotifyPlaylistsLoading.value = false
                    _spotifyMessage.value = result.message
                }
            }
        }
    }

    fun selectSpotifyPlaylist(uri: String, name: String) {
        viewModelScope.launch {
            spotifyRepository.selectPlaylist(uri, name)
            _spotifyPlaylists.value = emptyList()
            _spotifyMessage.value = "Selected playlist: $name"
        }
    }

    fun playSpotify() {
        viewModelScope.launch {
            when (val result = spotifyRepository.playPlaylist(context)) {
                SpotifyPlayResult.PLAYING -> {
                    _spotifyMessage.value = "Playing on Spotify"
                }
                SpotifyPlayResult.NOT_LOGGED_IN -> {
                    _spotifyMessage.value = "Log in to Spotify first"
                }
                is SpotifyPlayResult.NO_DEVICE -> {
                    _spotifyMessage.value = "Opening Spotify app — tap play to start"
                }
                is SpotifyPlayResult.ERROR -> {
                    _spotifyMessage.value = result.message
                }
            }
        }
    }

    fun dismissSpotifyPlaylists() {
        _spotifyPlaylists.value = emptyList()
    }

    fun clearSpotifyMessage() {
        _spotifyMessage.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            userUseCases.logout()
            settingsManager.clearAuthToken()
        }
    }

    fun clearCache() {
        // TODO: Implement cache clearing
    }

    fun exportData() {
        // TODO: Implement data export
    }

    fun deleteAccount() {
        // TODO: Implement account deletion
    }

    private fun getWorkoutRemindersPref(): Boolean {
        return application.getSharedPreferences("settings", 0)
            .getBoolean("workout_reminders", true)
    }

    private fun saveWorkoutRemindersPref(enabled: Boolean) {
        application.getSharedPreferences("settings", 0)
            .edit().putBoolean("workout_reminders", enabled).apply()
    }

    /**
     * Mirror of the DataStore value in the same prefs file as [saveWorkoutRemindersPref],
     * so WorkoutBootReceiver can reschedule after a reboot without Hilt or DataStore.
     */
    private fun saveReminderTimePref(minutes: Int) {
        application.getSharedPreferences("settings", 0)
            .edit().putInt("workout_reminder_minutes", minutes).apply()
    }

    private fun getNbaAlertsPref(): Boolean {
        return application.getSharedPreferences("settings", 0)
            .getBoolean("nba_alerts", true)
    }

    private fun saveNbaAlertsPref(enabled: Boolean) {
        application.getSharedPreferences("settings", 0)
            .edit().putBoolean("nba_alerts", enabled).apply()
    }
}