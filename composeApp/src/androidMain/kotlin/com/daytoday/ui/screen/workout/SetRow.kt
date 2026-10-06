package com.daytoday.ui.screen.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daytoday.model.SetRecord
import com.daytoday.ui.theme.DayTodayCard

@Composable
fun SetRow(
    set: SetRecord,
    setNumber: Int,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRpeChange: (Double?) -> Unit,
    onComplete: () -> Unit
) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Set $setNumber", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Checkbox(
                    checked = set.isCompleted,
                    onCheckedChange = { if (it) onComplete() },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = set.weight.toString(),
                    onValueChange = { onWeightChange(it.toDoubleOrNull() ?: 0.0) },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = set.reps.toString(),
                    onValueChange = { onRepsChange(it.toIntOrNull() ?: 0) },
                    label = { Text("Reps") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = set.rpe?.toString() ?: "",
                    onValueChange = { onRpeChange(it.toDoubleOrNull()) },
                    label = { Text("RPE") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        }
    }
}