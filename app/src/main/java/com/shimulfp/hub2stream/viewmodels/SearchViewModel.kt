package com.shimulfp.hub2stream.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.extractor.models.MediaItemPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MovieRepository(application)
    private val _searchResults = MutableStateFlow<List<MediaItemPreview>>(emptyList())
    val searchResults: StateFlow<List<MediaItemPreview>> = _searchResults
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val results = repo.search(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}