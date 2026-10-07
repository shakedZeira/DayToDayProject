package com.daytoday.ui.screen.goals

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

private const val MAX_CALORIES_GOAL = 10000
private const val MAX_WEEKLY_WORKOUT_GOAL = 14

@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Goals",
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoalsScreenContent(viewModel = viewModel)
        }
    }
}

@Composable
private fun GoalsScreenContent(
    viewModel: GoalsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            UiState.Loading -> {
                LoadingOverlay("Loading goals...")
            }
            is UiState.Error -> {
                ErrorState(state.message, viewModel::load)
            }
            is UiState.Success -> {
                GoalsContent(state = state.data, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun GoalsContent(
    state: GoalsUiState,
    viewModel: GoalsViewModel
) {
    var showCaloriesDialog by remember { mutableStateOf(false) }
    var showWorkoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        GoalCard(
            title = "Calories Burned",
            icon = Icons.Default.LocalFireDepartment,
            current = state.caloriesToday,
            goal = state.caloriesGoal,
            unit = "kcal",
            emptyHint = "No calories burned yet today",
            onEdit = { showCaloriesDialog = true }
        )

        Text("This Week", style = MaterialTheme.typography.titleLarge)
        GoalCard(
            title = "Workout Sessions",
            icon = Icons.Default.FitnessCenter,
            current = state.workoutsThisWeek,
            goal = state.weeklyWorkoutGoal,
            unit = "sessions",
            emptyHint = "No workouts yet this week",
            onEdit = { showWorkoutDialog = true }
        )
    }

    if (showCaloriesDialog) {
        EditGoalDialog(
            title = "Daily Calories Goal",
            initialValue = state.caloriesGoal,
            maxValue = MAX_CALORIES_GOAL,
            onDismiss = { showCaloriesDialog = false },
            onConfirm = { value ->
                showCaloriesDialog = false
                viewModel.updateCaloriesGoal(value)
            }
        )
    }

    if (showWorkoutDialog) {
        EditGoalDialog(
            title = "Weekly Workout Goal",
            initialValue = state.weeklyWorkoutGoal,
            maxValue = MAX_WEEKLY_WORKOUT_GOAL,
            onDismiss = { showWorkoutDialog = false },
            onConfirm = { value ->
                showWorkoutDialog = false
                viewModel.updateWeeklyWorkoutGoal(value)
            }
        )
    }
}

@Composable
private fun GoalCard(
    title: String,
    icon: ImageVector,
    current: Int,
    goal: Int,
    unit: String,
    emptyHint: String,
    onEdit: () -> Unit
) {
    val safeGoal = goal.coerceAtLeast(1)
    val fraction = (current.toFloat() / safeGoal).coerceIn(0f, 1f)
    val met = goal > 0 && current >= goal

    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit $title",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$current / $goal $unit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (met) {
                    Text(
                        text = if (current > goal) {
                            "Over by ${current - goal} $unit"
                        } else {
                            "Completed"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            if (current <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditGoalDialog(
    title: String,
    initialValue: Int,
    maxValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1..maxValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText.filter { it.isDigit() }
                },
                label = { Text("Goal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = !valid,
                supportingText = {
                    if (!valid) {
                        Text("Enter a number between 1 and $maxValue")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
