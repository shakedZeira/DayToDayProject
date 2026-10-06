package com.daytoday.ui.screen.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.daytoday.model.Exercise
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    isSelected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelect: () -> Unit,
    onStartNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                DetailMetaRow(exercise = exercise)

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    ExerciseAnimationView(
                        exerciseName = exercise.name,
                        muscleGroup = exercise.muscleGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        showLabel = false
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "How To Do It",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = exercise.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            DetailActionRow(
                isSelected = isSelected,
                onToggleSelect = onToggleSelect,
                onStartNow = onStartNow
            )
        }
    }
}

@Composable
fun ExerciseDetailDialog(
    exercise: Exercise,
    isSelected: Boolean,
    onDismiss: () -> Unit,
    onToggleSelect: () -> Unit,
    onStartNow: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ExerciseAnimationView(
                    exerciseName = exercise.name,
                    muscleGroup = exercise.muscleGroup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    showLabel = false
                )

                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                DetailMetaRow(exercise = exercise)

                Text(
                    text = exercise.instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .verticalScroll(rememberScrollState())
                )

                DetailActionRow(
                    isSelected = isSelected,
                    onToggleSelect = onToggleSelect,
                    onStartNow = onStartNow,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DetailMetaRow(exercise: Exercise) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetaPill(text = exercise.muscleGroup)
        MetaPill(text = exercise.equipment)
        MetaPill(text = resolveMotionType(exercise.name, exercise.muscleGroup).prettyMotionLabel())
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DetailActionRow(
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onStartNow: (() -> Unit)?,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onStartNow != null) {
            DayTodayButton(
                text = "Start Now",
                onClick = {
                    onStartNow()
                    onDismiss?.invoke()
                },
                modifier = Modifier.weight(1f),
                buttonType = ButtonType.Filled
            )
        }
        if (isSelected) {
            DayTodayButton(
                text = "Remove from Workout",
                onClick = onToggleSelect,
                modifier = if (onStartNow != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                buttonType = ButtonType.Outlined,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            )
        } else {
            DayTodayButton(
                text = "Add to Workout",
                onClick = onToggleSelect,
                modifier = if (onStartNow != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                buttonType = ButtonType.Filled
            )
        }
    }
}

private fun ExerciseMotionType.prettyMotionLabel(): String =
    name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }