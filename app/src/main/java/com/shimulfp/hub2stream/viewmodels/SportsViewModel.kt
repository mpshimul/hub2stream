package com.shimulfp.hub2stream.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.SportsRepository
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for live sports events
 * Manages state for sports data loading and presentation
 */
class SportsViewModel : ViewModel() {
    private val repo = SportsRepository()
    private val _events = MutableStateFlow<List<SportsEvent>>(emptyList())
    val events: StateFlow<List<SportsEvent>> = _events
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadEvents()
    }

    /**
     * Load live sports events
     */
    fun loadEvents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = repo.getLiveEvents()
                _events.value = result
                android.util.Log.d("SportsViewModel", "Loaded ${result.size} events from repository")
                result.take(3).forEach { event ->
                    android.util.Log.d("SportsViewModel", "Event: ${event.name}, status: ${event.status}, streamUrl: ${event.streamUrl.isNotBlank()}")
                }
                if (result.isEmpty()) {
                    _error.value = "No live events available"
                    android.util.Log.w("SportsViewModel", "Result is empty, setting error")
                }
            } catch (e: Exception) {
                android.util.Log.e("SportsViewModel", "Error loading events: ${e.message}", e)
                _error.value = e.message ?: "Failed to load live events"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh the events list
     */
    fun refresh() {
        loadEvents()
    }
}