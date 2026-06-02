package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.LiveTVExtractor
import com.shimulfp.hub2stream.extractor.RoarZoneExtractor
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LiveTVRepository {
    private val roarZoneExtractor = RoarZoneExtractor()
    private val redForceExtractor = LiveTVExtractor()

    companion object {
        private var cachedChannels: List<LiveChannel>? = null
        private var cacheTimestamp: Long = 0
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    }

    suspend fun getChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Return cache if still fresh
        if (cachedChannels != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            println("LiveTV: using cached channels (${cachedChannels!!.size} channels)")
            return@withContext cachedChannels!!
        }


        // Fallback to RoarZone
        val roarZoneChannels = try {
            withTimeoutOrNull(40000L) { roarZoneExtractor.fetchChannels() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (roarZoneChannels.isNotEmpty()) {
            println("LiveTV: fetched ${roarZoneChannels.size} fresh channels from RoarZone (fallback)")
            cachedChannels = roarZoneChannels
            cacheTimestamp = now
            return@withContext roarZoneChannels
        }

        // Try RedForce first (primary source)
        val redForceChannels = try {
            withTimeoutOrNull(20000L) { redForceExtractor.fetchChannels() } ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (redForceChannels.isNotEmpty()) {
            println("LiveTV: fetched ${redForceChannels.size} fresh channels from RedForce")
            cachedChannels = redForceChannels
            cacheTimestamp = now
            return@withContext redForceChannels
        }

        println("LiveTV: No channels from any source")
        emptyList()
    }

    suspend fun refreshChannels(): List<LiveChannel> {
        cachedChannels = null
        cacheTimestamp = 0
        return getChannels()
    }

    /**
     * Refresh a single channel's stream URL by re-fetching from the extractor.
     * Tries RedForce first, then RoarZone.
     * Returns a fresh m3u8 URL or null if both fail.
     */
    suspend fun refreshStreamUrl(channelId: String, channelName: String): String? {
        // Try RedForce first (fast — single player.php fetch)
        val redForceUrl = try {
            withTimeoutOrNull(8000L) { redForceExtractor.refreshChannelStreamUrl(channelId, channelName) }
        } catch (e: Exception) { null }
        if (!redForceUrl.isNullOrBlank()) {
            println("LiveTV: refreshed '$channelName' from RedForce: $redForceUrl")
            return redForceUrl
        }
        // Fallback to RoarZone
        val roarZoneUrl = try {
            withTimeoutOrNull(8000L) { roarZoneExtractor.refreshChannelStreamUrl(channelId, channelName) }
        } catch (e: Exception) { null }
        if (!roarZoneUrl.isNullOrBlank()) {
            println("LiveTV: refreshed '$channelName' from RoarZone: $roarZoneUrl")
            return roarZoneUrl
        }
        println("LiveTV: failed to refresh stream URL for '$channelName'")
        return null
    }
}