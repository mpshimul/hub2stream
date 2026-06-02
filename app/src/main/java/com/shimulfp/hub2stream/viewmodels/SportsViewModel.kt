package com.shimulfp.hub2stream.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.SportsRepository
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SportsViewModel : ViewModel() {
    private val repo = SportsRepository()
    private val _events = MutableStateFlow<List<SportsEvent>>(emptyList())
    val events: StateFlow<List<SportsEvent>> = _events
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init { loadEvents() }

    fun loadEvents() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _events.value = repo.getLiveEvents()
                _error.value = null
            } catch (e: Exception) { _error.value = e.message }
            finally { _isLoading.value = false }
        }
    }
}