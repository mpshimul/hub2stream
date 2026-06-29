package com.shimulfp.hub2stream.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.data.LiveTVRepository
import com.shimulfp.hub2stream.data.SourceChannels
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LiveTVUiState(
    val sourceResults: List<SourceChannels> = emptyList(),
    val selectedSourceIndex: Int = 0,
    val isGlobalLoading: Boolean = true,
    val isValidating: Boolean = false,
    val validationProgress: String = ""
) {
    val currentChannels: List<LiveChannel>
        get() = sourceResults.getOrNull(selectedSourceIndex)?.channels ?: emptyList()
}

class LiveTVViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTVUiState())
    val uiState: StateFlow<LiveTVUiState> = _uiState

    init {
        loadAllSources()
    }

    fun loadAllSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGlobalLoading = true)
            try {
                // Show loading chips for all known sources (built-in + any remote already discovered)
                val allKnown = LiveTVRepository.allSources
                val loadingStates = allKnown.mapIndexed { index, source ->
                    SourceChannels(
                        source = source.copy(name = "TV-${index + 1}"),
                        channels = emptyList(),
                        isLoading = true
                    )
                }
                _uiState.value = _uiState.value.copy(sourceResults = loadingStates)

                // Get results — prefers validated (from background), falls back to fast
                val results = LiveTVRepository.getAllSources()

                // Rename sources to TV-1, TV-2, TV-3...
                val renamed = results.mapIndexed { index, sc ->
                    sc.copy(source = sc.source.copy(name = "TV-${index + 1}"))
                }

                _uiState.value = _uiState.value.copy(
                    sourceResults = renamed,
                    selectedSourceIndex = 0,
                    isGlobalLoading = false,
                    isValidating = LiveTVRepository.isValidationInProgress(),
                    validationProgress = if (LiveTVRepository.isValidationInProgress()) "Validating channels..." else ""
                )

                // Poll validation status until it completes
                if (LiveTVRepository.isValidationInProgress()) {
                    pollValidationStatus()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGlobalLoading = false)
            }
        }
    }

    fun selectSource(index: Int) {
        _uiState.value = _uiState.value.copy(selectedSourceIndex = index)
    }

    fun refreshCurrentSource() {
        viewModelScope.launch {
            val current = _uiState.value.sourceResults.getOrNull(_uiState.value.selectedSourceIndex)
            if (current == null) return@launch

            val updated = current.copy(isLoading = true, channels = emptyList())
            _uiState.value = _uiState.value.copy(
                sourceResults = _uiState.value.sourceResults.map {
                    if (it.source.id == current.source.id) updated else it
                }
            )

            val refreshed = LiveTVRepository.refreshSource(current.source.id)
            // Re-apply TV-N naming
            val renamedResults = _uiState.value.sourceResults.map { sc ->
                if (sc.source.id == refreshed.source.id) {
                    val idx = _uiState.value.sourceResults.indexOf(sc)
                    refreshed.copy(source = refreshed.source.copy(name = "TV-${idx + 1}"))
                } else sc
            }
            _uiState.value = _uiState.value.copy(sourceResults = renamedResults)
        }
    }

    private fun pollValidationStatus() {
        viewModelScope.launch {
            while (LiveTVRepository.isValidationInProgress()) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    isValidating = true,
                    validationProgress = "Validating channels..."
                )
            }
            // Validation done — reload to get validated results
            loadAllSources()
        }
    }

    suspend fun refreshStreamUrl(channel: LiveChannel): String? {
        return LiveTVRepository.refreshStreamUrl(channel.id, channel.name, channel.sourceId)
    }
}