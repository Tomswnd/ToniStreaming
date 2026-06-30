package com.toni.streaming.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val episodeId: String,
    val animeId: String,
    val watchedPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val animeTitle: String = "",
    val animeImageUrl: String = "",
    val animeSlug: String = ""
)
