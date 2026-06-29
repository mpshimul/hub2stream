package com.shimulfp.hub2stream.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.UpcomingMatchesRepository
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FifaWorldCupViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "FifaWorldCupViewModel"
        private const val ITEMS_PER_PAGE = 15
    }

    // Repository is now a singleton - shared across all ViewModels
    private val repo = UpcomingMatchesRepository

    // All matches (for filter counts)
    private val _allMatches = MutableStateFlow<List<UpcomingMatch>>(emptyList())
    val allMatches: StateFlow<List<UpcomingMatch>> = _allMatches

    // Displayed items (paginated)
    private val _items = MutableStateFlow<List<UpcomingMatch>>(emptyList())
    val items: StateFlow<List<UpcomingMatch>> = _items

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    // Track current state for pagination
    private var currentPage = 1
    private var currentLeagueId = "4186762757372631736"

    fun loadMatches(leagueId: String = "4186762757372631736") {
        viewModelScope.launch {
            // Don't show loading if we already have data
            val alreadyHasData = _allMatches.value.isNotEmpty()
            if (alreadyHasData) {
                Log.d(TAG, "loadMatches - Already have ${_allMatches.value.size} matches, using cached state")
                _isLoading.value = false
                return@launch
            }

            _isLoading.value = true
            _error.value = null
            currentPage = 1
            currentLeagueId = leagueId
            _hasMore.value = true
            Log.d(TAG, "loadMatches - leagueId=$leagueId, page=1 (using shared cache)")
            try {
                // Use cached data by default - share cache with HomeViewModel
                val cacheAge = repo.getCacheAge()
                val cacheAgeText = if (cacheAge == 0L) "not initialized" else "${cacheAge / 1000}s"
                Log.d(TAG, "Current cache age: $cacheAgeText")

                // Load ALL matches first for filter counts
                val allMatchesList = repo.getUpcomingMatches(leagueId, forceRefresh = false)
                _allMatches.value = allMatchesList
                Log.d(TAG, "Loaded ${allMatchesList.size} total matches for filter counts")

                // Then load paginated items for display
                val result = repo.getUpcomingMatchesPaginated(leagueId, page = 1, pageSize = ITEMS_PER_PAGE, forceRefresh = false)
                _items.value = result.items
                _hasMore.value = result.hasMore
                Log.d(TAG, "Loaded ${result.items.size} display items, hasMore=${result.hasMore}")
                if (result.items.isEmpty()) {
                    _error.value = "No upcoming FIFA World Cup matches found."
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Error loading FIFA World Cup matches", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Force refresh data from API (bypassing cache)
     */
    fun refreshMatches() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            currentPage = 1
            Log.d(TAG, "refreshMatches - forcing fresh API call")
            try {
                // Load ALL matches first for filter counts
                val allMatchesList = repo.getUpcomingMatches(currentLeagueId, forceRefresh = true)
                _allMatches.value = allMatchesList
                Log.d(TAG, "Loaded ${allMatchesList.size} total matches for filter counts")

                // Then load paginated items for display
                val result = repo.getUpcomingMatchesPaginated(currentLeagueId, page = 1, pageSize = ITEMS_PER_PAGE, forceRefresh = false)
                _items.value = result.items
                _hasMore.value = result.hasMore
                Log.d(TAG, "Refreshed ${result.items.size} display items, hasMore=${result.hasMore}")
                if (result.items.isEmpty()) {
                    _error.value = "No upcoming FIFA World Cup matches found."
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Error refreshing FIFA World Cup matches", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            if (!(_isLoadingMore.value || !_hasMore.value || currentPage == 0)) {
                Log.d(TAG, "loadMore() called - currentPage=$currentPage, hasMore=${_hasMore.value}")
                _isLoadingMore.value = true
                _error.value = null
                currentPage++

                try {
                    Log.d(TAG, "Fetching page $currentPage for league: $currentLeagueId")
                    val result = repo.getUpcomingMatchesPaginated(currentLeagueId, page = currentPage, pageSize = ITEMS_PER_PAGE, forceRefresh = false)
                    Log.d(TAG, "Got ${result.items.size} items from page $currentPage, hasMore=${result.hasMore}")

                    if (result.items.isEmpty()) {
                        _hasMore.value = false
                        Log.d(TAG, "No more items available on page $currentPage")
                    } else {
                        _items.value = _items.value + result.items
                        _hasMore.value = result.hasMore
                        Log.d(TAG, "Added ${result.items.size} items, total=${_items.value.size}, hasMore=${_hasMore.value}")
                    }
                } catch (e: Exception) {
                    currentPage-- // Revert page increment on error
                    _error.value = e.message
                    Log.e(TAG, "Error loading more", e)
                } finally {
                    _isLoadingMore.value = false
                }
            } else {
                Log.d(TAG, "loadMore() skipped - isLoadingMore=${_isLoadingMore.value}, hasMore=${_hasMore.value}, currentPage=$currentPage")
            }
        }
    }

    fun getCurrentPage(): Int = currentPage
}