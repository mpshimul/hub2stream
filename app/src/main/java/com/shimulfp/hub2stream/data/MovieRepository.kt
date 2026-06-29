package com.shimulfp.hub2stream.data

import android.content.Context
import android.util.Log
import com.shimulfp.hub2stream.data.cache.CacheManager
import com.shimulfp.hub2stream.extractor.AoneroomApiClient
import com.shimulfp.hub2stream.extractor.models.*
import com.shimulfp.hub2stream.extractor.models.PaginatedResult

/**
 * MovieRepository using the simplified Aoneroom API
 * Checks CacheManager first for instant loading, then fetches from network if stale/empty
 */
class MovieRepository(private val context: Context? = null) {
    companion object {
        private const val TAG = "MovieRepository"
    }

    private val apiClient = AoneroomApiClient()

    suspend fun getHomePageRows(): List<HomePageRow> {
        // Try to get from cache first if context is available
        if (context != null) {
            try {
                val cacheManager = CacheManager(context)
                val (cachedMovies, isStale) = cacheManager.getMovies()
                if (!cachedMovies.isEmpty()) {
                    Log.d(TAG, "Returning cached movies: ${cachedMovies.size} rows (stale=$isStale)")
                    return cachedMovies
                }
                Log.d(TAG, "Cache is empty, fetching from network...")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting from cache: ${e.message}")
            }
        }
        // Fetch from network
        return apiClient.getHomePageRows()
    }
    suspend fun getAllSeriesByCategory(category: String?, page: Int = 1, perPage: Int = 50): List<Series> = apiClient.getAllSeriesByCategory(category, page, perPage)
    suspend fun getMovieDetails(slug: String): Movie? = apiClient.getMovieDetails(slug)
    suspend fun getSeriesDetails(slug: String): Series? = apiClient.getSeriesDetails(slug)
    suspend fun search(query: String, page: Int = 1, perPage: Int = 20): List<MediaItemPreview> = apiClient.search(query, page, perPage)
    suspend fun getCategoryByPage(categoryType: String, categoryData: String, page: Int = 1, perPage: Int = 20): PaginatedResult<MediaItemPreview> = apiClient.fetchCategoryByPage(categoryType, categoryData, page, perPage)

    suspend fun getFilteredContent(
        classify: String? = null,
        country: String? = null,
        genre: String? = null,
        sort: String = "ForYou",
        year: String? = null,
        page: Int = 1,
        perPage: Int = 20,
        subjectType: Int = 0
    ): List<MediaItemPreview> {
        val filterParams = mutableMapOf<String, Any>()
        filterParams["page"] = page
        filterParams["perPage"] = perPage
        filterParams["subjectType"] = subjectType
        filterParams["sort"] = sort

        if (!classify.isNullOrBlank()) filterParams["classify"] = classify
        if (!country.isNullOrBlank()) filterParams["country"] = country
        if (!genre.isNullOrBlank()) filterParams["genre"] = genre
        if (!year.isNullOrBlank()) filterParams["year"] = year

        return apiClient.getFilteredContent(filterParams)
    }

    suspend fun loadLinks(linkData: String, callback: (String, String, String?) -> Unit) {
        Log.d(TAG, "loadLinks() - linkData=$linkData")
        apiClient.loadLinks(linkData, callback)
    }

    /**
     * Get available stream qualities for a movie or episode
     * Now returns the list directly instead of using callback
     */
    suspend fun getStreams(linkData: String): List<QualityInfo> {
        Log.d(TAG, "getStreams() - linkData=$linkData")
        return apiClient.getStreams(linkData)
    }

    // Legacy callback-based method for backward compatibility
    suspend fun getStreams(linkData: String, callback: (List<QualityInfo>, String?) -> Unit) {
        Log.d(TAG, "getStreams(callback) - linkData=$linkData")
        val qualities = apiClient.getStreams(linkData)
        Log.d(TAG, "getStreams(callback) - returning ${qualities.size} qualities")
        callback(qualities, null)
    }

    suspend fun fetchSubtitles(
        subjectId: String,
        resourceId: String,
        detailPath: String,
        format: String = "MP4"
    ): List<SubtitleInfo> {
        Log.d(TAG, "fetchSubtitles() - subjectId=$subjectId, resourceId=$resourceId, detailPath=$detailPath")
        return apiClient.fetchSubtitles(subjectId, resourceId, detailPath, format)
    }

    suspend fun fetchDubs(detailPath: String): List<DubInfo> {
        Log.d(TAG, "fetchDubs() - detailPath=$detailPath")
        return apiClient.fetchDubs(detailPath)
    }
}