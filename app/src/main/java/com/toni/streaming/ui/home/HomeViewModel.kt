package com.toni.streaming.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toni.streaming.data.local.FavoriteEntity
import com.toni.streaming.data.local.WatchHistoryEntity
import com.toni.streaming.data.model.Anime
import com.toni.streaming.data.repository.AnimeRepository
import com.toni.streaming.data.scraper.TopAnimeType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val featuredList: List<Anime> = emptyList(),
    val popularList: List<Anime> = emptyList(),
    val mostViewedList: List<Anime> = emptyList(),
    val searchResults: List<Anime> = emptyList(),
    val continueWatchingList: List<WatchHistoryEntity> = emptyList(),
    val favoritesList: List<FavoriteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

class HomeViewModel(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadAnimeList()
        observeSearch()
        observeWatchHistory()
        observeFavorites()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavorites().collect { favorites ->
                _uiState.update { it.copy(favoritesList = favorites) }
                recomputeFeatured()
            }
        }
    }

    fun loadAnimeList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Sources for the "In evidenza" mix + the catalog rows, fetched in parallel.
            val popularDeferred = async { repository.getTopAnimeList(TopAnimeType.POPULAR) }
            val mostViewedDeferred = async { repository.getTopAnimeList(TopAnimeType.MOST_VIEWED) }

            val popularResult = popularDeferred.await()
            val mostViewedResult = mostViewedDeferred.await()

            if (popularResult.isSuccess || mostViewedResult.isSuccess) {
                _uiState.update { state ->
                    state.copy(
                        popularList = popularResult.getOrDefault(emptyList()),
                        mostViewedList = mostViewedResult.getOrDefault(emptyList()),
                        isLoading = false
                    )
                }
                recomputeFeatured()
            } else {
                val errorMsg = mostViewedResult.exceptionOrNull()?.message
                    ?: popularResult.exceptionOrNull()?.message
                    ?: "Errore nel caricamento dei dati"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    /**
     * Builds the "In evidenza" carousel: a personalized, shuffled mix. The user's favorites
     * lead the row, followed by a shuffled blend of popular + most-viewed titles that have a
     * wide banner. Reshuffled on every rebuild so the hero rotates.
     */
    private fun recomputeFeatured() {
        val state = _uiState.value
        val favorites = state.favoritesList.map { fav ->
            Anime(
                id = fav.animeId,
                title = fav.title,
                imageUrl = fav.imageUrl,
                coverUrl = fav.coverUrl,
                episodeUrl = fav.animeUrl
            )
        }
        val trending = (state.popularList + state.mostViewedList)
            .filter { !it.coverUrl.isNullOrBlank() }

        val featured = (favorites.shuffled() + trending.shuffled())
            .distinctBy { it.id }
            .take(8)

        _uiState.update { it.copy(featuredList = featured) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun retry() {
        loadAnimeList()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        _searchQuery
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { query ->
                _uiState.update { it.copy(isSearching = true) }
                repository.searchAnime(query).fold(
                    onSuccess = { results ->
                        _uiState.update { it.copy(searchResults = results, isSearching = false) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isSearching = false, error = error.message) }
                    }
                )
            }
            .launchIn(viewModelScope)
    }

    private fun observeWatchHistory() {
        viewModelScope.launch {
            repository.getRecentlyWatched().collect { historyList ->
                // Keep the most recent 10 in-progress items
                val inProgressItems = historyList
                    .filter { entity ->
                        val isFinished = entity.totalDurationMs > 0 && 
                            (entity.totalDurationMs - entity.watchedPositionMs < 15_000 || 
                             entity.watchedPositionMs * 100 / entity.totalDurationMs > 80)
                        !isFinished
                    }
                    .take(10)
                
                _uiState.update { it.copy(continueWatchingList = inProgressItems) }
            }
        }
    }
}
