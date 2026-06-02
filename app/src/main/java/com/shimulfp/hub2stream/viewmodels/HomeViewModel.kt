package com.shimulfp.hub2stream.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.ContinueWatchingRepository
import com.shimulfp.hub2stream.data.FavoritesRepository
import com.shimulfp.hub2stream.data.LiveTVRepository
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.data.SportsRepository
import com.shimulfp.hub2stream.data.UpcomingMatchesRepository
import com.shimulfp.hub2stream.extractor.models.HomePageRow
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import com.shimulfp.hub2stream.models.FavoriteItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val movieRows: List<HomePageRow> = emptyList(),
    val liveChannels: List<LiveChannel> = emptyList(),
    val liveEvents: List<SportsEvent> = emptyList(),
    val upcomingMatches: List<UpcomingMatch> = emptyList(),
    val isLoadingMovies: Boolean = true,
    val isLoadingLiveTV: Boolean = true,
    val isLoadingSports: Boolean = true,
    val isLoadingUpcoming: Boolean = true,
    val continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
    val favoriteItems: List<FavoriteItem> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val movieRepo = MovieRepository(application)
    private val liveTvRepo = LiveTVRepository()
    private val sportsRepo = SportsRepository()
    private val upcomingMatchesRepo = UpcomingMatchesRepository()
    private val continueWatchingRepo = ContinueWatchingRepository(application)
    private val favoritesRepo = FavoritesRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Observe continue watching items continuously
        viewModelScope.launch {
            continueWatchingRepo.items
                .catch { e -> e.printStackTrace() }
                .collect { items ->
                    // Filter continue watching items:
                    // - For movies: include all
                    // - For series: include only the latest episode (highest episode number) for each series
                    val filteredItems = items.filter { item ->
                        item.type == "movie" || item.type == "series"
                    }.groupBy { item ->
                        // Group by slug and type (same series or same movie)
                        "${item.type}_${item.slug}"
                    }.mapValues { (_, groupItems) ->
                        if (groupItems.first().type == "series") {
                            // For series, keep only the latest episode (highest season/episode number)
                            groupItems.maxByOrNull { compareValues(it.seasonNumber, it.episodeNumber) }
                                ?: groupItems.firstOrNull()
                        } else {
                            // For movies, keep the item
                            groupItems.firstOrNull()
                        }
                    }.values.mapNotNull { it }
                    .sortedByDescending { it.timestamp }

                    _uiState.value = _uiState.value.copy(continueWatchingItems = filteredItems)
                }
        }
        // Observe favorites items continuously
        viewModelScope.launch {
            favoritesRepo.items
                .catch { e -> e.printStackTrace() }
                .collect { items ->
                    _uiState.value = _uiState.value.copy(favoriteItems = items)
                }
        }
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // Movies
            launch {
                val result = withTimeoutOrNull(60000L) { movieRepo.getHomePageRows() } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    movieRows = result,
                    isLoadingMovies = false
                )
            }
            // Live TV
            launch {
                val result = withTimeoutOrNull(60000L) { liveTvRepo.getChannels() } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    liveChannels = result,
                    isLoadingLiveTV = false
                )
            }
            // Live Sports
            launch {
                val result = withTimeoutOrNull(15000L) { sportsRepo.getLiveEvents() } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    liveEvents = result,
                    isLoadingSports = false
                )
            }
            // Upcoming Matches
            launch {
                val result = withTimeoutOrNull(15000L) { upcomingMatchesRepo.getUpcomingMatches() } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    upcomingMatches = result,
                    isLoadingUpcoming = false
                )
            }
        }
    }

    suspend fun clearContinueWatching() {
        continueWatchingRepo.clearAll()
    }
}