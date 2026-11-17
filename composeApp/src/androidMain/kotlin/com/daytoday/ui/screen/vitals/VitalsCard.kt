package com.daytoday.ui.screen.vitals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daytoday.data.health.VitalsKind
import com.daytoday.data.health.VitalsLoad
import com.daytoday.data.health.VitalsPermissions
import com.daytoday.data.health.VitalsSnapshot
import com.daytoday.data.health.VitalsUnavailable
import com.daytoday.data.health.formatSleep
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import java.util.Locale

/**
 * Weight, sleep and heart rate from Health Connect. Handles every state the user can be in:
 * Health Connect missing, nothing granted yet, partly granted, or a failed read.
 */
@Composable
fun VitalsCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    viewModel: VitalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    DayTodayCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Body & recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (val result = state.result) {
                null -> Text("Reading Health Connect...", style = MaterialTheme.typography.bodySmall)
                is VitalsLoad.Unavailable -> Text(
                    when (result.reason) {
                        VitalsUnavailable.NOT_INSTALLED ->
                            "Install or update Health Connect to see weight, sleep and heart rate."
                        VitalsUnavailable.UNSUPPORTED ->
                            "Health Connect is not supported on this device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VitalsLoad.NeedsPermission -> {
                    Text(
                        "Allow DayToDay to read weight, sleep and heart rate from Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DayTodayButton(
                        text = "Connect",
                        onClick = { runCatching { launcher.launch(VitalsPermissions.all) } },
                        buttonType = ButtonType.Filled,
                    )
                }
                is VitalsLoad.Failed -> {
                    Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    DayTodayButton(text = "Retry", onClick = viewModel::refresh)
                }
                is VitalsLoad.Ready -> {
                    VitalsRows(result.snapshot, compact)
                    if (VitalsPermissions.all.size > result.snapshot.grantedKinds.size) {
                        DayTodayButton(
                            text = "Allow more",
                            onClick = { runCatching { launcher.launch(VitalsPermissions.all) } },
                        )
                    }
                    if (!compact) {
                        Text(
                            "Read from Health Connect and kept on this device only.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsRows(snapshot: VitalsSnapshot, compact: Boolean) {
    val today = snapshot.today
    val weight = when {
        VitalsKind.WEIGHT !in snapshot.grantedKinds -> "Not connected"
        snapshot.latestWeightKg == null -> "No data"
        else -> weightText(snapshot.latestWeightKg, snapshot.weightChangeKg.takeUnless { compact })
    }
    val sleep = when {
        VitalsKind.SLEEP !in snapshot.grantedKinds -> "Not connected"
        today.sleepMinutes == null -> "No data"
        else -> formatSleep(today.sleepMinutes)
    }
    val heart = when {
        VitalsKind.HEART_RATE !in snapshot.grantedKinds -> "Not connected"
        today.heartRate == null -> "No data"
        compact -> "${today.heartRate.average} bpm"
        else -> "avg ${today.heartRate.average} / resting ${today.heartRate.resting} bpm"
    }
    VitalRow("Weight", weight)
    VitalRow("Sleep (last night)", sleep)
    VitalRow("Heart rate (today)", heart)
}

internal fun weightText(kg: Double, change: Double?): String {
    val base = String.format(Locale.getDefault(), "%.1f kg", kg)
    return if (change == null) base else base + String.format(Locale.getDefault(), " (%+.1f in 7d)", change)
}

@Composable
private fun VitalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
