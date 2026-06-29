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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val movieRepo = MovieRepository(application)  // Pass context for cache access
    private val sportsRepo = SportsRepository()
    // UpcomingMatchesRepository is now a singleton - shared with FifaWorldCupViewModel
    private val upcomingMatchesRepo = UpcomingMatchesRepository
    private val continueWatchingRepo = ContinueWatchingRepository(application)
    private val favoritesRepo = FavoritesRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Job for periodic match refresh
    private var matchRefreshJob: Job? = null

    init {
        android.util.Log.d("HomeViewModel", "HomeViewModel initialized")
        // Load data immediately (no extension system anymore)
        android.util.Log.d("HomeViewModel", "Loading home data...")
        loadHomeData()

        // Start periodic refresh for match data every 5 minutes
        startPeriodicMatchRefresh()

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

    }

    private fun startPeriodicMatchRefresh() {
        matchRefreshJob?.cancel()
        matchRefreshJob = viewModelScope.launch {
            while (true) {
                delay(5 * 60 * 1000L) // 5 minutes
                refreshUpcomingMatches()
            }
        }
    }

    private suspend fun refreshUpcomingMatches() {
        android.util.Log.d("HomeViewModel", "Periodic refresh: fetching fresh match data...")
        try {
            val allFreshMatches = upcomingMatchesRepo.getUpcomingMatches(forceRefresh = true)
            // Filter out ENDED matches - only show LIVE, SOON, TODAY, UPCOMING
            val filteredFresh = allFreshMatches.filter { it.getRealTimeStatus() != com.shimulfp.hub2stream.extractor.models.MatchStatus.ENDED }
            _uiState.value = _uiState.value.copy(upcomingMatches = filteredFresh)
            android.util.Log.d("HomeViewModel", "Periodic refresh: ${filteredFresh.size} matches (cache updated, removed ${allFreshMatches.size - filteredFresh.size} ended)")
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Error in periodic match refresh", e)
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // Movies
            launch {
                try {
                    android.util.Log.d("HomeViewModel", "Loading movies...")
                    val result = withTimeoutOrNull(60000L) { movieRepo.getHomePageRows() } ?: emptyList()
                    android.util.Log.d("HomeViewModel", "Movies loaded: ${result.size} rows")
                    _uiState.value = _uiState.value.copy(
                        movieRows = result,
                        isLoadingMovies = false
                    )
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Error loading movies", e)
                    _uiState.value = _uiState.value.copy(
                        movieRows = emptyList(),
                        isLoadingMovies = false
                    )
                }
            }
            // Live TV — Phase 1: fast load (no validation) for homescreen preview
            launch {
                val result = withTimeoutOrNull(60000L) { LiveTVRepository.getChannels() } ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    liveChannels = result,
                    isLoadingLiveTV = false
                )
                // Phase 2: trigger background validation AFTER homescreen LiveTV is ready
                launch {
                    android.util.Log.d("HomeViewModel", "Homescreen LiveTV loaded, starting background validation...")
                    LiveTVRepository.preloadAndValidate()
                }
            }
            // Live Sports
            launch {
                val result = withTimeoutOrNull(30000L) { sportsRepo.getLiveEvents() } ?: emptyList()  // Increased from 15s to 30s
                _uiState.value = _uiState.value.copy(
                    liveEvents = result,
                    isLoadingSports = false
                )
            }
            // Upcoming Matches (uses shared cache with FifaWorldCupViewModel)
            launch {
                try {
                    android.util.Log.d("HomeViewModel", "Loading upcoming matches (shared cache)...")
                    val result = withTimeoutOrNull(30000L) {  // Increased from 15s to 30s for better reliability
                        upcomingMatchesRepo.getUpcomingMatches()
                    } ?: emptyList()

                    val cacheAge = upcomingMatchesRepo.getCacheAge()
                    val cacheAgeText = if (cacheAge == 0L) "not initialized" else "${cacheAge / 1000}s"
                    android.util.Log.d("HomeViewModel", "Upcoming matches loaded: ${result.size} matches (cache age: $cacheAgeText)")

                    // Filter out ENDED matches - only show LIVE, SOON, TODAY, UPCOMING
                    val filteredResult = result.filter { it.getRealTimeStatus() != com.shimulfp.hub2stream.extractor.models.MatchStatus.ENDED }
                    android.util.Log.d("HomeViewModel", "Filtered matches for home: ${filteredResult.size} (removed ${result.size - filteredResult.size} ended matches)")

                    _uiState.value = _uiState.value.copy(
                        upcomingMatches = filteredResult,
                        isLoadingUpcoming = false
                    )
                } catch (e: Exception) {
                    android.util.Log.e("HomeViewModel", "Error loading upcoming matches", e)
                    _uiState.value = _uiState.value.copy(
                        upcomingMatches = emptyList(),
                        isLoadingUpcoming = false
                    )
                }
            }
        }
    }

    suspend fun clearContinueWatching() {
        continueWatchingRepo.clearAll()
    }

    fun refreshSportsData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSports = true)
            android.util.Log.d("HomeViewModel", "Refreshing sports data...")
            val result = withTimeoutOrNull(15000L) { sportsRepo.getLiveEvents() } ?: emptyList()
            android.util.Log.d("HomeViewModel", "Sports data refreshed: ${result.size} events")
            _uiState.value = _uiState.value.copy(
                liveEvents = result,
                isLoadingSports = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        matchRefreshJob?.cancel()
    }
}