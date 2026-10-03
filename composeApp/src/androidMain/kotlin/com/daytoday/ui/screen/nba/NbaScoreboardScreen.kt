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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbaScoreboardScreen(
    viewModel: NbaScoreboardViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val scoreboard by viewModel.scoreboard.collectAsStateWithLifecycle()
    var selectedDate by remember {
        mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "NBA Scoreboard",
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        onClick = { /* TODO: Show date picker dialog */ },
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
                            // Stale data indicator — data is from a previous fetch;
                            // pull to refresh or change the date to get fresh scores.
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
}

/**
 * kotlinx-datetime does not ship pattern based formatting in this module, so the label is
 * rendered through java.time (available from minSdk 26).
 */
private fun LocalDate.toDisplayLabel(): String =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))