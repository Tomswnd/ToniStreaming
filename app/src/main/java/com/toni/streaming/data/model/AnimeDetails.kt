package com.toni.streaming.data.model

/**
 * A related anime (other season, movie, OVA or special) linked from an anime's page.
 */
data class RelatedAnime(
    val id: String,
    val slug: String,
    val title: String,
    val imageUrl: String,
    val url: String,
    /** Raw info line from the source, e.g. "TV - 2002". */
    val info: String = "",
    /** Release year parsed from [info], used for chronological ordering (0 if unknown). */
    val year: Int = 0
)

/**
 * Detailed information about an anime including metadata and episodes.
 */
data class AnimeDetails(
    val title: String,
    val plot: String,
    val imageUrl: String,
    /** Wide horizontal banner (AniList cover), better suited to backdrops than [imageUrl]. */
    val coverUrl: String? = null,
    val episodes: List<Episode>,
    /** AnimeUnity rating/score (0 if unknown). */
    val score: Float = 0f,
    /** Total episodes available on the source. May exceed [episodes], which only holds the first page. */
    val totalEpisodes: Int = episodes.size,
    /** Other seasons / movies / specials, in chronological release order. */
    val related: List<RelatedAnime> = emptyList()
)
