package com.shimulfp.hub2stream.data

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shimulfp.hub2stream.extractor.models.LiveTVSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches remote playlist definitions from a JSON config file.
 *
 * Config JSON format (hosted on GitHub or any URL):
 * {
 *   "playlists": [
 *     { "name": "Toffee", "url": "https://raw.githubusercontent.com/.../playlist.m3u" },
 *     { "name": "Sports HD", "url": "https://example.com/channels.m3u" }
 *   ]
 * }
 *
 * Each playlist becomes a separate Live TV source (TV-N tab).
 * No app update needed — just edit the JSON file.
 */
object RemotePlaylistConfig {

    // Change this URL to your own JSON config file
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/mpshimul/hub2stream/refs/heads/main/app/src/main/java/com/shimulfp/hub2stream/data/playlists.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val mapper = jacksonObjectMapper()

    private var cachedPlaylists: List<PlaylistEntry>? = null
    private var cacheTimestamp = 0L
    private val CACHE_TTL = 30 * 60 * 1000L // 30 minutes

    data class PlaylistEntry(
        val name: String,
        val url: String
    )

    /**
     * Fetch the remote config and return playlist entries.
     * Returns cached result if within TTL.
     */
    suspend fun getPlaylists(): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedPlaylists != null && (now - cacheTimestamp) < CACHE_TTL) {
            Log.d(TAG, "Using cached remote playlists: ${cachedPlaylists!!.size}")
            return@withContext cachedPlaylists!!
        }

        try {
            Log.d(TAG, "Fetching remote config: $CONFIG_URL")
            val request = Request.Builder()
                .url(CONFIG_URL)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()

            val result = withTimeoutOrNull(10000L) {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Config fetch failed: HTTP ${response.code}")
                    return@withTimeoutOrNull null
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Config response is empty")
                    return@withTimeoutOrNull null
                }
                try {
                    val config: PlaylistConfig = mapper.readValue(body)
                    config.playlists
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse config JSON: ${e.message}")
                    null
                }
            }

            if (result != null) {
                cachedPlaylists = result
                cacheTimestamp = now
                Log.d(TAG, "Loaded ${result.size} remote playlists")
                result
            } else {
                cachedPlaylists ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote config: ${e.message}")
            cachedPlaylists ?: emptyList()
        }
    }

    /**
     * Convert remote playlists to LiveTVSource list for use in the repository.
     */
    fun toSources(playlists: List<PlaylistEntry>): List<LiveTVSource> {
        return playlists.mapIndexed { index, entry ->
            LiveTVSource(
                id = "playlist_${index}",
                name = entry.name
            )
        }
    }

    private data class PlaylistConfig(
        val playlists: List<PlaylistEntry> = emptyList()
    )

    private const val TAG = "RemotePlaylistConfig"
}