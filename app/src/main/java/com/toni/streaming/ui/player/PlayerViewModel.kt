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
    private var episodeUrl: String,
    private var episodeNumber: Int = 0,
    /**
     * Optional hook that resolves a locally downloaded copy of an episode.
     * When it returns a StreamInfo, network extraction is skipped entirely,
     * so downloaded episodes play even without a connection.
     */
    private val offlineStreamResolver: (suspend (episodeId: String) -> StreamInfo?)? = null
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
            episodeNumber = episode.number
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
                currentEpisodeId = episodeId,
                currentEpisodeNumber = episodeNumber
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

            // If the episode was downloaded, play the local copy and skip extraction.
            try {
                offlineStreamResolver?.invoke(episodeId)?.let { offlineInfo ->
                    Log.i(TAG, "Playing episode $episodeId from local download")
                    _uiState.update { it.copy(streamInfo = offlineInfo, isLoading = false) }
                    // Next-episode lookup needs the network; if offline it fails silently.
                    fetchNextEpisode(offlineInfo)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline stream resolution failed, falling back to network", e)
            }

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
                Log.d(TAG, "Resolving next episode for anime $animeId (current ep number: $episodeNumber)")

                // Only the first page is fetched here (fast). It also gives us the real total.
                repository.getAnimeDetails(animeUrl, animeId).fold(
                    onSuccess = { details ->
                        val firstPage = details.episodes

                        // Prefer the episode number passed via navigation; fall back to locating
                        // the current episode inside the first page if it wasn't provided.
                        val currentNumber = if (episodeNumber > 0) {
                            episodeNumber
                        } else {
                            firstPage.find { it.id == episodeId || it.url == episodeUrl }?.number ?: 0
                        }

                        if (currentNumber <= 0) {
                            Log.w(TAG, "Could not determine current episode number; skipping next-episode lookup")
                            return@fold
                        }

                        _uiState.update { it.copy(currentEpisodeNumber = currentNumber) }

                        val nextNumber = currentNumber + 1
                        if (nextNumber > details.totalEpisodes) {
                            Log.i(TAG, "Episode $currentNumber is the last one (total ${details.totalEpisodes}).")
                            return@fold
                        }

                        // Next episode is usually within the first page; otherwise fetch just that one.
                        val nextEp = firstPage.find { it.number == nextNumber }
                            ?: repository.getEpisodesRange(animeId, animeUrl, nextNumber, nextNumber)
                                .getOrNull()
                                ?.firstOrNull { it.number == nextNumber }

                        if (nextEp != null) {
                            Log.i(TAG, "Found next episode: Ep. ${nextEp.number} (ID: ${nextEp.id})")
                            _uiState.update { it.copy(nextEpisode = nextEp) }
                        } else {
                            Log.i(TAG, "No next episode resolved for number $nextNumber.")
                        }
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Failed to fetch details for next-episode detection", err)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during fetchNextEpisode", e)
            }
        }
    }
}
