package com.shimulfp.hub2stream.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.extractor.models.MediaItemPreview
import com.shimulfp.hub2stream.extractor.models.PaginatedResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "CategoryViewModel"
    }

    private val repo = MovieRepository(application)
    private val _items = MutableStateFlow<List<MediaItemPreview>>(emptyList())
    val items: StateFlow<List<MediaItemPreview>> = _items
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
    private var currentCategory: String? = null
    private var currentCategoryType: String? = null
    private var currentCategoryData: String? = null
    private var currentIsSeriesCategory = false

    fun loadItems(slugs: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _hasMore.value = false // No pagination for slug-based loading
            try {
                val items = slugs.mapNotNull { slug ->
                    val movie = repo.getMovieDetails(slug)
                    if (movie != null) {
                        MediaItemPreview.MoviePreview(movie.title, movie.posterUrl, slug, movie.year)
                    } else {
                        val series = repo.getSeriesDetails(slug)
                        if (series != null) {
                            MediaItemPreview.SeriesPreview(series.title, series.posterUrl, slug, series.year)
                        } else null
                    }
                }
                _items.value = items
                currentPage = 1
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSeriesByCategory(category: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            currentPage = 1
            currentCategory = category
            currentCategoryType = "series"
            currentCategoryData = null
            currentIsSeriesCategory = true
            _hasMore.value = true
            Log.d(TAG, "loadSeriesByCategory - category=$category, page=1")
            try {
                val seriesList = repo.getAllSeriesByCategory(category, page = 1, perPage = 20)
                val previews = seriesList.map { series ->
                    MediaItemPreview.SeriesPreview(series.title, series.posterUrl, series.slug, series.year)
                }
                _items.value = previews
                _hasMore.value = seriesList.size >= 20 // Assume more if we got a full page
                Log.d(TAG, "Loaded ${previews.size} items, hasMore=${_hasMore.value}")
                if (previews.isEmpty()) {
                    _error.value = "No series found for this category."
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Error loading series", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCategoryByType(categoryType: String, categoryData: String, initialItems: List<MediaItemPreview>) {
        viewModelScope.launch {
            _isLoading.value = false // Don't show loading since we have initial items
            _error.value = null
            currentPage = 1
            currentCategory = null
            currentCategoryType = categoryType
            currentCategoryData = categoryData
            currentIsSeriesCategory = false
            _items.value = initialItems

            // For filter, make an API call to get the initial hasMore value
            // For ranking, assume there's more
            if (categoryType == "filter") {
                try {
                    val result = repo.getCategoryByPage(categoryType, categoryData, page = 1, perPage = 20)
                    _hasMore.value = result.hasMore
                    Log.d(TAG, "loadCategoryByType - type=$categoryType, hasMore from API=${result.hasMore}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching initial hasMore for filter", e)
                    _hasMore.value = initialItems.size >= 15
                }
            } else {
                _hasMore.value = initialItems.size >= 15
                Log.d(TAG, "loadCategoryByType - type=$categoryType, hasMore based on items=${_hasMore.value}")
            }

            Log.d(TAG, "loadCategoryByType - type=$categoryType, data=$categoryData, items=${initialItems.size}, hasMore=${_hasMore.value}")
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            if (!(_isLoadingMore.value || !_hasMore.value || currentPage == 0)) {
                Log.d(TAG, "loadMore() called - currentPage=$currentPage, hasMore=${_hasMore.value}, type=$currentCategoryType")
                _isLoadingMore.value = true
                _error.value = null
                currentPage++

                // Safety limit: Don't load more than 50 pages
                if (currentPage > 50) {
                    _hasMore.value = false
                    _isLoadingMore.value = false
                    Log.d(TAG, "Reached max page limit (50), stopping pagination")
                    return@launch
                }

                try {
                    val newItems = when {
                        currentIsSeriesCategory && currentCategory != null -> {
                            Log.d(TAG, "Loading series page $currentPage for category: $currentCategory")
                            val seriesList = repo.getAllSeriesByCategory(currentCategory, page = currentPage, perPage = 20)
                            Log.d(TAG, "Got ${seriesList.size} series from API")
                            seriesList.map { series ->
                                MediaItemPreview.SeriesPreview(series.title, series.posterUrl, series.slug, series.year)
                            }
                        }
                        currentCategoryType != null && currentCategoryData != null -> {
                            Log.d(TAG, "Loading category page $currentPage - type=$currentCategoryType, data=$currentCategoryData")
                            val result = repo.getCategoryByPage(currentCategoryType!!, currentCategoryData!!, page = currentPage, perPage = 20)
                            Log.d(TAG, "Got ${result.items.size} items from API, hasMore=${result.hasMore}")
                            // Use hasMore from API response for filter, calculate for series
                            if (currentCategoryType == "filter") {
                                _hasMore.value = result.hasMore
                            }
                            result.items
                        }
                        else -> {
                            Log.d(TAG, "No pagination support for current content type")
                            emptyList()
                        }
                    }

                    Log.d(TAG, "newItems.size=${newItems.size}")
                    if (newItems.isEmpty()) {
                        _hasMore.value = false
                        Log.d(TAG, "No more items available")
                    } else {
                        // Check for duplicates to prevent infinite loading
                        val existingSlugs = _items.value.map { it.slug }.toSet()
                        val uniqueNewItems = newItems.filter { it.slug !in existingSlugs }
                        val duplicateCount = newItems.size - uniqueNewItems.size

                        if (uniqueNewItems.isEmpty()) {
                            _hasMore.value = false
                            Log.d(TAG, "All items are duplicates, no more items available (duplicates=$duplicateCount)")
                        } else {
                            _items.value = _items.value + uniqueNewItems
                            // For filter and ranking, hasMore is already set above from API response
                            // For series, calculate hasMore based on item count
                            if (currentIsSeriesCategory) {
                                _hasMore.value = newItems.size >= 20
                            }
                            Log.d(TAG, "Added ${uniqueNewItems.size} items (duplicates=$duplicateCount), total=${_items.value.size}, hasMore=${_hasMore.value}, type=$currentCategoryType")
                        }
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

    fun setItems(newItems: List<MediaItemPreview>) {
        _items.value = newItems
        _isLoading.value = false
        _error.value = null
        _hasMore.value = false // Cached items don't support pagination
        currentPage = 0 // Disable pagination
        currentCategoryType = null
        currentCategoryData = null
        currentIsSeriesCategory = false
        Log.d(TAG, "setItems - pagination disabled, items=${newItems.size}")
    }

    fun getCurrentPage(): Int = currentPage
}