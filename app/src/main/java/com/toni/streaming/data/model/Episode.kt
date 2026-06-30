package com.toni.streaming.data.model

data class Episode(
    val id: String,
    val number: Int,
    val title: String = "",
    val url: String,
    val animeId: String,
    val imageUrl: String = ""
)
