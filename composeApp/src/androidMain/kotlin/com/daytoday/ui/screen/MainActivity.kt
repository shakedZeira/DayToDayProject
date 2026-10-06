package com.daytoday.ui.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.daytoday.settings.SettingsManager
import com.daytoday.ui.navigation.DayTodayNavHost
import com.daytoday.ui.theme.DayTodayTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsManager: SettingsManager
) : ViewModel() {
    val theme: StateFlow<String> = settingsManager.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val useDarkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DayTodayTheme(darkTheme = useDarkTheme) {
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