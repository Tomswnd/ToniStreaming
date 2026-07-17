package com.toni.streaming.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toni.streaming.data.local.WatchHistoryEntity
import com.toni.streaming.data.model.AnimeDetails
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI representation of an episode coupled with its watch progress history.
 */
data class EpisodeWithProgress(
    val episode: Episode,
    val progress: WatchHistoryEntity? = null
)

data class DetailUiState(
    val animeDetails: AnimeDetails? = null,
    val episodes: List<EpisodeWithProgress> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalEpisodes: Int = 0,
    val isLoadingMore: Boolean = false,
    val isFavorite: Boolean = false,
    /** Set once a "load more" call returns nothing, to stop offering a dead button. */
    val reachedEnd: Boolean = false
) {
    /**
     * Only offer "load more" when the first embedded page is full (~120): a shorter first page
     * means the site already returned every episode, so there is nothing more to fetch even if
     * episodes_count claims a higher number (e.g. count says 25 but only 24 exist).
     */
    val canLoadMore: Boolean
        get() = !reachedEnd && episodes.size >= PAGE_SIZE && episodes.size < totalEpisodes
    /** Episode number range that the "load more" action will fetch next. */
    val nextRangeStart: Int get() = (episodes.maxOfOrNull { it.episode.number } ?: episodes.size) + 1
    val nextRangeEnd: Int get() = minOf(nextRangeStart + PAGE_SIZE - 1, totalEpisodes)
}

private const val PAGE_SIZE = 120

class DetailViewModel(
    private val repository: AnimeRepository,
    private val animeUrl: String,
    private val animeId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
        observeFavorite()
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            repository.isFavorite(animeId).collect { fav ->
                _uiState.update { it.copy(isFavorite = fav) }
            }
        }
    }

    fun toggleFavorite() {
        val details = _uiState.value.animeDetails ?: return
        viewModelScope.launch {
            repository.toggleFavorite(
                animeId = animeId,
                title = details.title,
                imageUrl = details.imageUrl,
                animeUrl = animeUrl,
                coverUrl = details.coverUrl
            )
        }
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.getAnimeDetails(animeUrl, animeId).fold(
                onSuccess = { details ->
                    // Load watch progress database entries for this anime
                    val progressList = repository.getWatchHistoryForAnime(animeId)
                    val progressMap = progressList.associateBy { it.episodeId }

                    val episodesWithProgress = details.episodes.map { episode ->
                        EpisodeWithProgress(
                            episode = episode,
                            progress = progressMap[episode.id]
                        )
                    }

                    _uiState.update {
                        it.copy(
                            animeDetails = details,
                            episodes = episodesWithProgress,
                            totalEpisodes = details.totalEpisodes,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Errore nel caricamento dei dettagli"
                        )
                    }
                }
            )
        }
    }

    /**
     * Lazily loads the next page (~120) of episodes and appends them to the list.
     */
    fun loadMoreEpisodes() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore) return

        val start = state.nextRangeStart
        val end = state.nextRangeEnd

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            repository.getEpisodesRange(animeId, animeUrl, start, end).fold(
                onSuccess = { newEpisodes ->
                    val progressMap = repository.getWatchHistoryForAnime(animeId)
                        .associateBy { it.episodeId }

                    _uiState.update { current ->
                        val existingIds = current.episodes.mapTo(HashSet()) { it.episode.id }
                        val appended = newEpisodes
                            .filter { it.id !in existingIds }
                            .map { EpisodeWithProgress(it, progressMap[it.id]) }

                        val merged = (current.episodes + appended)
                            .sortedBy { it.episode.number }

                        current.copy(
                            episodes = merged,
                            isLoadingMore = false,
                            // Nothing new came back -> stop offering the button (count was over-reported).
                            reachedEnd = appended.isEmpty()
                        )
                    }
                },
                onFailure = { error ->
                    Log.e("DetailViewModel", "loadMoreEpisodes failed", error)
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    fun retry() {
        loadDetails()
    }
}
