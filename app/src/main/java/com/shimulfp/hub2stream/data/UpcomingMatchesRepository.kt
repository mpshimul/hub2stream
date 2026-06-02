package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.AoneroomUpcomingMatchesExtractor
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import com.shimulfp.hub2stream.extractor.models.PaginatedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for upcoming matches data
 * Uses Aoneroom API to fetch upcoming sports matches with client-side pagination
 * Note: The API doesn't support server-side pagination, so we fetch all matches at once
 * and handle pagination on the client side
 */
class UpcomingMatchesRepository {
    private val extractor = AoneroomUpcomingMatchesExtractor()

    // Cache for all matches to avoid repeated API calls
    private var cachedMatches: List<UpcomingMatch>? = null
    private var cachedLeagueId: String? = null

    /**
     * Get all upcoming matches (non-paginated)
     * @param leagueId Optional league ID (uses default FIFA World Cup league if not provided)
     * @return List of upcoming matches sorted by start time
     */
    suspend fun getUpcomingMatches(leagueId: String = "4186762757372631736"): List<UpcomingMatch> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            if (cachedLeagueId == leagueId && cachedMatches != null) {
                android.util.Log.d("UpcomingMatchesRepository", "Returning cached matches for league: $leagueId")
                return@withContext cachedMatches!!
            }

            val matches = extractor.fetchAllUpcomingMatches(leagueId)
            // Update cache
            cachedMatches = matches
            cachedLeagueId = leagueId
            matches
        } catch (e: Exception) {
            android.util.Log.e("UpcomingMatchesRepository", "Error fetching upcoming matches: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get upcoming matches with pagination (client-side pagination)
     * @param leagueId Optional league ID (uses default FIFA World Cup league if not provided)
     * @param page Page number (starting from 1)
     * @param pageSize Number of items per page
     * @return PaginatedResult containing matches and hasMore flag
     */
    suspend fun getUpcomingMatchesPaginated(
        leagueId: String = "4186762757372631736",
        page: Int = 1,
        pageSize: Int = 20
    ): PaginatedResult<UpcomingMatch> = withContext(Dispatchers.IO) {
        try {
            // Get all matches (from cache or API)
            val allMatches = getUpcomingMatches(leagueId)
            android.util.Log.d("UpcomingMatchesRepository", "Got ${allMatches.size} total matches for pagination")

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
        android.util.Log.d("UpcomingMatchesRepository", "Cache cleared")
    }
}