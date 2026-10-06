package com.daytoday.ui.screen.workout

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.ui.theme.ErrorState
import com.daytoday.model.DailyProgress
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.util.formatDateTime
import com.daytoday.util.formatNumber

@Composable
fun WorkoutProgressScreen(
    viewModel: WorkoutProgressViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Workout Progress",
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            WorkoutProgressScreenContent(viewModel = viewModel)
        }
    }
}

@Composable
fun WorkoutProgressScreenContent(
    viewModel: WorkoutProgressViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val dailyProgress by viewModel.dailyProgress.collectAsStateWithLifecycle()
    val personalBests by viewModel.personalBests.collectAsStateWithLifecycle()
    val weeklyProgress by viewModel.weeklyProgress.collectAsStateWithLifecycle()

    val dailyError = (dailyProgress as? UiState.Error)?.message
    val bestsError = (personalBests as? UiState.Error)?.message
    val weeklyError = (weeklyProgress as? UiState.Error)?.message
    val isLoading = dailyProgress is UiState.Loading ||
        personalBests is UiState.Loading ||
        weeklyProgress is UiState.Loading

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                LoadingOverlay("Loading progress...")
            }
            dailyError != null -> {
                ErrorState(dailyError, viewModel::loadAll)
            }
            bestsError != null -> {
                ErrorState(bestsError, viewModel::loadAll)
            }
            weeklyError != null -> {
                ErrorState(weeklyError, viewModel::loadAll)
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Daily Progress Card
                    (dailyProgress as? UiState.Success<DailyProgress>)?.data?.let { progress ->
                        DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Today's Progress", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProgressStat("Workouts", progress.workoutsCompleted.toString(), Icons.Default.FitnessCenter)
                                    ProgressStat("Duration", "${progress.totalDurationMinutes} min", Icons.Default.AccessTime)
                                    ProgressStat("Calories", progress.caloriesBurned.toString(), Icons.Default.LocalFireDepartment)
                                    ProgressStat("Steps", formatNumber(progress.steps.toDouble()), Icons.Default.DirectionsWalk)
                                }
                            }
                        }
                    }

                    // Personal Bests
                    (personalBests as? UiState.Success<List<PersonalBests>>)?.data?.let { bests ->
                        if (bests.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Personal Bests", style = MaterialTheme.typography.titleLarge)
                                bests.forEach { best ->
                                    PersonalBestCard(best = best)
                                }
                            }
                        }
                    }

                    // Weekly Progress
                    (weeklyProgress as? UiState.Success<WeeklyProgress>)?.data?.let { weekly ->
                        DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("This Week", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ProgressStat("Workouts", weekly.workouts.toString(), Icons.Default.FitnessCenter)
                                    ProgressStat("Duration", "${weekly.durationMinutes} min", Icons.Default.AccessTime)
                                    ProgressStat("Calories", weekly.caloriesBurned.toString(), Icons.Default.LocalFireDepartment)
                                    ProgressStat("Volume", "${formatNumber(weekly.volume)} kg", Icons.Default.TrendingUp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ProgressStat(label: String, value: String, icon: ImageVector) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonalBestCard(best: PersonalBests) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(best.exerciseName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Achieved ${formatDateTime(best.achievedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${formatNumber(best.bestWeight)} kg × ${best.bestReps}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "1RM: ${formatNumber(best.bestOneRM)} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}