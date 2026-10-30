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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import com.daytoday.ui.theme.DayTodayTextField
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
    private val settingsManager: SettingsManager,
    private val localServer: LocalServerManager,
    private val sessionProvisioner: SessionProvisioner,
) : ViewModel() {
    val theme: StateFlow<String> = settingsManager.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    val serverBaseUrl: StateFlow<String> = settingsManager.serverBaseUrl
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsManager.defaultServerBaseUrl
        )

    private val _serverUrlError = MutableStateFlow<String?>(null)
    val serverUrlError: StateFlow<String?> = _serverUrlError

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

    fun saveServerBaseUrl(raw: String) {
        viewModelScope.launch {
            val saved = settingsManager.setServerBaseUrl(raw)
            _serverUrlError.value = if (saved == null) {
                "Enter a URL starting with http:// or https://"
            } else {
                null
            }
            if (saved != null) retryServer()
        }
    }

    fun clearServerUrlError() {
        _serverUrlError.value = null
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
            val serverBaseUrl by viewModel.serverBaseUrl.collectAsStateWithLifecycle()
            val serverUrlError by viewModel.serverUrlError.collectAsStateWithLifecycle()
            val useDarkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DayTodayTheme(darkTheme = useDarkTheme) {
                if (startup != StartupState.Ready) {
                    StartupGate(
                        state = startup,
                        onRetry = { viewModel.retryServer() },
                        serverBaseUrl = serverBaseUrl,
                        serverUrlError = serverUrlError,
                        onSaveServerUrl = viewModel::saveServerBaseUrl,
                        onClearServerUrlError = viewModel::clearServerUrlError,
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
private fun StartupGate(
    state: StartupState,
    onRetry: () -> Unit,
    serverBaseUrl: String,
    serverUrlError: String?,
    onSaveServerUrl: (String) -> Unit,
    onClearServerUrlError: () -> Unit,
) {
    var showServerDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            if (state == StartupState.Failed) {
                Text(text = "Couldn't reach the server.", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Start the backend, or set its address for this phone. " +
                        "Current: $serverBaseUrl",
                    style = MaterialTheme.typography.bodyMedium
                )
                serverUrlError?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = { showServerDialog = true }) {
                        Text("Server URL")
                    }
                }
            } else {
                CircularProgressIndicator()
                val label = when (state) {
                    StartupState.SigningIn -> "Signing in…"
                    else -> "Connecting to the server…"
                }
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showServerDialog) {
        var draft by remember(serverBaseUrl) { mutableStateOf(serverBaseUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Server URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTodayTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            onClearServerUrlError()
                        },
                        label = "API base URL",
                        placeholder = "http://192.168.1.20:4000/",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = serverUrlError != null,
                        supportingText = serverUrlError
                            ?: "Emulator: http://10.0.2.2:4000/ — Phone: your PC's LAN IP."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveServerUrl(draft)
                    showServerDialog = false
                }) {
                    Text("Save & retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) { Text("Cancel") }
            }
        )
    }
}
