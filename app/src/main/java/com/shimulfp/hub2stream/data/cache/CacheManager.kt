package com.shimulfp.hub2stream.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shimulfp.hub2stream.extractor.models.HomePageRow
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Extension to create DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cache_datastore")

/**
 * Cache Manager for storing and retrieving content
 * Uses DataStore to persist data with timestamps for auto-refresh
 */
class CacheManager(context: Context) {

    private val dataStore = context.dataStore
    private val objectMapper = jacksonObjectMapper()

    // Cache keys
    private val MOVIES_KEY = stringPreferencesKey("cached_movies")
    private val MOVIES_TIMESTAMP_KEY = stringPreferencesKey("cached_movies_timestamp")

    private val LIVE_TV_KEY = stringPreferencesKey("cached_live_tv")
    private val LIVE_TV_TIMESTAMP_KEY = stringPreferencesKey("cached_live_tv_timestamp")

    private val SPORTS_KEY = stringPreferencesKey("cached_sports")
    private val SPORTS_TIMESTAMP_KEY = stringPreferencesKey("cached_sports_timestamp")

    private val UPCOMING_MATCHES_KEY = stringPreferencesKey("cached_upcoming_matches")
    private val UPCOMING_MATCHES_TIMESTAMP_KEY = stringPreferencesKey("cached_upcoming_matches_timestamp")

    // Cache expiration times (in milliseconds)
    private val MOVIES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(30) // 30 minutes
    private val LIVE_TV_CACHE_DURATION = TimeUnit.MINUTES.toMillis(15) // 15 minutes (live content changes more often)
    private val SPORTS_CACHE_DURATION = TimeUnit.MINUTES.toMillis(10) // 10 minutes (live events)
    private val UPCOMING_MATCHES_CACHE_DURATION = TimeUnit.MINUTES.toMillis(30) // 30 minutes (metadata changes rarely, timers are calculated from startTimeMs)

    /**
     * Save movies to cache with current timestamp
     */
    suspend fun saveMovies(movies: List<HomePageRow>) {
        dataStore.edit { preferences ->
            preferences[MOVIES_KEY] = objectMapper.writeValueAsString(movies)
            preferences[MOVIES_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
        }
        android.util.Log.d("CacheManager", "Movies cached: ${movies.size} rows")
    }

    /**
     * Get movies from cache
     * Returns Pair<List<HomePageRow>, Boolean> where Boolean indicates if cache is stale (needs refresh)
     */
    suspend fun getMovies(): Pair<List<HomePageRow>, Boolean> {
        return getCachedData(
            dataKey = MOVIES_KEY,
            timestampKey = MOVIES_TIMESTAMP_KEY,
            cacheDuration = MOVIES_CACHE_DURATION
        ) { json ->
            objectMapper.readValue(json)
        }
    }

    /**
     * Save Live TV channels to cache with current timestamp
     */
    suspend fun saveLiveTv(channels: List<LiveChannel>) {
        dataStore.edit { preferences ->
            preferences[LIVE_TV_KEY] = objectMapper.writeValueAsString(channels)
            preferences[LIVE_TV_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
        }
        android.util.Log.d("CacheManager", "Live TV cached: ${channels.size} channels")
    }

    /**
     * Get Live TV channels from cache
     */
    suspend fun getLiveTv(): Pair<List<LiveChannel>, Boolean> {
        return getCachedData(
            dataKey = LIVE_TV_KEY,
            timestampKey = LIVE_TV_TIMESTAMP_KEY,
            cacheDuration = LIVE_TV_CACHE_DURATION
        ) { json ->
            objectMapper.readValue(json)
        }
    }

    /**
     * Save Sports events to cache with current timestamp
     */
    suspend fun saveSports(events: List<SportsEvent>) {
        dataStore.edit { preferences ->
            preferences[SPORTS_KEY] = objectMapper.writeValueAsString(events)
            preferences[SPORTS_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
        }
        android.util.Log.d("CacheManager", "Sports cached: ${events.size} events")
    }

    /**
     * Get Sports events from cache
     */
    suspend fun getSports(): Pair<List<SportsEvent>, Boolean> {
        return getCachedData(
            dataKey = SPORTS_KEY,
            timestampKey = SPORTS_TIMESTAMP_KEY,
            cacheDuration = SPORTS_CACHE_DURATION
        ) { json ->
            objectMapper.readValue(json)
        }
    }

    /**
     * Save Upcoming Matches to cache with current timestamp
     */
    suspend fun saveUpcomingMatches(matches: List<UpcomingMatch>) {
        dataStore.edit { preferences ->
            preferences[UPCOMING_MATCHES_KEY] = objectMapper.writeValueAsString(matches)
            preferences[UPCOMING_MATCHES_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
        }
        android.util.Log.d("CacheManager", "Upcoming matches cached: ${matches.size} matches")
    }

    /**
     * Get Upcoming Matches from cache
     */
    suspend fun getUpcomingMatches(): Pair<List<UpcomingMatch>, Boolean> {
        return getCachedData(
            dataKey = UPCOMING_MATCHES_KEY,
            timestampKey = UPCOMING_MATCHES_TIMESTAMP_KEY,
            cacheDuration = UPCOMING_MATCHES_CACHE_DURATION
        ) { json ->
            objectMapper.readValue(json)
        }
    }

    /**
     * Generic method to get cached data and check if it's stale
     * Returns Pair<Data, Boolean> where Boolean indicates if cache needs refresh
     */
    private suspend fun <T> getCachedData(
        dataKey: Preferences.Key<String>,
        timestampKey: Preferences.Key<String>,
        cacheDuration: Long,
        deserializer: (String) -> T
    ): Pair<T, Boolean> {
        val preferences = dataStore.data.first()

        val jsonData = preferences[dataKey]
        val timestampStr = preferences[timestampKey]

        if (jsonData != null && timestampStr != null) {
            try {
                val timestamp = timestampStr.toLong()
                val currentTime = System.currentTimeMillis()
                val age = currentTime - timestamp
                val isStale = age > cacheDuration

                val data = deserializer(jsonData)

                android.util.Log.d("CacheManager", "Cache hit! Age: ${age / 1000}s, Stale: $isStale")

                return Pair(data, isStale)
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Error deserializing cached data: ${e.message}")
                return Pair(emptyList<T>() as T, true)
            }
        }

        android.util.Log.d("CacheManager", "Cache miss")
        return Pair(emptyList<T>() as T, true)
    }

    /**
     * Clear all cached data
     */
    suspend fun clearAllCache() {
        dataStore.edit { preferences ->
            preferences.remove(MOVIES_KEY)
            preferences.remove(MOVIES_TIMESTAMP_KEY)
            preferences.remove(LIVE_TV_KEY)
            preferences.remove(LIVE_TV_TIMESTAMP_KEY)
            preferences.remove(SPORTS_KEY)
            preferences.remove(SPORTS_TIMESTAMP_KEY)
            preferences.remove(UPCOMING_MATCHES_KEY)
            preferences.remove(UPCOMING_MATCHES_TIMESTAMP_KEY)
        }
        android.util.Log.d("CacheManager", "All cache cleared")
    }
}