package com.toni.streaming.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Upsert
    suspend fun upsert(entity: WatchHistoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WatchHistoryEntity>)

    @Query("SELECT * FROM watch_history")
    suspend fun getAllNow(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT 20")
    fun getRecentlyWatched(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId")
    suspend fun getByAnimeId(animeId: String): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)
}
