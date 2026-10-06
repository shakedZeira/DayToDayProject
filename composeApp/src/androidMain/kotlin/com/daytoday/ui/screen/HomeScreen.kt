package com.daytoday.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.util.CalorieCalculator
import com.daytoday.util.formatCalories
import com.daytoday.util.formatDuration
import com.daytoday.util.formatSteps
import com.daytoday.util.formatVolume
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val localSummaryUseCases: LocalSummaryUseCases
) : ViewModel() {

    val healthPermissionGranted: StateFlow<Boolean> = localSummaryUseCases.healthPermissionGranted

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        load()
    }

    internal fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val granted = localSummaryUseCases.refreshHealthPermission()
                val stats = localSummaryUseCases.todayStats()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stats = stats,
                    healthPermissionGranted = granted,
                    healthSdkAvailable = true,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load your stats"
                )
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            // Only re-check permission state; the user may have granted/revoked it while
            // the app was in the background (e.g. via system Settings).
            val granted = localSummaryUseCases.refreshHealthPermission()
            if (granted) {
                // Permission was already granted before this resume; refresh stats too.
                val stats = localSummaryUseCases.todayStats()
                _uiState.value = _uiState.value.copy(stats = stats, healthPermissionGranted = true)
            } else if (_uiState.value.healthPermissionGranted) {
                // Permission was previously granted but now missing — clear stats so the
                // connect prompt reappears.
                _uiState.value = _uiState.value.copy(healthPermissionGranted = false, stats = null)
            }
        }
    }

    fun requestPermission() {
        viewModelScope.launch {
            val granted = localSummaryUseCases.requestHealthPermission()
            if (granted) {
                val stats = localSummaryUseCases.todayStats()
                _uiState.value = _uiState.value.copy(stats = stats, healthPermissionGranted = true)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            localSummaryUseCases.setHealthPermissionGranted(granted)
            if (granted) {
                val stats = localSummaryUseCases.todayStats()
                _uiState.value = _uiState.value.copy(stats = stats, healthPermissionGranted = true)
            }
        }
    }

    fun checkSdkStatus() {
        viewModelScope.launch {
            try {
                val status = localSummaryUseCases.healthSdkStatus()
                _uiState.value = _uiState.value.copy(healthSdkAvailable = status != com.daytoday.data.health.HealthSdkStatus.MISSING)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(healthSdkAvailable = false)
            }
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val stats: LocalDayStats? = null,
    val healthPermissionGranted: Boolean = false,
    val healthSdkAvailable: Boolean = true,
    val errorMessage: String? = null
)

@Composable
fun HomeScreen(
    navController: NavController = rememberNavController(),
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.healthPermissionGranted.collectAsStateWithLifecycle(false)

    LaunchedEffect(Unit) {
        viewModel.checkSdkStatus()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectivePermissionGranted = permissionGranted || uiState.healthPermissionGranted

    val healthConnectPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val hasSteps = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        viewModel.onPermissionResult(hasSteps)
    }

    Scaffold(topBar = { DayTodayTopAppBar(title = "DayToDay") }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.stats == null -> {
                    LoadingOverlay(message = "Loading your day...")
                }
                uiState.errorMessage != null && uiState.stats == null -> {
                    ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.load() })
                }
                else -> {
                    HomeContent(
                        uiState = uiState,
                        permissionGranted = effectivePermissionGranted,
                        onRequestPermission = {
                            try {
                                permissionLauncher.launch(healthConnectPermissions)
                            } catch (e: Exception) {
                                viewModel.requestPermission()
                            }
                        },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val stats = uiState.stats ?: LocalDayStats()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome back!", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Here's your daily overview",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!uiState.healthSdkAvailable) {
            DayTodayCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Health Connect",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Health Connect not installed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Install Health Connect from the Play Store to track your steps automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (!permissionGranted) {
            DayTodayCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = "Steps",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Connect Health Connect to see your steps",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Your step count and step calories will appear here once permission is granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    DayTodayButton(
                        text = "Connect Health Connect",
                        onClick = onRequestPermission,
                        buttonType = ButtonType.Filled
                    )
                }
            }
        } else {
            val primaryStatColor = MaterialTheme.colorScheme.primary
            val secondaryStatColor = MaterialTheme.colorScheme.secondary
            val tertiaryStatColor = MaterialTheme.colorScheme.tertiary
            val calorieStatColor = Color(0xFFEF4444)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    listOf(
                        QuickStat(
                            "Steps",
                            formatSteps(stats.steps.toInt()),
                            Icons.Default.DirectionsWalk,
                            tertiaryStatColor
                        ),
                        QuickStat(
                            "Steps Calories",
                            formatCalories(stats.stepsCalories),
                            Icons.Default.LocalFireDepartment,
                            secondaryStatColor
                        ),
                        QuickStat(
                            "Workout Calories",
                            formatCalories(stats.workoutCalories),
                            Icons.Default.FitnessCenter,
                            primaryStatColor
                        ),
                        QuickStat(
                            "Total Calories",
                            formatCalories(stats.totalCalories),
                            Icons.Default.LocalFireDepartment,
                            calorieStatColor
                        )
                    )
                ) { stat ->
                    DayTodayCard(modifier = Modifier.fillMaxWidth()) { QuickStatCard(stat) }
                }
            }

            if (stats.calorieGoal > 0) {
                val progress = CalorieCalculator.progressFraction(stats.totalCalories.toDouble(), stats.calorieGoal)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Daily Burn Goal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${formatCalories(stats.totalCalories)} / ${formatCalories(stats.calorieGoal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }

            if (stats.completedSessions > 0 || stats.durationMinutes > 0) {
                val sessionColor = MaterialTheme.colorScheme.primary
                val durationColor = MaterialTheme.colorScheme.secondary
                val volumeColor = MaterialTheme.colorScheme.tertiary
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        listOf(
                            QuickStat(
                                "Sessions",
                                stats.completedSessions.toString(),
                                Icons.Default.FitnessCenter,
                                sessionColor
                            ),
                            QuickStat(
                                "Duration",
                                formatDuration(stats.durationMinutes.toLong() * 60_000),
                                Icons.Default.TrendingUp,
                                durationColor
                            ),
                            QuickStat(
                                "Volume",
                                formatVolume(stats.totalVolume, 0),
                                Icons.Default.Article,
                                volumeColor
                            )
                        )
                    ) { stat ->
                        DayTodayCard(modifier = Modifier.fillMaxWidth()) { QuickStatCard(stat) }
                    }
                }
            }
            DayTodayCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.WorkoutProgress.route) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Workout Progress & History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "View volume stats, personal bests & workout trends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Open Workout Progress",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("Quick Actions", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTodayButton(
                text = "NBA Scores",
                onClick = { onNavigate(Screen.NbaScoreboard.route) },
                modifier = Modifier.weight(1f)
            )
            DayTodayButton(
                text = "Build Workout",
                onClick = { onNavigate(Screen.WorkoutActive.route) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTodayButton(
                text = "Merge PDFs",
                onClick = { onNavigate(Screen.PdfMerge.route) },
                modifier = Modifier.weight(1f)
            )
            DayTodayButton(
                text = "Workout Progress",
                onClick = { onNavigate(Screen.WorkoutProgress.route) },
                buttonType = ButtonType.Filled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

data class QuickStat(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun QuickStatCard(stat: QuickStat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = stat.icon,
            contentDescription = stat.title,
            modifier = Modifier.size(32.dp),
            tint = stat.color
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stat.value,
            style = MaterialTheme.typography.headlineMedium,
            color = stat.color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stat.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
fun HomeScreenPreview() {
    com.daytoday.ui.theme.DayTodayTheme {
        HomeScreen(navController = rememberNavController())
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
fun HomeScreenPreviewDark() {
    com.daytoday.ui.theme.DayTodayTheme(darkTheme = true) {
        HomeScreen(navController = rememberNavController())
    }
}