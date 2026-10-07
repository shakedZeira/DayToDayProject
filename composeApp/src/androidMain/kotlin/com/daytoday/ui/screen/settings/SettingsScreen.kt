package com.daytoday.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.daytoday.data.spotify.SpotifyPlaylist
import com.daytoday.model.UserProfile
import com.daytoday.ui.theme.DayTodayTextField
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.ButtonType
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val pushEnabled by viewModel.pushEnabled.collectAsStateWithLifecycle()
    val workoutReminders by viewModel.workoutReminders.collectAsStateWithLifecycle()
    val nbaAlerts by viewModel.nbaAlerts.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val spotifyLoggedIn by viewModel.spotifyLoggedIn.collectAsStateWithLifecycle()
    val spotifyPlaylistName by viewModel.spotifyPlaylistName.collectAsStateWithLifecycle()
    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsStateWithLifecycle()
    val spotifyPlaylistsLoading by viewModel.spotifyPlaylistsLoading.collectAsStateWithLifecycle()
    val spotifyMessage by viewModel.spotifyMessage.collectAsStateWithLifecycle()
    val reminderMinutes by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val reminderHour = reminderMinutes / 60
    val reminderMinute = reminderMinutes % 60
    val context = LocalContext.current
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showReminderTimeDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setWorkoutReminders(granted)
    }

    Scaffold(topBar = { DayTodayTopAppBar(title = "Settings") }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val profileExpanded = remember { mutableStateOf(false) }
            fun toggleProfile() { profileExpanded.value = !profileExpanded.value }

            SettingsSection("Account") {
                SettingsRow(
                    title = "Profile",
                    icon = Icons.Default.Person,
                    subtitle = "Weight, height, age, gender",
                    onClick = { toggleProfile() }
                )
                SettingsRow(
                    title = "Sign Out",
                    icon = Icons.Default.Logout,
                    onClick = { viewModel.signOut() },
                    isDestructive = true
                )
            }

            if (profileExpanded.value) {
                BodyProfileCard(
                    profile = userProfile,
                    onWeightChange = viewModel::updateWeightKg,
                    onHeightChange = viewModel::updateHeightCm,
                    onAgeChange = viewModel::updateAgeYears,
                    onGenderChange = viewModel::updateGender
                )
            }

            SettingsSection("Appearance") {
                SettingsSwitch(
                    title = "Dark Mode",
                    icon = Icons.Default.DarkMode,
                    checked = isDarkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                SettingsRow(
                    title = "Theme",
                    icon = Icons.Default.Palette,
                    onClick = { /* navigate to theme picker */ }
                )
            }

            SettingsSection("Notifications") {
                SettingsSwitch(
                    title = "Push Notifications",
                    icon = Icons.Default.Notifications,
                    checked = pushEnabled,
                    onCheckedChange = viewModel::setPushEnabled
                )
                SettingsSwitch(
                    title = "Workout Reminders",
                    icon = Icons.Default.Schedule,
                    checked = workoutReminders,
                    onCheckedChange = { enabled ->
                        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        if (enabled && !canNotify) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            viewModel.setWorkoutReminders(enabled)
                        }
                    }
                )
                SettingsRow(
                    title = "Reminder time",
                    icon = Icons.Default.AccessTime,
                    subtitle = String.format("%02d:%02d", reminderHour, reminderMinute),
                    onClick = { showReminderTimeDialog = true }
                )
                SettingsSwitch(
                    title = "NBA Game Alerts",
                    icon = Icons.Default.SportsBasketball,
                    checked = nbaAlerts,
                    onCheckedChange = viewModel::setNbaAlerts
                )
            }

            if (showReminderTimeDialog) {
                val timePickerState = rememberTimePickerState(
                    initialHour = reminderHour,
                    initialMinute = reminderMinute,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showReminderTimeDialog = false },
                    title = { Text("Reminder time") },
                    text = {
                        TimePicker(state = timePickerState)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setReminderTime(
                                timePickerState.hour,
                                timePickerState.minute
                            )
                            showReminderTimeDialog = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReminderTimeDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            SettingsSection("Spotify Integration", icon = Icons.Default.MusicNote) {
                if (spotifyLoggedIn) {
                    // Connected status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Connected as Spotify User",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }
                    }

                    // Selected playlist
                    spotifyPlaylistName?.let { playlistName ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Workout Playlist: $playlistName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Action buttons
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DayTodayButton(
                                text = "Change Playlist",
                                onClick = {
                                    viewModel.loadSpotifyPlaylists()
                                    showPlaylistsDialog = true
                                },
                                buttonType = ButtonType.Outlined,
                                modifier = Modifier.weight(1f)
                            )
                            DayTodayButton(
                                text = "Test Playback",
                                onClick = { viewModel.playSpotify() },
                                buttonType = ButtonType.Filled,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DayTodayButton(
                            text = "Disconnect",
                            onClick = { viewModel.logoutSpotify() },
                            buttonType = ButtonType.Outlined,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Not connected state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            Text(
                                "Not Connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DayTodayButton(
                        text = "Connect Spotify",
                        onClick = { viewModel.loginSpotify() },
                        buttonType = ButtonType.Filled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Message display
                spotifyMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Playlists dialog
                if (showPlaylistsDialog) {
                    val playlistsToShow = spotifyPlaylists
                    if (playlistsToShow.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissSpotifyPlaylists(); showPlaylistsDialog = false },
                            title = { Text("Select Workout Playlist") },
                            text = {
                                if (spotifyPlaylistsLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        items(playlistsToShow) { playlist ->
                                            TextButton(
                                                onClick = {
                                                    viewModel.selectSpotifyPlaylist(playlist.uri, playlist.name)
                                                    showPlaylistsDialog = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    playlist.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(
                                    onClick = { viewModel.dismissSpotifyPlaylists(); showPlaylistsDialog = false }
                                ) { Text("Cancel") }
                            }
                        )
                    }
                }
            }

            SettingsSection("Data & Privacy") {
                SettingsRow(
                    title = "Clear Cache",
                    icon = Icons.Default.Delete,
                    onClick = { viewModel.clearCache() }
                )
                SettingsRow(
                    title = "Export Data",
                    icon = Icons.Default.Download,
                    onClick = { viewModel.exportData() }
                )
                SettingsRow(
                    title = "Delete Account",
                    icon = Icons.Default.DeleteForever,
                    onClick = { viewModel.deleteAccount() },
                    isDestructive = true
                )
            }

            SettingsSection("About") {
                SettingsRow(
                    title = "Version 1.0.0",
                    icon = Icons.Default.Info,
                    onClick = { }
                )
                SettingsRow(
                    title = "Privacy Policy",
                    icon = Icons.Default.Policy,
                    onClick = { }
                )
                SettingsRow(
                    title = "Terms of Service",
                    icon = Icons.Default.Description,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
fun BodyProfileCard(
    profile: UserProfile,
    onWeightChange: (Double) -> Unit,
    onHeightChange: (Double) -> Unit,
    onAgeChange: (Int) -> Unit,
    onGenderChange: (Boolean) -> Unit
) {
    // Read directly from the profile flow value so the fields always reflect the
    // latest saved data (e.g. after the user types and the ViewModel writes to
    // DataStore, the next recomposition pulls the persisted value).
    val weightText = remember(profile.weightKg) { mutableStateOf(profile.weightKg.toString()) }
    val heightText = remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    val ageText = remember(profile.ageYears) { mutableStateOf(profile.ageYears.toString()) }
    val isMale = remember(profile.isMale) { mutableStateOf(profile.isMale) }

    val weightKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    val heightKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    val ageKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DayTodayTextField(
                value = weightText.value,
                onValueChange = { newText ->
                    weightText.value = newText
                    newText.toDoubleOrNull()?.let { onWeightChange(it) }
                },
                label = "Weight (kg)",
                placeholder = "70.0",
                keyboardOptions = weightKeyboardOptions,
                leadingIcon = { Icon(
                    imageVector = Icons.Default.MonitorWeight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            DayTodayTextField(
                value = heightText.value,
                onValueChange = { newText ->
                    heightText.value = newText
                    newText.toDoubleOrNull()?.let { onHeightChange(it) }
                },
                label = "Height (cm)",
                placeholder = "175",
                keyboardOptions = heightKeyboardOptions,
                leadingIcon = { Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DayTodayTextField(
                value = ageText.value,
                onValueChange = { newText ->
                    ageText.value = newText
                    newText.toIntOrNull()?.let { onAgeChange(it) }
                },
                label = "Age (years)",
                placeholder = "30",
                keyboardOptions = ageKeyboardOptions,
                leadingIcon = { Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = if (isMale.value) Icons.Default.Male else Icons.Default.Female,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = if (isMale.value) "Male" else "Female",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Switch(
                    checked = isMale.value,
                    onCheckedChange = { newValue ->
                        isMale.value = newValue
                        onGenderChange(newValue)
                    },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}