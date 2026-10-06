package com.daytoday.ui.screen.nba

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.SubcomposeAsyncImage
import com.daytoday.model.BoxScore
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaTeam
import com.daytoday.model.PlayerStat
import com.daytoday.model.TeamStats
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbaGameDetailScreen(
    gameId: String,
    viewModel: NbaGameDetailViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val gameDetail by viewModel.gameDetail.collectAsStateWithLifecycle()

    LaunchedEffect(gameId) {
        viewModel.loadGameDetail(gameId)
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Game Detail",
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = gameDetail) {
                is UiState.Loading -> {
                    LoadingOverlay("Loading game details...")
                }
                is UiState.Error -> {
                    ErrorState(state.message, onRetry = { viewModel.refresh(gameId) })
                }
                is UiState.Success -> {
                    GameDetailContent(state.data)
                }
                is UiState.Stale -> {
                    val game = state.data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    text = "Showing cached game data — pull to refresh",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        GameDetailContent(game)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetailContent(game: NbaGame) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with teams and score
        GameHeader(game)

        // Game status
        GameStatusCard(game)

        // Box score if available
        game.boxScore?.let { boxScore ->
            BoxScoreSection(boxScore)
        }

        // Player stats if available
        game.boxScore?.playerStats?.let { playerStats ->
            if (playerStats.isNotEmpty()) {
                PlayerStatsSection(playerStats, game.homeTeam.id)
            }
        }
    }
}

@Composable
private fun GameHeader(game: NbaGame) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamHeaderView(team = game.awayTeam, score = game.awayScore)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = game.status,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (!game.isCompleted) {
                        Text(
                            text = "Q${game.quarter} ${game.timeRemaining}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TeamHeaderView(team = game.homeTeam, score = game.homeScore, isHome = true)
            }

            if (game.status == "Scheduled" || game.status == "Pre-Game") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsBasketball,
                        contentDescription = "Game time",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = formatGameTime(game.startTime),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TeamHeaderView(
    team: NbaTeam,
    score: Int,
    isHome: Boolean = false
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = if (isHome) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = if (isHome) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = team.logoUrl,
                contentDescription = team.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { TeamLogoPlaceholder(size = 48.dp) },
                error = { TeamLogoPlaceholder(size = 48.dp) }
            )
            Column(
                horizontalAlignment = if (isHome) Alignment.End else Alignment.Start,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "${team.city} ${team.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = team.abbreviation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.displaySmall,
color = 
parseHexColor(team.primaryColor),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GameStatusCard(game: NbaGame) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Game Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(game.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (!game.isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Quarter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Q${game.quarter}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Time Remaining", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(game.timeRemaining, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Final", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Game Completed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BoxScoreSection(boxScore: BoxScore) {
    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Box Score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            TeamBoxScore(boxScore.homeTeamStats, "Home")
            TeamBoxScore(boxScore.awayTeamStats, "Away")
        }
    }
}

@Composable
private fun TeamBoxScore(stats: TeamStats, label: String) {
    val rows = listOf(
        "Rebounds" to stats.rebounds.toString(),
        "Assists" to stats.assists.toString(),
        "Steals" to stats.steals.toString(),
        "Blocks" to stats.blocks.toString(),
        "Turnovers" to stats.turnovers.toString(),
        "FG" to "${stats.fgMade}/${stats.fgAttempted}",
        "3PT" to "${stats.fg3Made}/${stats.fg3Attempted}",
        "FT" to "${stats.ftMade}/${stats.ftAttempted}",
        "Fast Break Pts" to stats.fastBreakPoints.toString(),
        "Points in Paint" to stats.pointsInPaint.toString(),
        "Largest Lead" to stats.largestLead.toString()
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        rows.forEach { (name, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PlayerStatsSection(playerStats: List<PlayerStat>, homeTeamId: String) {
    val homePlayers = playerStats.filter { it.teamId == homeTeamId }
    val awayPlayers = playerStats.filter { it.teamId != homeTeamId }

    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Player Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            PlayerTeamStats("Home", homePlayers)
            Spacer(modifier = Modifier.height(8.dp))
            PlayerTeamStats("Away", awayPlayers)
        }
    }
}

@Composable
private fun PlayerTeamStats(label: String, players: List<PlayerStat>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Player", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("MIN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            Text("PTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            Text("REB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            Text("AST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
            Text("+/-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
        }

        players.sortedByDescending { it.points }.forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = player.playerName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(player.minutes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                Text(
                    text = player.points.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp)
                )
                Text(player.rebounds.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                Text(player.assists.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                Text(
                    text = if (player.plusMinus > 0) "+${player.plusMinus}" else player.plusMinus.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (player.plusMinus > 0) MaterialTheme.colorScheme.primary
                    else if (player.plusMinus < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamLogoPlaceholder(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SportsBasketball,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

private fun formatGameTime(timestamp: Long): String =
    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))