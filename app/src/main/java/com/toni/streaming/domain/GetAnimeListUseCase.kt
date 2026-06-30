package com.toni.streaming.domain

import com.toni.streaming.data.model.Anime
import com.toni.streaming.data.repository.AnimeRepository

/**
 * Use case for fetching the anime catalog.
 * Encapsulates pagination logic and result mapping.
 */
class GetAnimeListUseCase(
    private val repository: AnimeRepository
) {
    /**
     * Fetches a page of anime from the catalog.
     * @param page Page number (1-indexed)
     * @return Result containing list of Anime or an error
     */
    suspend operator fun invoke(page: Int = 1): Result<List<Anime>> {
        return repository.getAnimeList(page)
    }

    /**
     * Searches anime by title query.
     * @param query Search term
     * @return Result containing matching anime list
     */
    suspend fun search(query: String): Result<List<Anime>> {
        if (query.isBlank()) return Result.success(emptyList())
        return repository.searchAnime(query.trim())
    }
}
