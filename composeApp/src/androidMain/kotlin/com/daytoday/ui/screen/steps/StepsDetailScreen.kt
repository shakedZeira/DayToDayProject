package com.daytoday.ui.screen.steps

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import com.daytoday.usecase.StepsAggregator
import com.daytoday.usecase.StepsHistory
import com.daytoday.usecase.StepsPeriod
import com.daytoday.util.formatCalories
import com.daytoday.util.formatDistanceKm
import com.daytoday.util.formatSteps
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StepsDetailScreen(
    viewModel: StepsDetailViewModel = hiltViewModel(),
    navController: NavController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    val permissions = remember {
        setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onPermissionResult(
            granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Steps",
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                StepsUiState.Loading -> LoadingOverlay("Loading steps...")
                StepsUiState.PermissionMissing -> StepsPermissionState(
                    onConnect = {
                        try {
                            launcher.launch(permissions)
                        } catch (_: Exception) {
                            viewModel.requestPermission()
                        }
                    }
                )
                is StepsUiState.Error -> ErrorState(state.message, onRetry = { viewModel.refresh() })
                is StepsUiState.Success -> StepsContent(
                    history = state.history,
                    period = period,
                    onPeriodSelected = viewModel::selectPeriod,
                    onEditGoal = { navController.navigate(Screen.Goals.route) },
                )
            }
        }
    }
}

@Composable
private fun StepsPermissionState(onConnect: () -> Unit) {
    EmptyState(
        icon = { Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.height(64.dp)) },
        title = "Connect Health Connect",
        subtitle = "Allow step access to see your daily and weekly history. Nothing leaves your device.",
        action = { DayTodayButton(text = "Connect Health Connect", onClick = onConnect) },
    )
}

@Composable
private fun StepsContent(
    history: StepsHistory,
    period: StepsPeriod,
    onPeriodSelected: (StepsPeriod) -> Unit,
    onEditGoal: () -> Unit,
) {
    var selectedIndex by remember(history) {
        mutableIntStateOf(
            history.buckets.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: history.buckets.lastIndex.coerceAtLeast(0)
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepsPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { onPeriodSelected(p) },
                        label = { Text(p.label) },
                    )
                }
            }
        }
        item {
            DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StepsGoalRing(todaySteps = history.todaySteps, goal = history.goal)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Today", style = MaterialTheme.typography.titleMedium)
                        Text(formatSteps(history.todaySteps), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${StepsAggregator.percentage(history.todaySteps, history.goal)}% of ${formatSteps(history.goal)} goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!history.todayHasData) {
                            Text("No data from Health Connect today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = onEditGoal) {
                            Text("Edit goal")
                        }
                    }
                }
            }
        }
        item {
            DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(periodTitle(period), style = MaterialTheme.typography.titleLarge)
                    Text("${formatSteps(history.stats.total)} / ${formatSteps(history.paceTarget)} steps", style = MaterialTheme.typography.bodyLarge)
                    val progress = if (history.paceTarget > 0L) (history.stats.total.toFloat() / history.paceTarget.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${StepsAggregator.percentage(history.stats.total, history.paceTarget)}% of target (goal × ${history.stats.elapsedDays} days)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val cp = history.changePercent
                    if (cp != null) {
                        val sign = if (cp >= 0) "+" else ""
                        Text("$sign$cp% vs previous ${period.label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = if (cp >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    } else {
                        Text("No previous ${period.label.lowercase()} data to compare", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("≈ ${formatDistanceKm(history.totalDistanceKm)}", style = MaterialTheme.typography.bodyMedium)
                        Text("≈ ${formatCalories(history.totalCalories)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Estimates from height and weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Steps trend", style = MaterialTheme.typography.titleLarge)
                    StepsBarChart(
                        buckets = history.buckets,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                    )
                    StepsChartLabels(buckets = history.buckets, selectedIndex = selectedIndex)
                    val bucket = history.buckets.getOrNull(selectedIndex)
                    if (bucket != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (period == StepsPeriod.YEAR) {
                                Text("${bucket.label} · ${formatSteps(bucket.steps)} steps · goal ${formatSteps(bucket.goal)}", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text("${bucket.label} · ${formatSteps(bucket.steps)} / ${formatSteps(bucket.goal)} (${StepsAggregator.percentage(bucket.steps, bucket.goal)}%)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!bucket.hasData) {
                                Text(" · no data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Average", if (period == StepsPeriod.YEAR) "${formatSteps(history.stats.averagePerDay)}\nper day*" else "${formatSteps(history.stats.averagePerDay)}\nper day", modifier = Modifier.weight(1f))
                val bestLabel = if (history.stats.bestDate != null) formatStatsBestLabel(period, history.stats.bestDate) else ""
                StatCard("Best", if (history.stats.bestDate != null) "${formatSteps(history.stats.bestSteps ?: 0)}\n$bestLabel" else "—", modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", formatSteps(history.stats.total), modifier = Modifier.weight(1f))
                val goalMetText = if (history.stats.daysGoalMet != null) {
                    "${history.stats.daysGoalMet} / ${history.stats.elapsedDays} days"
                } else {
                    "Best month: ${history.stats.bestSteps?.let { formatSteps(it) } ?: "—"}"
                }
                StatCard("Goal met", goalMetText, modifier = Modifier.weight(1f))
            }
        }
        if (period == StepsPeriod.YEAR) {
            item {
                Text("* includes today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text(if (period == StepsPeriod.YEAR) "Recent months" else "Recent days", style = MaterialTheme.typography.titleLarge)
        }
        val recent = history.listRows.takeLast(if (period == StepsPeriod.YEAR) 6 else 7).reversed()
        if (recent.isEmpty()) {
            item {
                Text("No step history yet for this period.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(count = recent.size) { idx ->
                val row = recent[idx]
                DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(if (row.hasData) formatSteps(row.steps) else "—", style = MaterialTheme.typography.bodyMedium)
                        }
                        val p = if (row.goal > 0L && row.hasData) (row.steps.toFloat() / row.goal.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
        if (history.stats.total == 0L) {
            item {
                DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No steps recorded in this period. Samsung Health may not have synced yet.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { /* refresh handled by screen */ }) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    DayTodayCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
        }
    }
}

private fun periodTitle(period: StepsPeriod): String = when (period) {
    StepsPeriod.DAY -> "Today"
    StepsPeriod.WEEK -> "This week"
    StepsPeriod.MONTH -> "This month"
    StepsPeriod.YEAR -> "This year"
}

private fun formatStatsBestLabel(period: StepsPeriod, date: LocalDate): String = when (period) {
    StepsPeriod.YEAR -> date.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}
