package com.toni.streaming.data.model

data class StreamInfo(
    val m3u8Url: String,
    val referer: String,
    val userAgent: String = "",
    val headers: Map<String, String> = emptyMap(),
    val animeTitle: String = "",
    val animeImageUrl: String = "",
    val animeSlug: String = ""
)
