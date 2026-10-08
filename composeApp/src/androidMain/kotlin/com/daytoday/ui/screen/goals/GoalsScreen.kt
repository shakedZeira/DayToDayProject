package com.daytoday.ui.screen.goals

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.daytoday.settings.CUSTOM_TARGET_MAX
import com.daytoday.settings.CUSTOM_TARGET_MIN
import com.daytoday.settings.GoalPeriod
import com.daytoday.settings.GoalType
import com.daytoday.settings.MAX_CUSTOM_GOAL_LENGTH
import com.daytoday.settings.StatsSource
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Goals",
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
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
                ErrorState(state.message, viewModel::refresh)
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
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllAddedMessage by remember { mutableStateOf(false) }
    var editingGoalId by remember { mutableStateOf<String?>(null) }
    var pendingRemovalId by remember { mutableStateOf<String?>(null) }

    val availableTypes = GoalType.values().filter { type ->
        type == GoalType.CUSTOM || state.goals.none { it.entry.type == type }
    }
    val editingGoal = state.goals.firstOrNull { it.entry.id == editingGoalId }
    val removalGoal = state.goals.firstOrNull { it.entry.id == pendingRemovalId }
    val todayGoals = state.goals.filter { it.entry.type.source == StatsSource.TODAY }
    val weekGoals = state.goals.filter { it.entry.type.source == StatsSource.WEEK }
    val customGoals = state.goals.filter { it.entry.type == GoalType.CUSTOM }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DayTodayButton(
            onClick = {
                if (availableTypes.isEmpty()) {
                    showAllAddedMessage = true
                } else {
                    showAddDialog = true
                }
            },
            text = "Add goal",
            modifier = Modifier.fillMaxWidth()
        )

        if (state.goals.isEmpty()) {
            Text(
                text = "No goals yet. Tap \"Add goal\" to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (todayGoals.isNotEmpty()) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            todayGoals.forEach { item ->
                GoalCard(
                    title = item.entry.type.label,
                    icon = item.entry.type.icon(),
                    current = item.current,
                    goal = item.entry.target,
                    unit = item.entry.type.unit,
                    emptyHint = item.entry.type.emptyHint(),
                    onEdit = { editingGoalId = item.entry.id },
                    onRemove = { pendingRemovalId = item.entry.id }
                )
            }
        }

        if (weekGoals.isNotEmpty()) {
            Text("This Week", style = MaterialTheme.typography.titleLarge)
            weekGoals.forEach { item ->
                GoalCard(
                    title = item.entry.type.label,
                    icon = item.entry.type.icon(),
                    current = item.current,
                    goal = item.entry.target,
                    unit = item.entry.type.unit,
                    emptyHint = item.entry.type.emptyHint(),
                    onEdit = { editingGoalId = item.entry.id },
                    onRemove = { pendingRemovalId = item.entry.id }
                )
            }
        }

        if (customGoals.isNotEmpty()) {
            Text("Custom", style = MaterialTheme.typography.titleLarge)
            customGoals.forEach { item ->
                CustomGoalCard(
                    title = item.entry.label,
                    progress = item.entry.progress,
                    target = item.entry.target,
                    period = item.entry.period,
                    onIncrement = { viewModel.incrementCustomProgress(item.entry.id) },
                    onDecrement = { viewModel.decrementCustomProgress(item.entry.id) },
                    onEdit = { editingGoalId = item.entry.id },
                    onRemove = { pendingRemovalId = item.entry.id }
                )
            }
        }
    }

    if (showAddDialog && availableTypes.isNotEmpty()) {
        AddGoalDialog(
            availableTypes = availableTypes,
            onDismiss = { showAddDialog = false },
            onConfirm = { type, target ->
                showAddDialog = false
                viewModel.addGoal(type, target)
            },
            onConfirmCustom = { label, target, period ->
                showAddDialog = false
                viewModel.addCustomGoal(label, target, period)
            }
        )
    }

    if (showAllAddedMessage) {
        AlertDialog(
            onDismissRequest = { showAllAddedMessage = false },
            title = { Text("All goals added") },
            text = { Text("Every supported goal type is already on your list.") },
            confirmButton = {
                TextButton(onClick = { showAllAddedMessage = false }) {
                    Text("OK")
                }
            }
        )
    }

    editingGoal?.let { item ->
        key(item.entry.id) {
            if (item.entry.type == GoalType.CUSTOM) {
                CustomGoalDialog(
                    title = "Edit goal",
                    initialLabel = item.entry.label,
                    initialTarget = item.entry.target.toInt(),
                    initialPeriod = item.entry.period,
                    onDismiss = { editingGoalId = null },
                    onConfirm = { label, target, period ->
                        editingGoalId = null
                        viewModel.updateCustomGoal(item.entry.id, label, target, period)
                    }
                )
            } else {
                EditGoalDialog(
                    title = "${item.entry.type.label} goal",
                    initialValue = item.entry.target.toInt(),
                    maxValue = item.entry.type.maxValue,
                    onDismiss = { editingGoalId = null },
                    onConfirm = { value ->
                        editingGoalId = null
                        viewModel.updateGoalTarget(item.entry.id, value.toDouble())
                    }
                )
            }
        }
    }

    removalGoal?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
            title = { Text("Remove goal?") },
            text = {
                val name = if (item.entry.type == GoalType.CUSTOM) {
                    item.entry.label
                } else {
                    item.entry.type.label
                }
                Text("Remove \"$name\" from your goals?")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemovalId = null
                    viewModel.removeGoal(item.entry.id)
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovalId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddGoalDialog(
    availableTypes: List<GoalType>,
    onDismiss: () -> Unit,
    onConfirm: (GoalType, Double) -> Unit,
    onConfirmCustom: (String, Double, GoalPeriod) -> Unit
) {
    var selectedType by remember { mutableStateOf(availableTypes.first()) }
    var targetText by remember { mutableStateOf(selectedType.defaultValue.toInt().toString()) }
    var customText by remember { mutableStateOf("") }
    var customTargetText by remember { mutableStateOf("1") }
    var customPeriod by remember { mutableStateOf(GoalPeriod.WEEK) }

    fun select(type: GoalType) {
        selectedType = type
        targetText = type.defaultValue.toInt().toString()
    }

    val parsed = targetText.trim().toIntOrNull()
    val customTarget = customTargetText.trim().toIntOrNull()
    val customLabelValid = customText.trim().isNotEmpty()
    val customTargetValid = customTarget != null && customTarget in CUSTOM_TARGET_MIN..CUSTOM_TARGET_MAX
    val valid = if (selectedType == GoalType.CUSTOM) {
        customLabelValid && customTargetValid
    } else {
        parsed != null && parsed > 0 && parsed <= selectedType.maxValue
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add goal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { select(type) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { select(type) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(type.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (type == GoalType.CUSTOM) {
                                    "Write your own goal"
                                } else {
                                    "Default ${type.defaultValue.toInt()} ${type.unit}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedType == GoalType.CUSTOM) {
                    CustomGoalFields(
                        label = customText,
                        onLabelChange = { newText ->
                            customText = newText.take(MAX_CUSTOM_GOAL_LENGTH)
                        },
                        targetText = customTargetText,
                        onTargetChange = { newText ->
                            customTargetText = newText.filter { it.isDigit() }
                        },
                        period = customPeriod,
                        onPeriodChange = { customPeriod = it },
                        labelValid = customLabelValid,
                        targetValid = customTargetValid,
                    )
                } else {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { newText ->
                            targetText = newText.filter { it.isDigit() }
                        },
                        label = { Text("Target (${selectedType.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !valid,
                        supportingText = {
                            if (!valid) {
                                Text("Enter a number between 1 and ${selectedType.maxValue}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedType == GoalType.CUSTOM) {
                        if (customLabelValid && customTargetValid) {
                            onConfirmCustom(
                                customText.trim(),
                                customTarget!!.toDouble(),
                                customPeriod,
                            )
                        }
                    } else {
                        parsed?.let { onConfirm(selectedType, it.toDouble()) }
                    }
                },
                enabled = valid
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GoalCard(
    title: String,
    icon: ImageVector,
    current: Double,
    goal: Double,
    unit: String,
    emptyHint: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val safeGoal = goal.coerceAtLeast(1.0)
    val fraction = if (safeGoal > 0.0) {
        (current / safeGoal).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit $title",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove $title",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${formatValue(current)} / ${formatValue(goal)} $unit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (met) {
                    Text(
                        text = if (current > goal) {
                            "Over by ${formatValue(current - goal)} $unit"
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

@Composable
private fun CustomGoalCard(
    title: String,
    progress: Double,
    target: Double,
    period: GoalPeriod,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val safeTarget = target.coerceAtLeast(1.0)
    val fraction = (progress / safeTarget).toFloat().coerceIn(0f, 1f)
    val achieved = progress >= safeTarget
    val periodWord = if (period == GoalPeriod.DAY) "day" else "week"

    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${progress.toInt()} of ${target.toInt()} per $periodWord",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit $title",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove $title",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDecrement) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease $title",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        "${progress.toInt()} / ${target.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onIncrement) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase $title",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (achieved) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Achieved",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Label + numeric target + period selector, shared by the add and edit dialogs. */
@Composable
private fun CustomGoalFields(
    label: String,
    onLabelChange: (String) -> Unit,
    targetText: String,
    onTargetChange: (String) -> Unit,
    period: GoalPeriod,
    onPeriodChange: (GoalPeriod) -> Unit,
    labelValid: Boolean,
    targetValid: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text("Goal") },
            singleLine = true,
            isError = !labelValid,
            supportingText = {
                if (!labelValid) {
                    Text("Enter your goal")
                } else {
                    Text("${label.trim().length}/$MAX_CUSTOM_GOAL_LENGTH")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = targetText,
            onValueChange = onTargetChange,
            label = { Text("Target") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !targetValid,
            supportingText = {
                if (!targetValid) {
                    Text(
                        "Enter a number between $CUSTOM_TARGET_MIN and $CUSTOM_TARGET_MAX"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period == GoalPeriod.DAY,
                onClick = { onPeriodChange(GoalPeriod.DAY) },
                label = { Text("Per day") }
            )
            FilterChip(
                selected = period == GoalPeriod.WEEK,
                onClick = { onPeriodChange(GoalPeriod.WEEK) },
                label = { Text("Per week") }
            )
        }
    }
}

@Composable
private fun CustomGoalDialog(
    title: String,
    initialLabel: String,
    initialTarget: Int,
    initialPeriod: GoalPeriod,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, GoalPeriod) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var targetText by remember {
        mutableStateOf(initialTarget.coerceIn(CUSTOM_TARGET_MIN, CUSTOM_TARGET_MAX).toString())
    }
    var period by remember { mutableStateOf(initialPeriod) }

    val labelValid = label.trim().isNotEmpty()
    val parsedTarget = targetText.trim().toIntOrNull()
    val targetValid = parsedTarget != null && parsedTarget in CUSTOM_TARGET_MIN..CUSTOM_TARGET_MAX
    val valid = labelValid && targetValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomGoalFields(
                    label = label,
                    onLabelChange = { label = it.take(MAX_CUSTOM_GOAL_LENGTH) },
                    targetText = targetText,
                    onTargetChange = { newText -> targetText = newText.filter { it.isDigit() } },
                    period = period,
                    onPeriodChange = { period = it },
                    labelValid = labelValid,
                    targetValid = targetValid,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedTarget?.let { onConfirm(label.trim(), it.toDouble(), period) } },
                enabled = valid
            ) {
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

private fun formatValue(value: Double): String =
    String.format(Locale.getDefault(), "%,d", value.roundToLong())

private fun GoalType.icon(): ImageVector = when (this) {
    GoalType.CALORIES_TODAY -> Icons.Default.LocalFireDepartment
    GoalType.WORKOUTS_PER_WEEK -> Icons.Default.FitnessCenter
    GoalType.STEPS_TODAY -> Icons.Default.DirectionsWalk
    GoalType.ACTIVE_MINUTES_TODAY -> Icons.Default.Timer
    GoalType.WORKOUT_MINUTES_PER_WEEK -> Icons.Default.FitnessCenter
    GoalType.CALORIES_BURNED_PER_WEEK -> Icons.Default.LocalFireDepartment
    GoalType.LIFTING_VOLUME_PER_WEEK -> Icons.Default.FitnessCenter
    GoalType.CUSTOM -> Icons.Default.Check
}

private fun GoalType.emptyHint(): String = when (this) {
    GoalType.CALORIES_TODAY -> "No calories burned yet today"
    GoalType.WORKOUTS_PER_WEEK -> "No workouts yet this week"
    GoalType.STEPS_TODAY -> "No steps recorded yet today"
    GoalType.ACTIVE_MINUTES_TODAY -> "No active minutes yet today"
    GoalType.WORKOUT_MINUTES_PER_WEEK -> "No workout minutes yet this week"
    GoalType.CALORIES_BURNED_PER_WEEK -> "No calories burned yet this week"
    GoalType.LIFTING_VOLUME_PER_WEEK -> "No lifting volume yet this week"
    GoalType.CUSTOM -> ""
}
