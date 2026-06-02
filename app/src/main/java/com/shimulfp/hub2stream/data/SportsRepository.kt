package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.SportsExtractor
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SportsRepository {
    private val extractor = SportsExtractor()

    suspend fun getLiveEvents(): List<SportsEvent> = withContext(Dispatchers.IO) {
        try { extractor.fetchLiveEvents() } catch (e: Exception) { emptyList() }
    }
}