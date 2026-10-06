package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json

@Entity(
    tableName = "nba_news",
    indices = [
        Index("newsId", unique = true),
        Index("publishedAt")
    ]
)
data class NbaNewsEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "news_id") val newsId: String,
    val title: String,
    val summary: String,
    val url: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val source: String,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "team_ids_json") val teamIdsJson: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): NbaNews {
        return NbaNews(
            id = newsId,
            title = title,
            summary = summary,
            url = url,
            imageUrl = imageUrl,
            source = source,
            publishedAt = publishedAt,
            teamIds = teamIdsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        )
    }

    companion object {
        fun fromDomain(news: NbaNews): NbaNewsEntity {
            return NbaNewsEntity(
                newsId = news.id,
                title = news.title,
                summary = news.summary,
                url = news.url,
                imageUrl = news.imageUrl,
                source = news.source,
                publishedAt = news.publishedAt,
                teamIdsJson = news.teamIds.let { if (it.isNotEmpty()) Json.encodeToString(it) else null }
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class NbaNews(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val source: String,
    val publishedAt: Long,
    val teamIds: List<String>
)