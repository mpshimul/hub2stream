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

    private val repo = UpcomingMatchesRepository()
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
            _isLoading.value = true
            _error.value = null
            currentPage = 1
            currentLeagueId = leagueId
            _hasMore.value = true
            Log.d(TAG, "loadMatches - leagueId=$leagueId, page=1")
            try {
                // Clear cache to ensure fresh data
                repo.clearCache()
                val result = repo.getUpcomingMatchesPaginated(leagueId, page = 1, pageSize = ITEMS_PER_PAGE)
                _items.value = result.items
                _hasMore.value = result.hasMore
                Log.d(TAG, "Loaded ${result.items.size} items, hasMore=${result.hasMore}")
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

    fun loadMore() {
        viewModelScope.launch {
            if (!(_isLoadingMore.value || !_hasMore.value || currentPage == 0)) {
                Log.d(TAG, "loadMore() called - currentPage=$currentPage, hasMore=${_hasMore.value}")
                _isLoadingMore.value = true
                _error.value = null
                currentPage++

                try {
                    Log.d(TAG, "Fetching page $currentPage for league: $currentLeagueId")
                    val result = repo.getUpcomingMatchesPaginated(currentLeagueId, page = currentPage, pageSize = ITEMS_PER_PAGE)
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