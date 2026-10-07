package com.daytoday.ui.screen.nba

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaTeam
import com.daytoday.ui.theme.DayTodayCard

@Composable
fun GameCard(
    game: NbaGame,
    onClick: () -> Unit
) {
    DayTodayCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away team
                TeamView(team = game.awayTeam, score = game.awayScore, isHome = false)

                // Status / Time
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = game.status,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (!game.isCompleted) {
                        Text(
                            text = "Q${game.quarter} ${game.timeRemaining}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Final",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Home team
                TeamView(team = game.homeTeam, score = game.homeScore, isHome = true)
            }

            // Game time (if not started)
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
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatGameTime(game.startTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TeamView(
    team: NbaTeam,
    score: Int,
    isHome: Boolean
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp),
        horizontalAlignment = if (isHome) Alignment.End else Alignment.Start
    ) {
        Row(
            horizontalArrangement = if (isHome) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = team.logoUrl,
                contentDescription = team.name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = { TeamLogoFallback() },
                error = { TeamLogoFallback() }
            )
            Text(
                text = team.abbreviation,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = parseHexColor(team.primaryColor),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(IntrinsicSize.Min)
        )
    }
}

@Composable
internal fun TeamLogoFallback(size: Dp = 32.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SportsBasketball,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2)
        )
    }
}

/**
 * Team colors arrive from the API as arbitrary hex strings, so an unparseable value must not
 * crash the composition. Accepts `RRGGBB`, `#RRGGBB` and `AARRGGBB`.
 */
internal fun parseHexColor(hex: String, fallback: Color = Color(0xFF9E9E9E)): Color {
    val digits = hex.trim().removePrefix("#")
    val argb = runCatching {
        when (digits.length) {
            6 -> (0xFF000000L or digits.toLong(16)).toInt()
            8 -> digits.toLong(16).toInt()
            else -> null
        }
    }.getOrNull() ?: return fallback
    return Color(argb)
}

private fun formatGameTime(timestamp: Long): String =
    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
