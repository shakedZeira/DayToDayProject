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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.daytoday.model.Injury
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.EmptyState
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbaInjuriesScreen(
    viewModel: NbaInjuriesViewModel = hiltViewModel(),
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "NBA Injuries",
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Home.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            NbaInjuriesTabContent(viewModel = viewModel)
        }
    }
}

@Composable
fun NbaInjuriesTabContent(
    viewModel: NbaInjuriesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val injuries by viewModel.injuries.collectAsStateWithLifecycle()
    val teamOptions by viewModel.teams.collectAsStateWithLifecycle()
    var selectedTeamFilter by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Team filter chips
            InjuryFilterChips(
                teams = teamOptions,
                selectedTeam = selectedTeamFilter,
                onTeamSelected = { teamId ->
                    selectedTeamFilter = teamId
                    viewModel.setTeamFilter(teamId)
                },
                onClearFilter = {
                    selectedTeamFilter = null
                    viewModel.clearTeamFilter()
                }
            )

            // Injuries list
            when (val state = injuries) {
                is UiState.Loading -> {
                    LoadingOverlay("Loading injuries...")
                }
                is UiState.Error -> {
                    ErrorState(state.message, viewModel::refresh)
                }
                is UiState.Success -> {
                    val injuryList = state.data
                    InjuriesContent(injuryList, selectedTeamFilter)
                }
                is UiState.Stale -> {
                    val injuryList = state.data
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
                                    text = "Showing cached injuries — pull to refresh",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        InjuriesContent(injuryList, selectedTeamFilter)
                    }
                }
            }
        }
    }
}

@Composable
private fun InjuriesContent(injuryList: List<Injury>, selectedTeamFilter: String?) {
    if (injuryList.isEmpty()) {
        EmptyState(
            icon = {
                Icon(
                    imageVector = Icons.Default.MedicalInformation,
                    contentDescription = "Injuries",
                    modifier = Modifier.size(64.dp)
                )
            },
            title = "No injuries",
            subtitle = selectedTeamFilter?.let { "No injuries for this team" }
                ?: "No injuries reported at this time"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(injuryList) { injury ->
                DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                    InjuryCard(injury = injury)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InjuryFilterChips(
    teams: List<Pair<String, String>>,
    selectedTeam: String?,
    onTeamSelected: (String?) -> Unit,
    onClearFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        teams.forEach { (label, token) ->
            TeamFilterChip(
                label = label,
                selected = if (token == ALL_TEAMS_FILTER) {
                    selectedTeam == null
                } else {
                    selectedTeam == token
                },
                onClick = { onTeamSelected(if (token == ALL_TEAMS_FILTER) null else token) }
            )
        }

        // Clear filter chip if filter is active
        if (selectedTeam != null) {
            TeamFilterChip(
                label = "Clear",
                selected = false,
                onClick = onClearFilter,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        },
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun InjuryCard(injury: Injury) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Player name and team
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = injury.playerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = injury.teamName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status badge
            InjuryStatusBadge(status = injury.status)
        }

        // Description
        Text(
            text = injury.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Since: ${formatDate(injury.startDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            injury.endDate?.let { endDate ->
                Text(
                    text = "Est. return: ${formatDate(endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Updated: ${formatDate(injury.lastUpdated)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InjuryStatusBadge(status: String) {
    val (color, label) = when (status.lowercase()) {
        "out" -> MaterialTheme.colorScheme.error to "Out"
        "questionable" -> MaterialTheme.colorScheme.tertiary to "Questionable"
        "doubtful" -> MaterialTheme.colorScheme.secondary to "Doubtful"
        "probable" -> MaterialTheme.colorScheme.primary to "Probable"
        "day-to-day" -> MaterialTheme.colorScheme.tertiary to "Day-to-Day"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to status
    }

    Card(
        modifier = Modifier.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun formatDate(timestamp: Long): String =
    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))