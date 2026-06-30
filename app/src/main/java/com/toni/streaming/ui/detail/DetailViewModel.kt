package com.toni.streaming.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toni.streaming.data.local.WatchHistoryEntity
import com.toni.streaming.data.model.AnimeDetails
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null
)

class DetailViewModel(
    private val repository: AnimeRepository,
    private val animeUrl: String,
    private val animeId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
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

    fun retry() {
        loadDetails()
    }
}
