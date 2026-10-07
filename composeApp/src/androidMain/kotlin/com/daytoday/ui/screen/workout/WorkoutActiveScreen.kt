package com.daytoday.ui.screen.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.model.Exercise
import com.daytoday.model.PlanExercise
import com.daytoday.model.WorkoutPlan
import com.daytoday.model.WorkoutSession
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.util.formatDuration
import kotlinx.coroutines.delay

@Composable
fun WorkoutActiveScreen(
    navController: NavController = rememberNavController(),
    viewModel: WorkoutActiveViewModel = hiltViewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activePlanQueue by viewModel.activePlanQueue.collectAsStateWithLifecycle()
    val activePlanIndex by viewModel.activePlanIndex.collectAsStateWithLifecycle()
    val exercisesState by viewModel.exercises.collectAsStateWithLifecycle()
    val selectedExercises by viewModel.selectedExercises.collectAsStateWithLifecycle()
    val builderExercises by viewModel.builderExercises.collectAsStateWithLifecycle()
    val savedPlans by viewModel.savedPlans.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val spotifyLoggedIn by viewModel.spotifyLoggedIn.collectAsStateWithLifecycle()
    val spotifyMessage by viewModel.spotifyMessage.collectAsStateWithLifecycle()
    val spotifyPlaylistName by viewModel.spotifyPlaylistName.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    var detailExercise by remember { mutableStateOf<Exercise?>(null) }

    fun handleBackNavigation() {
        if (activeSession != null) {
            if (activeSession?.isCompleted == true) {
                viewModel.exitActiveWorkout()
            } else {
                showExitDialog = true
            }
        } else {
            val popped = navController.popBackStack()
            if (!popped) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Leave Active Workout?") },
            text = { Text("Your workout is still in progress. Any sets marked completed will be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.completeWorkout()
                        viewModel.exitActiveWorkout()
                    }
                ) {
                    Text("Finish & Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            viewModel.exitActiveWorkout()
                        }
                    ) {
                        Text("Discard & Exit", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Resume")
                    }
                }
            }
        )
    }

    detailExercise?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            isSelected = selectedExercises.any { it.id == exercise.id },
            onDismiss = { detailExercise = null },
            onToggleSelect = { viewModel.toggleExerciseSelection(exercise) },
            onStartNow = if (currentTab == WorkoutScreenTab.QUICK_START) {
                { viewModel.startSingleExercise(exercise) }
            } else {
                null
            }
        )
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = when {
                    activeSession != null -> "Workout Session"
                    currentTab == WorkoutScreenTab.BUILDER -> "Workout Builder"
                    currentTab == WorkoutScreenTab.SAVED_PLANS -> "Saved Routines"
                    currentTab == WorkoutScreenTab.QUICK_START -> "Quick Workout"
                    currentTab == WorkoutScreenTab.PROGRESS -> "Workout Progress"
                    else -> "Workout"
                },
                navigationIcon = {
                    IconButton(onClick = { handleBackNavigation() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setTab(WorkoutScreenTab.PROGRESS) }) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Workout Progress")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (activeSession != null) {
                ActiveWorkoutScreen(
                    session = activeSession!!,
                    planQueue = activePlanQueue,
                    planIndex = activePlanIndex,
                    userWeightKg = userProfile.weightKg.takeIf { it > 0 } ?: 70.0,
                    onAddSet = viewModel::addSet,
                    onWeightChange = viewModel::updateSetWeight,
                    onRepsChange = viewModel::updateSetReps,
                    onRpeChange = viewModel::updateSetRpe,
                    onCompleteSet = viewModel::completeSet,
                    onNextExercise = viewModel::nextExerciseInPlan,
                    onFinishWorkout = viewModel::completeWorkout,
                    onDone = {
                        viewModel.exitActiveWorkout()
                        val popped = navController.popBackStack()
                        if (!popped) navController.navigate(Screen.Home.route)
                    },
                    spotifyLoggedIn = viewModel.spotifyLoggedIn.value,
                    spotifyMessage = spotifyMessage,
                    spotifyPlaylistName = spotifyPlaylistName,
                    onPlaySpotify = viewModel::playSpotifyMusic
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mode Selector Tabs: [Build Workout] [Saved Plans] [Quick Start] [Progress]
                    ScrollableTabRow(
                        selectedTabIndex = currentTab.ordinal,
                        edgePadding = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = currentTab == WorkoutScreenTab.BUILDER,
                            onClick = { viewModel.setTab(WorkoutScreenTab.BUILDER) },
                            text = { Text("Build Workout") },
                            icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) }
                        )
                        Tab(
                            selected = currentTab == WorkoutScreenTab.SAVED_PLANS,
                            onClick = { viewModel.setTab(WorkoutScreenTab.SAVED_PLANS) },
                            text = { Text("Saved Plans (${savedPlans.size})") },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                        )
                        Tab(
                            selected = currentTab == WorkoutScreenTab.QUICK_START,
                            onClick = { viewModel.setTab(WorkoutScreenTab.QUICK_START) },
                            text = { Text("Quick Start") },
                            icon = { Icon(Icons.Default.FlashOn, contentDescription = null) }
                        )
                        Tab(
                            selected = currentTab == WorkoutScreenTab.PROGRESS,
                            onClick = { viewModel.setTab(WorkoutScreenTab.PROGRESS) },
                            text = { Text("Progress") },
                            icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "View Workout Progress") }
                        )
                    }

                    when (currentTab) {
                        WorkoutScreenTab.BUILDER -> {
                            WorkoutBuilderScreen(
                                exercisesState = exercisesState,
                                selectedExercises = selectedExercises,
                                builderExercises = builderExercises,
                                onToggleExercise = viewModel::toggleExerciseSelection,
                                onRemoveExercise = viewModel::removeSelectedExercise,
                                onClearAll = viewModel::clearSelectedExercises,
                                onUpdateSets = viewModel::updateExerciseSets,
                                onUpdateReps = viewModel::updateExerciseReps,
                                onUpdateWeight = viewModel::updateExerciseWeight,
                                onSavePlan = { name -> viewModel.saveCurrentPlan(name) },
                                onStartWorkout = { viewModel.startSelectedAsWorkout() },
                                onNavigateToProgress = { viewModel.setTab(WorkoutScreenTab.PROGRESS) },
                                onShowDetail = { detailExercise = it },
                                onRetry = viewModel::loadExercises
                            )
                        }
                        WorkoutScreenTab.SAVED_PLANS -> {
                            SavedPlansScreen(
                                plans = savedPlans,
                                onStartPlan = viewModel::startPlan,
                                onDeletePlan = viewModel::deletePlan,
                                onGoToBuilder = { viewModel.setTab(WorkoutScreenTab.BUILDER) }
                            )
                        }
                        WorkoutScreenTab.QUICK_START -> {
                            QuickStartScreen(
                                exercisesState = exercisesState,
                                onStartExercise = viewModel::startSingleExercise,
                                onShowDetail = { detailExercise = it },
                                onRetry = viewModel::loadExercises
                            )
                        }
                        WorkoutScreenTab.PROGRESS -> {
                            WorkoutProgressScreenContent()
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Category Matching Helper
// ==========================================
private fun matchesExerciseCategory(exercise: Exercise, category: String): Boolean {
    if (category == "All") return true
    val group = exercise.muscleGroup
    return when (category) {
        "Chest" -> group.contains("Chest", ignoreCase = true)
        "Back" -> group.contains("Back", ignoreCase = true)
        "Legs" -> group.contains("Leg", ignoreCase = true)
        "Shoulders" -> group.contains("Shoulder", ignoreCase = true)
        "Arms" -> group.contains("Arm", ignoreCase = true)
        "Core" -> group.contains("Core", ignoreCase = true)
        "Glutes" -> group.contains("Glutes", ignoreCase = true)
        "Cardio" -> group.contains("Cardio", ignoreCase = true)
        "Full Body" -> group.contains("Full Body", ignoreCase = true)
        else -> group.contains(category, ignoreCase = true)
    }
}

@Composable
private fun WorkoutCategoryChips(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "All", "Chest", "Back", "Legs", "Glutes", "Shoulders", "Arms", "Core", "Cardio", "Full Body"
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = {
                    Text(
                        category,
                        fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

// ==========================================
// 1. WORKOUT BUILDER SCREEN
// ==========================================

@Composable
private fun WorkoutBuilderScreen(
    exercisesState: UiState<List<Exercise>>,
    selectedExercises: List<Exercise>,
    builderExercises: List<PlanExercise>,
    onToggleExercise: (Exercise) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onClearAll: () -> Unit,
    onUpdateSets: (exerciseId: String, sets: Int) -> Unit,
    onUpdateReps: (exerciseId: String, reps: Int) -> Unit,
    onUpdateWeight: (exerciseId: String, weightKg: Double) -> Unit,
    onSavePlan: (name: String) -> Unit,
    onStartWorkout: () -> Unit,
    onNavigateToProgress: () -> Unit,
    onShowDetail: (Exercise) -> Unit,
    onRetry: () -> Unit
) {
    var planName by remember { mutableStateOf("Custom Routine") }
    var savedSuccess by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    when (exercisesState) {
        UiState.Loading -> LoadingOverlay(message = "Loading exercises...")
        is UiState.Error -> ErrorState(message = exercisesState.message, onRetry = onRetry)
        is UiState.Success -> {
            val allExercises = exercisesState.data
            val filteredExercises = allExercises.filter { matchesExerciseCategory(it, selectedCategory) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Workout Progress Quick Navigation Card
                item {
                    DayTodayCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToProgress() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Workout Progress & History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "View charts, personal bests & daily targets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = "Open Progress",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Header Plan Card (Shows currently configured exercises with per-exercise sets, reps, weight)
                item {
                    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Your Workout Plan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (builderExercises.isNotEmpty()) {
                                    TextButton(onClick = onClearAll) {
                                        Text("Clear (${builderExercises.size})", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = planName,
                                onValueChange = { planName = it; savedSuccess = false },
                                label = { Text("Plan Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (builderExercises.isEmpty()) {
                                Text(
                                    "Select exercises below to customize sets, reps, and weight individually for this workout.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "Configured Exercises (${builderExercises.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    builderExercises.forEachIndexed { index, planEx ->
                                        BuilderExerciseItem(
                                            planEx = planEx,
                                            index = index,
                                            onUpdateSets = { onUpdateSets(planEx.exerciseId, it) },
                                            onUpdateReps = { onUpdateReps(planEx.exerciseId, it) },
                                            onUpdateWeight = { onUpdateWeight(planEx.exerciseId, it) },
                                            onRemove = { onRemoveExercise(planEx.exerciseId) },
                                            onShowDetail = {
                                                allExercises.firstOrNull { it.id == planEx.exerciseId }
                                                    ?.let(onShowDetail)
                                            }
                                        )
                                    }
                                }
                            }

                            if (savedSuccess) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Plan saved to 'Saved Plans' tab!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DayTodayButton(
                                    text = "Save Plan",
                                    onClick = {
                                        onSavePlan(planName)
                                        savedSuccess = true
                                    },
                                    enabled = builderExercises.isNotEmpty(),
                                    buttonType = ButtonType.Tonal,
                                    modifier = Modifier.weight(1f)
                                )
                                DayTodayButton(
                                    text = "Start Workout",
                                    onClick = onStartWorkout,
                                    enabled = builderExercises.isNotEmpty(),
                                    buttonType = ButtonType.Filled,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Category Filter Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Add Exercises",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${filteredExercises.size} found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        WorkoutCategoryChips(
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it }
                        )
                    }
                }

                // Exercise List
                if (filteredExercises.isEmpty()) {
                    item {
                        Text(
                            "No exercises found for category '$selectedCategory'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(filteredExercises) { exercise ->
                        val isSelected = selectedExercises.any { it.id == exercise.id }
                        ExercisePickerCard(
                            exercise = exercise,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleExercise(exercise) },
                            onShowDetail = { onShowDetail(exercise) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderExerciseItem(
    planEx: PlanExercise,
    index: Int,
    onUpdateSets: (Int) -> Unit,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onRemove: () -> Unit,
    onShowDetail: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExerciseAnimationView(
                    exerciseName = planEx.exerciseName,
                    muscleGroup = planEx.muscleGroup,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowDetail() },
                    showLabel = false
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${planEx.exerciseName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        planEx.muscleGroup,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Stepper controls for Sets, Reps, and Weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sets stepper
                CompactStepper(
                    label = "Sets",
                    value = planEx.targetSets.toString(),
                    onDecrement = { onUpdateSets(maxOf(1, planEx.targetSets - 1)) },
                    onIncrement = { onUpdateSets(planEx.targetSets + 1) },
                    modifier = Modifier.weight(1f)
                )

                // Reps stepper
                CompactStepper(
                    label = "Reps",
                    value = planEx.targetReps.toString(),
                    onDecrement = { onUpdateReps(maxOf(1, planEx.targetReps - 1)) },
                    onIncrement = { onUpdateReps(planEx.targetReps + 1) },
                    modifier = Modifier.weight(1f)
                )

                // Weight stepper (kg)
                val weightStr = if (planEx.targetWeightKg <= 0.0) {
                    "0 kg"
                } else if (planEx.targetWeightKg % 1.0 == 0.0) {
                    "${planEx.targetWeightKg.toInt()} kg"
                } else {
                    "${planEx.targetWeightKg} kg"
                }
                CompactStepper(
                    label = "Weight",
                    value = weightStr,
                    onDecrement = { onUpdateWeight(maxOf(0.0, planEx.targetWeightKg - 2.5)) },
                    onIncrement = { onUpdateWeight(planEx.targetWeightKg + 2.5) },
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
private fun CompactStepper(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase $label",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ==========================================
// 2. SAVED PLANS SCREEN
// ==========================================

@Composable
private fun SavedPlansScreen(
    plans: List<WorkoutPlan>,
    onStartPlan: (WorkoutPlan) -> Unit,
    onDeletePlan: (String) -> Unit,
    onGoToBuilder: () -> Unit
) {
    if (plans.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Text(
                    "No Saved Workout Plans",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Build and save a workout routine in the 'Build Workout' tab to quickly start it anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DayTodayButton(
                    text = "Go to Workout Builder",
                    onClick = onGoToBuilder,
                    buttonType = ButtonType.Filled
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Your Saved Workout Routines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(plans) { plan ->
                DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                plan.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { onDeletePlan(plan.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Plan", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Text(
                            "${plan.exercises.size} Exercises: " + plan.exercises.joinToString(", ") {
                                val wt = if (it.targetWeightKg > 0) " (${it.targetSets}x${it.targetReps} @ ${it.targetWeightKg}kg)" else " (${it.targetSets}x${it.targetReps})"
                                "${it.exerciseName}$wt"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DayTodayButton(
                            text = "Start This Workout",
                            onClick = { onStartPlan(plan) },
                            buttonType = ButtonType.Filled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. QUICK START SCREEN
// ==========================================

@Composable
private fun QuickStartScreen(
    exercisesState: UiState<List<Exercise>>,
    onStartExercise: (Exercise) -> Unit,
    onShowDetail: (Exercise) -> Unit,
    onRetry: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }

    when (exercisesState) {
        UiState.Loading -> LoadingOverlay(message = "Loading exercises...")
        is UiState.Error -> ErrorState(message = exercisesState.message, onRetry = onRetry)
        is UiState.Success -> {
            val list = exercisesState.data
            val filteredList = list.filter { matchesExerciseCategory(it, selectedCategory) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Select Any Exercise to Start Immediately",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        WorkoutCategoryChips(
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it }
                        )
                    }
                }

                if (filteredList.isEmpty()) {
                    item {
                        Text(
                            "No exercises found for category '$selectedCategory'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(filteredList) { exercise ->
                        DayTodayCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowDetail(exercise) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Exercise Animation Preview
                                ExerciseAnimationView(
                                    exerciseName = exercise.name,
                                    muscleGroup = exercise.muscleGroup,
                                    modifier = Modifier.size(62.dp),
                                    showLabel = false
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${exercise.muscleGroup} • ${exercise.equipment}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DayTodayButton(text = "Start", onClick = { onStartExercise(exercise) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ACTIVE WORKOUT SCREEN
// ==========================================

@Composable
private fun ActiveWorkoutScreen(
    session: WorkoutSession,
    planQueue: List<PlanExercise>,
    planIndex: Int,
    userWeightKg: Double,
    onAddSet: () -> Unit,
    onWeightChange: (Int, Double) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onRpeChange: (Int, Double?) -> Unit,
    onCompleteSet: (Int) -> Unit,
    onNextExercise: () -> Unit,
    onFinishWorkout: () -> Unit,
    onDone: () -> Unit,
    spotifyLoggedIn: Boolean,
    spotifyMessage: String?,
    spotifyPlaylistName: String?,
    onPlaySpotify: () -> Unit
) {
    var now by remember(session.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (!session.isCompleted) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val isPartOfPlan = planQueue.isNotEmpty()
    val hasNextExercise = isPartOfPlan && (planIndex + 1 < planQueue.size)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary & Metric Card with Animated Exercise Demonstration
        DayTodayCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isPartOfPlan) {
                    Text(
                        "Exercise ${planIndex + 1} of ${planQueue.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            session.exerciseName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    formatDuration(now - session.startTime),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    "${session.caloriesBurned} kcal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Looping Exercise Motion Demonstration
                    ExerciseAnimationView(
                        exerciseName = session.exerciseName,
                        muscleGroup = planQueue.getOrNull(planIndex)?.muscleGroup ?: "",
                        modifier = Modifier.size(86.dp),
                        showLabel = true
                    )
                }

                if (session.isCompleted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Workout Saved & Recorded to Calories!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Sets List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(session.sets) { index, setRecord ->
                SetRow(
                    set = setRecord,
                    setNumber = index + 1,
                    onWeightChange = { onWeightChange(index, it) },
                    onRepsChange = { onRepsChange(index, it) },
                    onRpeChange = { onRpeChange(index, it) },
                    onComplete = { onCompleteSet(index) }
                )
            }
        }

        if (!session.isCompleted) {
            DayTodayButton(
                onClick = onAddSet,
                text = "Add Set",
                buttonType = ButtonType.Outlined,
                modifier = Modifier.fillMaxWidth()
            )

            if (spotifyLoggedIn) {
                DayTodayButton(
                    onClick = onPlaySpotify,
                    text = "Play Spotify Music",
                    buttonType = ButtonType.Tonal,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            spotifyMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            spotifyPlaylistName?.let { playlistName ->
                Text(
                    "Playlist: $playlistName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasNextExercise) {
                DayTodayButton(
                    onClick = onNextExercise,
                    text = "Next: ${planQueue[planIndex + 1].exerciseName}",
                    buttonType = ButtonType.Tonal,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            DayTodayButton(
                onClick = onFinishWorkout,
                text = "Finish Workout & Save",
                buttonType = ButtonType.Filled,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            DayTodayButton(
                onClick = onDone,
                text = "Done (Return to Home)",
                buttonType = ButtonType.Filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
