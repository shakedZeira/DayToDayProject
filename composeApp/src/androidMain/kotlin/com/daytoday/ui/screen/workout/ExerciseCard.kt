package com.daytoday.ui.screen.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daytoday.model.Exercise
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard

@Composable
fun ExerciseCard(exercise: Exercise, onClick: () -> Unit) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExerciseAnimationView(
                exerciseName = exercise.name,
                muscleGroup = exercise.muscleGroup,
                modifier = Modifier.size(56.dp),
                showLabel = false
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercise.muscleGroup} • ${exercise.equipment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DayTodayButton(text = "Start", onClick = onClick)
        }
    }
}

@Composable
fun ExercisePickerCard(
    exercise: Exercise,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onShowDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    DayTodayCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onShowDetail() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))

            ExerciseAnimationView(
                exerciseName = exercise.name,
                muscleGroup = exercise.muscleGroup,
                modifier = Modifier.size(56.dp),
                showLabel = false
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercise.muscleGroup} • ${exercise.equipment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DayTodayButton(
                text = if (isSelected) "Added" else "Add",
                onClick = onToggleSelect,
                buttonType = if (isSelected) ButtonType.Filled else ButtonType.Outlined
            )
        }
    }
}
