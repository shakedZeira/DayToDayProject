package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "injuries",
    indices = [
        Index("injuryId", unique = true),
        Index("teamId")
    ]
)
data class InjuryEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "injury_id") val injuryId: String,
    @ColumnInfo(name = "player_id") val playerId: String,
    @ColumnInfo(name = "player_name") val playerName: String,
    @ColumnInfo(name = "team_id") val teamId: String,
    @ColumnInfo(name = "team_name") val teamName: String,
    val status: String,
    val description: String,
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "end_date") val endDate: Long?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Injury {
        return Injury(
            id = injuryId,
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
    }

    companion object {
        fun fromDomain(injury: Injury): InjuryEntity {
            return InjuryEntity(
                injuryId = injury.id,
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

data class Injury(
    val id: String,
    val playerId: String,
    val playerName: String,
    val teamId: String,
    val teamName: String,
    val status: String,
    val description: String,
    val startDate: Long,
    val endDate: Long?,
    val lastUpdated: Long
)