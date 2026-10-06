package com.daytoday.ui.screen.workout

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ErrorState
import com.daytoday.model.WorkoutSession
import com.daytoday.util.formatDateTime

@Composable
fun WorkoutHistoryScreen(
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Workout History",
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = sessions) {
                UiState.Loading -> {
                    LoadingOverlay("Loading workouts...")
                }
                is UiState.Error -> {
                    ErrorState(state.message, viewModel::loadSessions)
                }
                is UiState.Success -> {
                    val sessionList = state.data
                    if (sessionList.isEmpty()) {
                        EmptyState(
                            icon = {
                                Icon(
                                    Icons.Default.FitnessCenter,
                                    "Workout",
                                    modifier = Modifier.size(64.dp)
                                )
                            },
                            title = "No workouts yet",
                            subtitle = "Start your first workout to see history here"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(sessionList) { session ->
                                WorkoutSessionCard(session = session)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutSessionCard(session: WorkoutSession) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(session.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDateTime(session.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("${session.sets.size} sets", style = MaterialTheme.typography.bodyMedium)
                Text("${session.durationMinutes} min", style = MaterialTheme.typography.bodyMedium)
                Text("${session.caloriesBurned} cal", style = MaterialTheme.typography.bodyMedium)
            }
            if (session.isCompleted) {
                Text(
                    "Completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}