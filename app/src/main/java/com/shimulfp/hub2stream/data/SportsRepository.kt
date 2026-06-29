package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.SportsExtractor
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for live sports events data
 * Uses SportsExtractor (Fancode/Tapmad) for general sports
 * NOTE: This is for general sports (Fancode/Tapmad), NOT FIFA World Cup.
 * For FIFA World Cup, use UpcomingMatchesRepository instead.
 */
class SportsRepository {
    private val extractor = SportsExtractor()

    /**
     * Get live sports events from Fancode/Tapmad
     * @param forceRefresh Force refresh from network (ignores cache)
     * @return List of live sports events
     */
    suspend fun getLiveEvents(forceRefresh: Boolean = false): List<SportsEvent> = withContext(Dispatchers.IO) {
        try {
            extractor.fetchLiveEvents(forceRefresh)
        } catch (e: Exception) {
            android.util.Log.e("SportsRepository", "Error fetching live events: ${e.message}", e)
            emptyList()
        }
    }
}