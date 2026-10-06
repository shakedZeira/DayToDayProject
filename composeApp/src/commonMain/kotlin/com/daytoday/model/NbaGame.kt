package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class NbaGame(
    @SerialName("id") val id: String,
    @SerialName("homeTeam") val homeTeam: NbaTeam,
    @SerialName("awayTeam") val awayTeam: NbaTeam,
    @SerialName("homeScore") val homeScore: Int,
    @SerialName("awayScore") val awayScore: Int,
    @SerialName("status") val status: String,
    @SerialName("startTime") val startTime: Long,
    @SerialName("quarter") val quarter: Int,
    @SerialName("timeRemaining") val timeRemaining: String,
    @SerialName("isCompleted") val isCompleted: Boolean,
    @SerialName("boxScore") val boxScore: BoxScore?
)

@Serializable
data class NbaTeam(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("abbreviation") val abbreviation: String,
    @SerialName("city") val city: String,
    @SerialName("logoUrl") val logoUrl: String,
    @SerialName("primaryColor") val primaryColor: String,
    @SerialName("secondaryColor") val secondaryColor: String
)

@Serializable
data class BoxScore(
    @SerialName("gameId") val gameId: String,
    @SerialName("homeTeamStats") val homeTeamStats: TeamStats,
    @SerialName("awayTeamStats") val awayTeamStats: TeamStats,
    @SerialName("playerStats") val playerStats: List<PlayerStat>
)

@Serializable
data class TeamStats(
    @SerialName("teamId") val teamId: String,
    @SerialName("points") val points: Int,
    @SerialName("rebounds") val rebounds: Int,
    @SerialName("assists") val assists: Int,
    @SerialName("steals") val steals: Int,
    @SerialName("blocks") val blocks: Int,
    @SerialName("turnovers") val turnovers: Int,
    @SerialName("fgMade") val fgMade: Int,
    @SerialName("fgAttempted") val fgAttempted: Int,
    @SerialName("fg3Made") val fg3Made: Int,
    @SerialName("fg3Attempted") val fg3Attempted: Int,
    @SerialName("ftMade") val ftMade: Int,
    @SerialName("ftAttempted") val ftAttempted: Int,
    @SerialName("fastBreakPoints") val fastBreakPoints: Int,
    @SerialName("pointsInPaint") val pointsInPaint: Int,
    @SerialName("largestLead") val largestLead: Int
)

@Serializable
data class PlayerStat(
    @SerialName("playerId") val playerId: String,
    @SerialName("playerName") val playerName: String,
    @SerialName("teamId") val teamId: String,
    @SerialName("minutes") val minutes: String,
    @SerialName("points") val points: Int,
    @SerialName("rebounds") val rebounds: Int,
    @SerialName("assists") val assists: Int,
    @SerialName("steals") val steals: Int,
    @SerialName("blocks") val blocks: Int,
    @SerialName("turnovers") val turnovers: Int,
    @SerialName("fgMade") val fgMade: Int,
    @SerialName("fgAttempted") val fgAttempted: Int,
    @SerialName("fg3Made") val fg3Made: Int,
    @SerialName("fg3Attempted") val fg3Attempted: Int,
    @SerialName("ftMade") val ftMade: Int,
    @SerialName("ftAttempted") val ftAttempted: Int,
    @SerialName("plusMinus") val plusMinus: Int
)