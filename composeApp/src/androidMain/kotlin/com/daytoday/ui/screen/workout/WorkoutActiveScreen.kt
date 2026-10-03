package com.daytoday.ui.screen.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.DayTodayTheme
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ButtonType
import com.daytoday.model.Exercise
import com.daytoday.model.SetRecord
import com.daytoday.model.WorkoutSession
import com.daytoday.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun WorkoutActiveScreen(
    viewModel: WorkoutActiveViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    val exercisesState by viewModel.exercises.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val building by viewModel.building.collectAsStateWithLifecycle()
    val builderSessions by viewModel.builderSessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = when {
                    activeSession != null -> "Active Workout"
                    building -> "Build Workout"
                    else -> "Select Exercise"
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    activeSession?.let { session ->
                        DayTodayButton(
                            onClick = { viewModel.completeWorkout() },
                            text = "Complete",
                            buttonType = ButtonType.Filled
                        )
                    }
                    if (building) {
                        DayTodayButton(
                            onClick = { viewModel.stopBuilding() },
                            text = "Done",
                            buttonType = ButtonType.Outlined
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val session = activeSession
            when {
                session != null -> {
                    ActiveWorkout(
                        session = session,
                        onAddSet = viewModel::addSet,
                        onWeightChange = viewModel::updateSetWeight,
                        onRepsChange = viewModel::updateSetReps,
                        onRpeChange = viewModel::updateSetRpe,
                        onCompleteSet = viewModel::completeSet,
                        onFinish = viewModel::completeWorkout
                    )
                }
                building -> {
                    WorkoutBuilder(
                        exercisesState = exercisesState,
                        builderSessions = builderSessions,
                        onExerciseClick = viewModel::addExerciseToBuilder,
                        onRemove = viewModel::removeFromBuilder,
                        onSave = viewModel::saveBuilderAsWorkoutPlan,
                        onStartExercise = viewModel::startWorkoutFromBuilder,
                        onRetry = viewModel::retryLoadExercises
                    )
                }
                else -> {
                    ExercisePicker(
                        exerciseState = exercisesState,
                        onExerciseClick = viewModel::startWorkout,
                        onRetry = viewModel::retryLoadExercises
                    )
                }
            }
        }
    }
}

@Composable
private fun ExercisePicker(
    exerciseState: UiState<List<Exercise>>,
    onExerciseClick: (Exercise) -> Unit,
    onRetry: () -> Unit
) {
    when (exerciseState) {
        UiState.Loading -> LoadingOverlay(message = "Loading exercises...")
        is UiState.Error -> ErrorState(
            message = friendlyExerciseError(exerciseState.message),
            onRetry = onRetry
        )
        is UiState.Success -> {
            val exerciseList = exerciseState.data
            if (exerciseList.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = "No exercises available",
                    subtitle = "Your exercise library is empty. Try again to load the default exercises.",
                    action = {
                        DayTodayButton(
                            onClick = onRetry,
                            text = "Reload",
                            buttonType = ButtonType.Tonal
                        )
                    }
                )
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Exercise", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap an exercise to start your workout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(exerciseList) { exercise ->
                            ExercisePickerCard(
                                exercise = exercise,
                                onClick = { onExerciseClick(exercise) }
                            )
                        }
                    }
                }
            }
        }
        else -> LoadingOverlay(message = "Loading exercises...")
    }
}

@Composable
private fun ExercisePickerCard(
    exercise: Exercise,
    onClick: () -> Unit
) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = exercise.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(exercise.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DayTodayButton(text = "Start", onClick = onClick)
        }
    }
}

@Composable
private fun WorkoutBuilder(
    exercisesState: UiState<List<Exercise>>,
    builderSessions: List<WorkoutSession>,
    onExerciseClick: (Exercise) -> Unit,
    onRemove: (Int) -> Unit,
    onSave: () -> Unit,
    onStartExercise: (Exercise) -> Unit,
    onRetry: () -> Unit
) {
    when (exercisesState) {
        UiState.Loading -> LoadingOverlay(message = "Loading exercises...")
        is UiState.Error -> ErrorState(
            message = friendlyExerciseError(exercisesState.message),
            onRetry = onRetry
        )
        is UiState.Success -> {
            val exerciseList = exercisesState.data
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Build Your Workout", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap exercises to add them. Then start the workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Saved workout plan summary
                if (builderSessions.isNotEmpty()) {
                    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Your Workout Plan", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            builderSessions.forEachIndexed { index, session ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.exerciseName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "1 set · 0 reps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onRemove(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            "Remove",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DayTodayButton(
                                    text = "Save Plan",
                                    onClick = onSave,
                                    buttonType = ButtonType.Tonal,
                                    modifier = Modifier.weight(1f)
                                )
                                DayTodayButton(
                                    text = "Start Workout",
                                    onClick = {
                                        builderSessions.firstOrNull()?.let { first ->
                                            // Find the exercise by id from the saved list
                                            exerciseList.firstOrNull { it.id == first.exerciseId }?.let {
                                                onStartExercise(it)
                                            }
                                        }
                                    },
                                    buttonType = ButtonType.Filled,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text("Add Exercises", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(exerciseList) { exercise ->
                        ExercisePickerCard(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise) }
                        )
                    }
                }
            }
        }
        else -> LoadingOverlay(message = "Loading exercises...")
    }
}

@Composable
private fun ActiveWorkout(
    session: WorkoutSession,
    onAddSet: () -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onRpeChange: (Int, Double?) -> Unit,
    onCompleteSet: (Int) -> Unit,
    onFinish: () -> Unit
) {
    var now by remember(session.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        DayTodayCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(session.exerciseName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    formatDuration(now - session.startTime),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (session.isCompleted) "Completed" else "In progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(session.sets.size) { index ->
                SetRow(
                    set = session.sets[index],
                    setNumber = index + 1,
                    onWeightChange = { onWeightChange(index, it) },
                    onRepsChange = { onRepsChange(index, it) },
                    onRpeChange = { onRpeChange(index, it) },
                    onComplete = { onCompleteSet(index) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        DayTodayButton(
            onClick = onAddSet,
            text = "Add Set",
            modifier = Modifier.fillMaxWidth(),
            buttonType = ButtonType.Outlined
        )
        Spacer(modifier = Modifier.height(8.dp))
        DayTodayButton(
            onClick = onFinish,
            text = if (session.isCompleted) "Completed" else "Finish Workout",
            modifier = Modifier.fillMaxWidth(),
            enabled = !session.isCompleted,
            buttonType = ButtonType.Filled
        )
    }
}

private fun friendlyExerciseError(rawMessage: String): String {
    val lower = rawMessage.lowercase()
    return when {
        lower.contains("tls") || lower.contains("ssl") || lower.contains("certificate") ||
            lower.contains("unrecognized_name") || lower.contains("handshake") ||
            lower.contains("unknownhost") || lower.contains("host") ||
            lower.contains("timeout") || lower.contains("timed out") ||
            lower.contains("connect") || lower.contains("network") || lower.contains("socket") ->
            "Couldn't reach the server. Check your connection and try again."
        lower.contains("sqlite") || lower.contains("database") || lower.contains("room") ->
            "Couldn't read your saved exercises. Please try again."
        else -> "Couldn't load exercises right now. Please try again."
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisePickerLoadingPreview() {
    DayTodayTheme {
        ExercisePicker(UiState.Loading, onExerciseClick = {}, onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisePickerErrorPreview() {
    DayTodayTheme {
        ExercisePicker(
            exerciseState = UiState.Error("javax.net.ssl.SSLHandshakeException: TLSV1_ALERT_UNRECOGNIZED_NAME"),
            onExerciseClick = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisePickerEmptyPreview() {
    DayTodayTheme {
        ExercisePicker(UiState.Success(emptyList()), onExerciseClick = {}, onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisePickerListPreview() {
    DayTodayTheme {
        ExercisePicker(
            exerciseState = UiState.Success(
                listOf(
                    Exercise(
                        id = "ex_bench_press",
                        name = "Bench Press",
                        muscleGroup = "Chest",
                        equipment = "Barbell",
                        instructions = "Lower the bar to mid-chest with elbows tucked.",
                        demoVideoUrl = null
                    ),
                    Exercise(
                        id = "ex_back_squat",
                        name = "Back Squat",
                        muscleGroup = "Legs",
                        equipment = "Barbell",
                        instructions = "Sit the hips back and drive up through the foot.",
                        demoVideoUrl = null
                    )
                )
            ),
            onExerciseClick = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveWorkoutEmptySetsPreview() {
    DayTodayTheme {
        ActiveWorkout(
            session = WorkoutSession(
                id = "preview-session",
                exerciseId = "ex_bench_press",
                exerciseName = "Bench Press",
                startTime = System.currentTimeMillis(),
                endTime = null,
                durationMinutes = 0,
                caloriesBurned = 0,
                sets = emptyList(),
                isCompleted = false
            ),
            onAddSet = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onRpeChange = { _, _ -> },
            onCompleteSet = { _ -> },
            onFinish = {}
        )
    }
}