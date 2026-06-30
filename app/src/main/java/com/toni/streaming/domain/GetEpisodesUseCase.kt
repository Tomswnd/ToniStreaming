package com.toni.streaming.domain

import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.repository.AnimeRepository

/**
 * Use case for fetching the episode list of a specific anime.
 */
class GetEpisodesUseCase(
    private val repository: AnimeRepository
) {
    /**
     * Fetches all episodes for the given anime.
     * @param animeUrl Full URL of the anime page on AnimeUnity
     * @param animeId Unique identifier of the anime
     * @return Result containing sorted list of Episodes
     */
    suspend operator fun invoke(animeUrl: String, animeId: String): Result<List<Episode>> {
        return repository.getEpisodes(animeUrl, animeId)
    }
}
