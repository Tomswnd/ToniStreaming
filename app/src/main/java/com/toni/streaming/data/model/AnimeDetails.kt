package com.toni.streaming.data.model

/**
 * Detailed information about an anime including metadata and episodes.
 */
data class AnimeDetails(
    val title: String,
    val plot: String,
    val imageUrl: String,
    val episodes: List<Episode>
)
