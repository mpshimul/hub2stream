package com.shimulfp.hub2stream

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.shimulfp.hub2stream.data.UpcomingMatchesRepository
import com.shimulfp.hub2stream.data.cache.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for Hub2Stream
 * All extractors are now internal - no external JAR loading
 */
class Hub2StreamApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "Hub2StreamApplication"
        lateinit var instance: Hub2StreamApplication
            private set
    }

    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        Log.d(TAG, "Hub2Stream application initialized (using internal extractors)")

        // Preload FIFA data from cache first, then refresh in background
        preloadFIFAData()
    }

    /**
     * Preload FIFA match data in the background when app starts
     * First loads from DataStore cache (instant), then refreshes in background if stale
     */
    private fun preloadFIFAData() {
        applicationScope.launch {
            try {
                Log.d(TAG, "Preloading FIFA match data in background...")
                val startTime = System.currentTimeMillis()

                // Use applicationContext to get the Context (this inside launch block is the coroutine)
                val cacheManager = CacheManager(applicationContext)
                val leagueId = "4186762757372631736"

                // Step 1: Check DataStore cache first (instant load from previous session)
                Log.d(TAG, "Checking DataStore cache for FIFA matches...")
                val (cachedMatches, isStale) = cacheManager.getUpcomingMatches()

                if (cachedMatches.isNotEmpty()) {
                    // Pre-warm repository cache with DataStore data (instant)
                    UpcomingMatchesRepository.setCache(cachedMatches, leagueId)
                    Log.d(TAG, "Cache pre-warmed with ${cachedMatches.size} matches from DataStore (stale=$isStale)")
                }

                // Step 2: Refresh from API in background if cache is stale or empty
                // This doesn't block the app because cache is already warmed
                if (cachedMatches.isEmpty() || isStale) {
                    Log.d(TAG, "Fetching fresh data from API in background...")
                    val matches = UpcomingMatchesRepository.getUpcomingMatches(leagueId, forceRefresh = true)

                    // Save to DataStore for next app launch
                    if (matches.isNotEmpty()) {
                        cacheManager.saveUpcomingMatches(matches)
                        Log.d(TAG, "Fresh data saved to DataStore (${matches.size} matches)")

                        // Repository cache is already warmed by getUpcomingMatches()
                        // No need to call setCache() again
                    }
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "FIFA data ready in ${elapsedTime}ms - MainActivity will load instantly")
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading FIFA data", e)
            }
        }
    }
}