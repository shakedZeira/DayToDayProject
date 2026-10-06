package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json

@Entity(
    tableName = "nba_games",
    indices = [
        Index("gameId", unique = true),
        Index("startTime")
    ]
)
data class NbaGameEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "game_id") val gameId: String,
    val homeTeam: String,
    val awayTeam: String,
    @ColumnInfo(name = "home_score") val homeScore: Int?,
    @ColumnInfo(name = "away_score") val awayScore: Int?,
    val status: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    val quarter: Int?,
    @ColumnInfo(name = "time_remaining") val timeRemaining: String?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "box_score_json") val boxScoreJson: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): NbaGame {
        return NbaGame(
            id = gameId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homeScore = homeScore,
            awayScore = awayScore,
            status = status,
            startTime = startTime,
            quarter = quarter,
            timeRemaining = timeRemaining,
            isCompleted = isCompleted,
            boxScore = if (boxScoreJson != null) Json.decodeFromString(boxScoreJson) else null
        )
    }

    companion object {
        fun fromDomain(game: NbaGame): NbaGameEntity {
            return NbaGameEntity(
                gameId = game.id,
                homeTeam = game.homeTeam,
                awayTeam = game.awayTeam,
                homeScore = game.homeScore,
                awayScore = game.awayScore,
                status = game.status,
                startTime = game.startTime,
                quarter = game.quarter,
                timeRemaining = game.timeRemaining,
                isCompleted = game.isCompleted,
                boxScoreJson = game.boxScore?.let { Json.encodeToString(it) }
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class NbaGame(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String,
    val startTime: Long,
    val quarter: Int?,
    val timeRemaining: String?,
    val isCompleted: Boolean,
    val boxScore: BoxScore?
)

@kotlinx.serialization.Serializable
data class BoxScore(
    val homeTeamStats: TeamStats,
    val awayTeamStats: TeamStats,
    val playerStats: List<PlayerStats>
)

@kotlinx.serialization.Serializable
data class TeamStats(
    val points: Int,
    val rebounds: Int,
    val assists: Int,
    val steals: Int,
    val blocks: Int,
    val turnovers: Int
)

@kotlinx.serialization.Serializable
data class PlayerStats(
    val playerId: String,
    val playerName: String,
    val teamId: String,
    val points: Int,
    val rebounds: Int,
    val assists: Int,
    val minutes: String
)