package com.toni.streaming.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An anime the user marked as favorite (heart) on the detail screen.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val animeId: String,
    val title: String,
    val imageUrl: String,
    val animeUrl: String,
    val coverUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
