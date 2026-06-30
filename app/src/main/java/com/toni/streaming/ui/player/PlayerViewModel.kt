package com.toni.streaming.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toni.streaming.data.model.Episode
import com.toni.streaming.data.model.StreamInfo
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val streamInfo: StreamInfo? = null,
    val startPositionMs: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val nextEpisode: Episode? = null,
    val currentEpisodeNumber: Int = 0,
    val currentEpisodeId: String = ""
)

class PlayerViewModel(
    private val repository: AnimeRepository,
    private val animeId: String,
    private var episodeId: String,
    private var episodeUrl: String
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadProgressAndExtractStream()
    }

    fun playEpisode(episode: Episode) {
        viewModelScope.launch {
            episodeId = episode.id
            episodeUrl = episode.url
            loadProgressAndExtractStream()
        }
    }

    fun retry() {
        loadProgressAndExtractStream()
    }

    private fun loadProgressAndExtractStream() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true, 
                error = null, 
                streamInfo = null, 
                nextEpisode = null,
                currentEpisodeId = episodeId
            ) }

            // Load saved watch progress
            var startPos = 0L
            try {
                repository.getWatchProgress(episodeId)?.let { progress ->
                    // Only resume if the user hasn't finished the episode (e.g. at least 15s remaining)
                    if (progress.totalDurationMs - progress.watchedPositionMs > 15_000) {
                        startPos = progress.watchedPositionMs
                        Log.d(TAG, "Loaded saved progress for episode $episodeId: $startPos ms")
                    } else {
                        Log.d(TAG, "Saved progress exists but episode is near completion. Starting from beginning.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load watch progress", e)
            }

            _uiState.update { it.copy(startPositionMs = startPos) }

            // Extract stream info
            repository.getStreamInfo(episodeUrl).fold(
                onSuccess = { streamInfo ->
                    Log.d(TAG, "Stream extracted: ${streamInfo.m3u8Url}")
                    _uiState.update {
                        it.copy(streamInfo = streamInfo, isLoading = false)
                    }
                    
                    // Fetch all episodes of the anime to find the next one!
                    fetchNextEpisode(streamInfo)
                },
                onFailure = { error ->
                    Log.e(TAG, "Stream extraction failed", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Impossibile estrarre lo stream"
                        )
                    }
                }
            )
        }
    }

    private fun fetchNextEpisode(streamInfo: StreamInfo) {
        viewModelScope.launch {
            try {
                val animeUrl = "https://www.animeunity.so/anime/${animeId}-${streamInfo.animeSlug}"
                Log.d(TAG, "Fetching episodes from: $animeUrl to find the next episode")
                
                repository.getAnimeDetails(animeUrl, animeId).fold(
                    onSuccess = { details ->
                        val episodes = details.episodes
                        // Find the current episode in the list
                        val currentEp = episodes.find { it.id == episodeId || it.url == episodeUrl }
                        
                        if (currentEp != null) {
                            _uiState.update { it.copy(currentEpisodeNumber = currentEp.number) }
                            
                            // Find the episode with number = currentEpisode.number + 1
                            val nextEp = episodes.find { it.number == currentEp.number + 1 }
                            if (nextEp != null) {
                                Log.i(TAG, "Found next episode: Ep. ${nextEp.number} (ID: ${nextEp.id})")
                                _uiState.update { it.copy(nextEpisode = nextEp) }
                            } else {
                                Log.i(TAG, "No next episode found (this might be the last episode).")
                            }
                        }
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Failed to fetch episodes list for next episode detection", err)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during fetchNextEpisode", e)
            }
        }
    }
}
