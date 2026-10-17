package com.daytoday.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.daytoday.server.LocalServerManager
import com.daytoday.server.ServerStatus
import com.daytoday.server.SessionProvisioner
import com.daytoday.settings.SettingsManager
import com.daytoday.ui.navigation.DayTodayNavHost
import com.daytoday.ui.theme.DayTodayTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartupState { StartingServer, SigningIn, Ready, Failed }

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsManager: SettingsManager,
    private val localServer: LocalServerManager,
    private val sessionProvisioner: SessionProvisioner,
) : ViewModel() {
    val theme: StateFlow<String> = settingsManager.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    private val _startup = MutableStateFlow(StartupState.StartingServer)
    val startup: StateFlow<StartupState> = _startup

    init {
        viewModelScope.launch {
            localServer.status.collect { status ->
                when (status) {
                    ServerStatus.Failed -> _startup.value = StartupState.Failed
                    ServerStatus.Ready -> {
                        if (_startup.value != StartupState.Ready) {
                            _startup.value = StartupState.SigningIn
                            val provisioned = sessionProvisioner.ensure()
                            android.util.Log.i("LocalServer", "session provisioned: $provisioned")
                            _startup.value = if (provisioned) {
                                StartupState.Ready
                            } else {
                                StartupState.Failed
                            }
                        }
                    }
                    else -> {
                        if (_startup.value != StartupState.Ready) {
                            _startup.value = StartupState.StartingServer
                        }
                    }
                }
            }
        }
    }

    fun retryServer() {
        _startup.value = StartupState.StartingServer
        localServer.onAppForeground()
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.i("SummaryNotifications", "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val startup by viewModel.startup.collectAsStateWithLifecycle()
            val useDarkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DayTodayTheme(darkTheme = useDarkTheme) {
                if (startup != StartupState.Ready) {
                    StartupGate(
                        state = startup,
                        onRetry = { viewModel.retryServer() }
                    )
                } else {
                    val navController = rememberNavController()
                    Scaffold(bottomBar = { BottomNavHost(navController) }) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            DayTodayNavHost(navController)
                        }
                    }
                }
            }
        }
    }

    /**
     * Prompts for POST_NOTIFICATIONS on Android 13+ (needed by the daily/weekly
     * summary notifications). No-op when already granted; if the user denies,
     * the system returns false on subsequent launches without re-prompting.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun StartupGate(state: StartupState, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (state == StartupState.Failed) {
                Text(text = "Couldn't start the local server.", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Make sure Termux is installed and its setup has finished.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onRetry) { Text("Retry") }
            } else {
                CircularProgressIndicator()
                val label = when (state) {
                    StartupState.SigningIn -> "Signing in…"
                    else -> "Starting local server…"
                }
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
