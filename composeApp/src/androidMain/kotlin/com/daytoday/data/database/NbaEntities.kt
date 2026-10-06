package com.daytoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "nba_games")
data class NbaGameEntity(
    @PrimaryKey val id: String,
    val homeTeamJson: String,
    val awayTeamJson: String,
    val homeScore: Int,
    val awayScore: Int,
    val status: String,
    val startTime: Long,
    val quarter: Int,
    val timeRemaining: String,
    val isCompleted: Boolean,
    val boxScoreJson: String?,
    val date: String
) {
    fun toModel(): com.daytoday.model.NbaGame = com.daytoday.data.database.NbaGameEntity.toModel(this)
    
    companion object {
        fun fromModel(game: com.daytoday.model.NbaGame, date: String): NbaGameEntity {
            return NbaGameEntity(
                id = game.id,
                homeTeamJson = game.homeTeam.toJson(),
                awayTeamJson = game.awayTeam.toJson(),
                homeScore = game.homeScore,
                awayScore = game.awayScore,
                status = game.status,
                startTime = game.startTime,
                quarter = game.quarter,
                timeRemaining = game.timeRemaining,
                isCompleted = game.isCompleted,
                boxScoreJson = game.boxScore?.toJson(),
                date = date
            )
        }
        
        fun toModel(entity: NbaGameEntity): com.daytoday.model.NbaGame {
            return com.daytoday.model.NbaGame(
                id = entity.id,
                homeTeam = entity.homeTeamJson.fromJson(),
                awayTeam = entity.awayTeamJson.fromJson(),
                homeScore = entity.homeScore,
                awayScore = entity.awayScore,
                status = entity.status,
                startTime = entity.startTime,
                quarter = entity.quarter,
                timeRemaining = entity.timeRemaining,
                isCompleted = entity.isCompleted,
                boxScore = entity.boxScoreJson?.fromJson()
            )
        }
    }
}

@Entity(tableName = "nba_news")
data class NbaNewsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val publishedAt: Long,
    val teamIdsJson: String
) {
    fun toModel(): com.daytoday.model.NbaNews = com.daytoday.data.database.NbaNewsEntity.toModel(this)
    
    companion object {
        fun fromModel(news: com.daytoday.model.NbaNews): NbaNewsEntity {
            return NbaNewsEntity(
                id = news.id,
                title = news.title,
                summary = news.summary,
                url = news.url,
                imageUrl = news.imageUrl,
                source = news.source,
                publishedAt = news.publishedAt,
                teamIdsJson = news.teamIds.toJson()
            )
        }
        
        fun toModel(entity: NbaNewsEntity): com.daytoday.model.NbaNews {
            return com.daytoday.model.NbaNews(
                id = entity.id,
                title = entity.title,
                summary = entity.summary,
                url = entity.url,
                imageUrl = entity.imageUrl,
                source = entity.source,
                publishedAt = entity.publishedAt,
                teamIds = entity.teamIdsJson.fromJson()
            )
        }
    }
}

@Entity(tableName = "injuries")
data class InjuryEntity(
    @PrimaryKey val id: String,
    val playerId: String,
    val playerName: String,
    val teamId: String,
    val teamName: String,
    val status: String,
    val description: String,
    val startDate: Long,
    val endDate: Long?,
    val lastUpdated: Long
) {
    fun toModel(): com.daytoday.model.Injury = com.daytoday.model.Injury(
        id = id,
        playerId = playerId,
        playerName = playerName,
        teamId = teamId,
        teamName = teamName,
        status = status,
        description = description,
        startDate = startDate,
        endDate = endDate,
        lastUpdated = lastUpdated
    )
    
    companion object {
        fun fromModel(injury: com.daytoday.model.Injury): InjuryEntity {
            return InjuryEntity(
                id = injury.id,
                playerId = injury.playerId,
                playerName = injury.playerName,
                teamId = injury.teamId,
                teamName = injury.teamName,
                status = injury.status,
                description = injury.description,
                startDate = injury.startDate,
                endDate = injury.endDate,
                lastUpdated = injury.lastUpdated
            )
        }
    }
}

class Converters {
}