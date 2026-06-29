package com.shimulfp.hub2stream.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimulfp.hub2stream.extractor.FIFA26M3UParser
import com.shimulfp.hub2stream.extractor.M3UChannel
import com.shimulfp.hub2stream.extractor.ValidationStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Repository/ViewModel for FIFA 2026 World Cup channels
 * Handles continuous updates and validation of M3U channels
 *
 * Usage:
 * 1. Create instance in your Activity/ViewModel
 * 2. Call startContinuousUpdates() to begin polling
 * 3. Call stopContinuousUpdates() when done (e.g., in onDestroy)
 * 4. Observe channels via StateFlow
 */
class FIFAChannelsRepository(application: Application) : AndroidViewModel(application) {

    private val parser = FIFA26M3UParser()
    private var isPollingStarted = false

    private val _channels = MutableStateFlow<List<M3UChannel>>(emptyList())
    val channels: StateFlow<List<M3UChannel>> = _channels

    private val _validationStats = MutableStateFlow<ValidationStats?>(null)
    val validationStats: StateFlow<ValidationStats?> = _validationStats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        Log.d("FIFAChannelsRepository", "FIFA Channels Repository initialized")

        // Start continuous updates automatically
        startContinuousUpdates()

        // Load initial channels
        loadChannels()
    }

    /**
     * Load channels from parser (single fetch)
     */
    fun loadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val channels = parser.getChannels()
                _channels.value = channels
                _validationStats.value = parser.getValidationStats()

                Log.d("FIFAChannelsRepository",
                    "Loaded ${channels.size} channels (total: ${_validationStats.value?.totalParsed}, " +
                    "valid: ${_validationStats.value?.validChannels}, invalid: ${_validationStats.value?.invalidChannels}, " +
                    "accessible: ${_validationStats.value?.accessibleChannels}, inaccessible: ${_validationStats.value?.inaccessibleChannels})")
            } catch (e: Exception) {
                Log.e("FIFAChannelsRepository", "Error loading channels: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Force refresh channels
     */
    fun refreshChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val channels = parser.getChannels(forceRefresh = true)
                _channels.value = channels
                _validationStats.value = parser.getValidationStats()

                Log.d("FIFAChannelsRepository", "Refreshed ${channels.size} channels")
            } catch (e: Exception) {
                Log.e("FIFAChannelsRepository", "Error refreshing channels: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Start continuous polling for channel updates
     * Polls every 3 minutes automatically
     */
    private fun startContinuousUpdates() {
        if (!isPollingStarted) {
            parser.startContinuousUpdates(viewModelScope)
            isPollingStarted = true

            // Watch for updates
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(30 * 1000) // Check every 30 seconds for updates

                    val stats = parser.getValidationStats()
                    if (stats.cachedChannelsCount != _channels.value.size) {
                        // Channels updated - reload
                        loadChannels()
                    }
                }
            }

            Log.d("FIFAChannelsRepository", "Continuous updates started")
        }
    }

    /**
     * Stop continuous polling
     * Call this in Activity/Fragment onDestroy
     */
    fun stopContinuousUpdates() {
        if (isPollingStarted) {
            parser.stopContinuousUpdates()
            isPollingStarted = false
            Log.d("FIFAChannelsRepository", "Continuous updates stopped")
        }
    }

    /**
     * Get channels for a specific match
     */
    suspend fun getChannelsForMatch(matchId: String): List<M3UChannel> {
        return parser.getChannelsForMatch(matchId)
    }

    /**
     * Enable or disable playability checking
     * @param enabled Whether to check if streams are accessible/playable
     * Note: When enabled, validation takes longer as each URL is tested
     */
    fun setPlayabilityChecking(enabled: Boolean) {
        parser.setPlayabilityChecking(enabled)
        Log.d("FIFAChannelsRepository", "Playability checking ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Force re-check playability of all channels
     * Useful when network conditions change
     */
    fun recheckPlayability() {
        viewModelScope.launch {
            Log.d("FIFAChannelsRepository", "Rechecking playability...")
            refreshChannels()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopContinuousUpdates()
        Log.d("FIFAChannelsRepository", "Repository cleared")
    }
}

/**
 * Singleton instance for easy access across the app
 */
object FIFAChannelsManager {
    private var repository: FIFAChannelsRepository? = null

    fun getInstance(application: Application): FIFAChannelsRepository {
        if (repository == null) {
            repository = FIFAChannelsRepository(application)
        }
        return repository!!
    }

    fun getInstanceOrNull(): FIFAChannelsRepository? = repository

    fun cleanup() {
        repository?.stopContinuousUpdates()
        repository = null
    }
}