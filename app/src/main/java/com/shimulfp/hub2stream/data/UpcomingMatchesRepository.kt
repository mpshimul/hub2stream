package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.AoneroomUpcomingMatchesExtractor
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import com.shimulfp.hub2stream.extractor.models.PaginatedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Singleton repository for upcoming matches data with cookie tracking
 * Uses Aoneroom API to fetch upcoming sports matches with client-side pagination
 * Note: The API doesn't support server-side pagination, so we fetch all matches at once
 * and handle pagination on the client side
 *
 * Cache behavior:
 * - In-memory metadata is cached for 30 minutes by default
 * - Can be pre-warmed with setCache() from DataStore for instant app launch
 * - Use forceRefresh=true to bypass cache and fetch fresh data from API
 * - Timers are calculated in real-time from startTimeMs (no API call needed)
 */
object UpcomingMatchesRepository {
    private val extractor = AoneroomUpcomingMatchesExtractor()

    // Cache for all matches to avoid repeated API calls
    private var cachedMatches: List<UpcomingMatch>? = null
    private var cachedLeagueId: String? = null
    private var cacheTimestamp = 0L
    private val CACHE_TTL = TimeUnit.MINUTES.toMillis(30) // 30 minutes cache

    /**
     * Get all upcoming matches (non-paginated)
     * @param leagueId Optional league ID (uses default FIFA World Cup league if not provided)
     * @param forceRefresh If true, bypass cache and fetch fresh data from API
     * @return List of upcoming matches sorted by start time
     */
    suspend fun getUpcomingMatches(
        leagueId: String = "4186762757372631736",
        forceRefresh: Boolean = false
    ): List<UpcomingMatch> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cacheAge = if (cacheTimestamp == 0L) 0L else now - cacheTimestamp

            // Check cache first (if not forceRefresh)
            if (!forceRefresh && cachedLeagueId == leagueId && cachedMatches != null && cacheTimestamp != 0L && cacheAge < CACHE_TTL) {
                android.util.Log.d("UpcomingMatchesRepository", "Returning cached matches (age: ${cacheAge/1000}s, ${cachedMatches!!.size} matches) for league: $leagueId")
                return@withContext cachedMatches!!
            }

            android.util.Log.d("UpcomingMatchesRepository", "Fetching fresh matches from API (forceRefresh=$forceRefresh, cacheAge=${cacheAge/1000}s) for league: $leagueId")

            // Fetch matches WITH channels (playability checks disabled by default for speed)
            val matches = extractor.fetchAllUpcomingMatches(leagueId, includeChannels = true)

            // Extract channels from first match to initialize background validation service
            // Note: API "Live Stream" channels are match-specific and not validated here
            // They are merged at the player level for live matches only
            if (matches.isNotEmpty() && matches.first().channels.isNotEmpty()) {
                val channels = matches.first().channels
                FIFAChannelValidationService.initializeWithChannels(channels)
                android.util.Log.d("UpcomingMatchesRepository", "Initialized FIFAChannelValidationService with ${channels.size} channels")
            }

            // Update cache
            cachedMatches = matches
            cachedLeagueId = leagueId
            cacheTimestamp = System.currentTimeMillis()
            android.util.Log.d("UpcomingMatchesRepository", "Repository cache UPDATED: ${matches.size} matches with channels, timestamp=$cacheTimestamp")

            // Log cookie information for each match
            android.util.Log.d("UpcomingMatchesRepository", "Fetched ${matches.size} matches for league: $leagueId")

            matches
        } catch (e: Exception) {
            android.util.Log.e("UpcomingMatchesRepository", "Error fetching upcoming matches: ${e.message}", e)
            // Return cached data if available even on error
            if (cachedMatches != null && cacheTimestamp != 0L) {
                android.util.Log.d("UpcomingMatchesRepository", "Returning cached matches due to error")
                return@withContext cachedMatches!!
            }
            emptyList()
        }
    }

    /**
     * Get upcoming matches with pagination (client-side pagination)
     * @param leagueId Optional league ID (uses default FIFA World Cup league if not provided)
     * @param page Page number (starting from 1)
     * @param pageSize Number of items per page
     * @param forceRefresh If true, bypass cache and fetch fresh data from API
     * @return PaginatedResult containing matches and hasMore flag
     */
    suspend fun getUpcomingMatchesPaginated(
        leagueId: String = "4186762757372631736",
        page: Int = 1,
        pageSize: Int = 20,
        forceRefresh: Boolean = false
    ): PaginatedResult<UpcomingMatch> = withContext(Dispatchers.IO) {
        try {
            // Get all matches (from cache or API)
            val allMatches = getUpcomingMatches(leagueId, forceRefresh)
            android.util.Log.d("UpcomingMatchesRepository", "Got ${allMatches.size} total matches for pagination (forceRefresh=$forceRefresh)")

            // Calculate pagination
            val startIndex = (page - 1) * pageSize
            if (startIndex >= allMatches.size) {
                android.util.Log.d("UpcomingMatchesRepository", "Page $page is out of bounds")
                return@withContext PaginatedResult(emptyList(), hasMore = false)
            }

            val endIndex = (startIndex + pageSize).coerceAtMost(allMatches.size)
            val pageItems = allMatches.subList(startIndex, endIndex)
            val hasMore = endIndex < allMatches.size

            android.util.Log.d("UpcomingMatchesRepository", "Page $page: returning ${pageItems.size} items, hasMore=$hasMore")
            
            // Log cookie information for this page
            pageItems.forEach { match ->
                val defaultChannel = match.channels.firstOrNull()
                val cookiesLength = defaultChannel?.cookies?.length ?: 0
                android.util.Log.d("UpcomingMatchesRepository", 
                    "[PAGE $page] Match '${match.name}' - cookies length: $cookiesLength")
            }
            
            PaginatedResult(items = pageItems, hasMore = hasMore)
        } catch (e: Exception) {
            android.util.Log.e("UpcomingMatchesRepository", "Error fetching paginated matches: ${e.message}", e)
            PaginatedResult(emptyList(), hasMore = false)
        }
    }

    /**
     * Clear the cache (useful when refreshing data)
     */
    fun clearCache() {
        cachedMatches = null
        cachedLeagueId = null
        cacheTimestamp = 0L
        android.util.Log.d("UpcomingMatchesRepository", "Cache cleared")
    }

    /**
     * Pre-warm the cache with data from DataStore (instant app launch)
     * This is called during app startup to load data from previous session
     * @param matches List of matches to cache
     * @param leagueId League ID for the matches
     */
    fun setCache(matches: List<UpcomingMatch>, leagueId: String = "4186762757372631736") {
        cachedMatches = matches
        cachedLeagueId = leagueId
        cacheTimestamp = System.currentTimeMillis()
        android.util.Log.d("UpcomingMatchesRepository", "Cache pre-warmed with ${matches.size} matches from DataStore")
    }

    /**
     * Get cache age in milliseconds (for debugging)
     * Returns 0 if cache has not been initialized
     */
    fun getCacheAge(): Long {
        if (cacheTimestamp == 0L) {
            return 0L // Cache not initialized
        }
        return System.currentTimeMillis() - cacheTimestamp
    }

    /**
     * Check if cache is initialized and valid
     */
    fun isCacheValid(leagueId: String = "4186762757372631736"): Boolean {
        if (cacheTimestamp == 0L || cachedMatches == null) {
            return false
        }
        val cacheAge = System.currentTimeMillis() - cacheTimestamp
        return cachedLeagueId == leagueId && cacheAge < CACHE_TTL
    }
}