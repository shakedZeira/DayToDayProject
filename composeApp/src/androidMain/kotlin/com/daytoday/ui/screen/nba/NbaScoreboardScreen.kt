package com.daytoday.ui.screen.nba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbaScoreboardScreen(
    scoreboardViewModel: NbaScoreboardViewModel = hiltViewModel(),
    newsViewModel: NbaNewsViewModel = hiltViewModel(),
    injuriesViewModel: NbaInjuriesViewModel = hiltViewModel(),
    navController: NavHostController,
    initialTab: Int = 0
) {
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            Column {
                DayTodayTopAppBar(
                    title = when (selectedTab) {
                        0 -> "NBA Scoreboard"
                        1 -> "NBA News"
                        else -> "NBA Injuries"
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate(Screen.Home.route) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            when (selectedTab) {
                                0 -> scoreboardViewModel.refresh()
                                1 -> newsViewModel.refresh()
                                else -> injuriesViewModel.refresh()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Scoreboard",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SportsBasketball,
                                contentDescription = "Scoreboard"
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "News",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Article,
                                contentDescription = "News"
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                text = "Injuries",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.MedicalInformation,
                                contentDescription = "Injuries"
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ScoreboardTabContent(
                    viewModel = scoreboardViewModel,
                    navController = navController
                )
                1 -> NbaNewsTabContent(
                    viewModel = newsViewModel
                )
                2 -> NbaInjuriesTabContent(
                    viewModel = injuriesViewModel
                )
            }
        }
    }
}

/**
 * Unified NBA screen entry point.
 */
@Composable
fun NbaScreen(
    scoreboardViewModel: NbaScoreboardViewModel = hiltViewModel(),
    newsViewModel: NbaNewsViewModel = hiltViewModel(),
    injuriesViewModel: NbaInjuriesViewModel = hiltViewModel(),
    navController: NavHostController,
    initialTab: Int = 0
) = NbaScoreboardScreen(
    scoreboardViewModel = scoreboardViewModel,
    newsViewModel = newsViewModel,
    injuriesViewModel = injuriesViewModel,
    navController = navController,
    initialTab = initialTab
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardTabContent(
    viewModel: NbaScoreboardViewModel = hiltViewModel(),
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val scoreboard by viewModel.scoreboard.collectAsStateWithLifecycle()
    var selectedDate by remember {
        mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.of(
                selectedDate.year,
                selectedDate.monthNumber,
                selectedDate.dayOfMonth
            ).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                DayTodayButton(
                    text = "OK",
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val instant = Instant.fromEpochMilliseconds(millis)
                            val newDate = instant.toLocalDateTime(TimeZone.UTC).date
                            selectedDate = newDate
                            viewModel.setDate(newDate)
                        }
                        showDatePicker = false
                    }
                )
            },
            dismissButton = {
                DayTodayButton(
                    text = "Cancel",
                    onClick = { showDatePicker = false },
                    buttonType = ButtonType.Text
                )
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Date picker row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Date:", style = MaterialTheme.typography.titleMedium)

                // Date display with picker
                Card(
                    modifier = Modifier.weight(1f),
                    onClick = { showDatePicker = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDate.toDisplayLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Select date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DayTodayButton(
                    text = "Today",
                    onClick = {
                        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                        selectedDate = today
                        viewModel.setDate(today)
                    },
                    buttonType = ButtonType.Outlined
                )
            }

            // Games list
            when (val state = scoreboard) {
                is UiState.Loading -> {
                    LoadingOverlay("Loading games...")
                }
                is UiState.Error -> {
                    ErrorState(state.message, viewModel::refresh)
                }
                is UiState.Success -> {
                    val games = state.data
                    if (games.isEmpty()) {
                        EmptyState(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.SportsBasketball,
                                    contentDescription = "NBA",
                                    modifier = Modifier.size(64.dp)
                                )
                            },
                            title = "No games",
                            subtitle = "No games scheduled for ${selectedDate.toDisplayLabel()}"
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(games) { game ->
                                    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                                        GameCard(
                                            game = game,
                                            onClick = {
                                                navController.navigate(
                                                    "${Screen.NbaGameDetail.route}/${game.id}"
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is UiState.Stale -> {
                    val games = state.data
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stale data indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Stale data",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Showing cached data — pull to refresh",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        if (games.isEmpty()) {
                            EmptyState(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.SportsBasketball,
                                        contentDescription = "NBA",
                                        modifier = Modifier.size(64.dp)
                                    )
                                },
                                title = "No games",
                                subtitle = "No games scheduled for ${selectedDate.toDisplayLabel()}"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(games) { game ->
                                    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                                        GameCard(
                                            game = game,
                                            onClick = {
                                                navController.navigate(
                                                    "${Screen.NbaGameDetail.route}/${game.id}"
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * kotlinx-datetime does not ship pattern based formatting in this module, so the label is
 * rendered through java.time (available from minSdk 26).
 */
private fun LocalDate.toDisplayLabel(): String =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
