package com.toni.streaming.data.model

data class Anime(
    val id: String,
    val title: String,
    val imageUrl: String,
    val coverUrl: String? = null,
    val synopsis: String = "",
    val status: String = "",
    val type: String = "",
    val episodeCount: Int = 0,
    val genres: List<String> = emptyList(),
    val rating: Float = 0f,
    val year: Int = 0,
    val slug: String = "",
    val episodeUrl: String = ""
)
