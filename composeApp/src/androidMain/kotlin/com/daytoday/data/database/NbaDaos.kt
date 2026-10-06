package com.daytoday.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NbaGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NbaGameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NbaGameEntity)

    @Query("SELECT * FROM nba_games WHERE date = :date")
    suspend fun getGamesByDate(date: String): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games")
    suspend fun getAllGames(): List<NbaGameEntity>

    @Query("SELECT * FROM nba_games WHERE id = :gameId")
    suspend fun getGameById(gameId: String): NbaGameEntity?

    @Query("DELETE FROM nba_games WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM nba_games")
    suspend fun deleteAll()
}

@Dao
interface NbaNewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NbaNewsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NbaNewsEntity)

    @Query("SELECT * FROM nba_news ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getLatestNews(limit: Int): List<NbaNewsEntity>

    @Query("SELECT * FROM nba_news")
    suspend fun getAllNews(): List<NbaNewsEntity>

    @Query("SELECT * FROM nba_news WHERE id = :newsId")
    suspend fun getNewsById(newsId: String): NbaNewsEntity?

    @Query("DELETE FROM nba_news")
    suspend fun deleteAll()
}

@Dao
interface InjuryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<InjuryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InjuryEntity)

    @Query("SELECT * FROM injuries WHERE teamId = :teamId")
    suspend fun getInjuriesByTeam(teamId: String): List<InjuryEntity>

    @Query("SELECT * FROM injuries")
    suspend fun getAllInjuries(): List<InjuryEntity>

    @Query("SELECT * FROM injuries WHERE teamId = :teamId")
    suspend fun getCachedInjuries(teamId: String): List<InjuryEntity>

    @Query("SELECT * FROM injuries")
    suspend fun getCachedAllInjuries(): List<InjuryEntity>

    @Query("DELETE FROM injuries")
    suspend fun deleteAll()
}