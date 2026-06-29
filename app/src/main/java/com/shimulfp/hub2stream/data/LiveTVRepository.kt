package com.shimulfp.hub2stream.data

import android.util.Log
import com.shimulfp.hub2stream.extractor.LiveTVExtractor
import com.shimulfp.hub2stream.extractor.RoarZoneExtractor
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.extractor.models.LiveTVSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

data class SourceChannels(
    val source: LiveTVSource,
    val channels: List<LiveChannel>,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Singleton repository for Live TV channels with multi-source support.
 *
 * Two-phase loading:
 *   Phase 1 (fast): Fetch all sources, NO validation. Homescreen uses this immediately.
 *   Phase 2 (background): Validate remote playlist channels. Updates cached results.
 *
 * Built-in sources: RoarZone, RedForce, TexasTV, Fallback.
 * Remote sources: Loaded from a JSON config file (GitHub), each M3U playlist becomes a source.
 */
object LiveTVRepository {
    private const val TAG = "LiveTVRepository"

    val BUILTIN_SOURCES = listOf(
        LiveTVSource("roarzone", "RoarZone"),
        LiveTVSource("redforce", "RedForce"),
        LiveTVSource("texastv", "TexasTV"),
        LiveTVSource("fallback", "Fallback")
    )

    private val roarZoneExtractor = RoarZoneExtractor()
    private val liveTvExtractor = LiveTVExtractor()

    // Cached remote playlist URLs (sourceId -> m3u URL) for refresh
    @Volatile
    private var remotePlaylistUrls: Map<String, String> = emptyMap()

    // Cache for remote playlist source definitions
    @Volatile
    private var remoteSources: List<LiveTVSource> = emptyList()

    // Phase 1 cache: fast results (no validation)
    @Volatile
    private var fastResults: List<SourceChannels>? = null

    // Phase 2 cache: validated results
    @Volatile
    private var validatedResults: List<SourceChannels>? = null
    private var validatedTimestamp = 0L

    // Phase 1 cache timestamp
    private var fastTimestamp = 0L
    private val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    // Validation state
    @Volatile
    private var isValidationRunning = false

    // Semaphore to prevent concurrent validation
    private val validationSemaphore = Semaphore(1)

    // Validation HTTP client — short timeouts, follows redirects
    private val validationClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * All sources = built-in + remote playlists.
     * Available after loadSources() is called.
     */
    val allSources: List<LiveTVSource>
        get() = BUILTIN_SOURCES + remoteSources

    // ========== Phase 1: Fast Load (No Validation) ==========

    /**
     * Load all source definitions and fetch channels WITHOUT validation.
     * This is fast — used by HomeScreen to show channels immediately.
     * Also populates remote source definitions for the tab count.
     */
    suspend fun loadSources(): List<SourceChannels> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Return cached fast results if fresh
        if (fastResults != null && (now - fastTimestamp) < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached fast results (${fastResults!!.size} sources)")
            return@withContext fastResults!!
        }

        // Fetch remote playlist config
        val remotePlaylists = RemotePlaylistConfig.getPlaylists()
        remoteSources = RemotePlaylistConfig.toSources(remotePlaylists)
        remotePlaylistUrls = remotePlaylists.mapIndexed { index, entry ->
            "playlist_$index" to entry.url
        }.toMap()

        val allSourceDefs = allSources
        Log.d(TAG, "Phase 1: Fetching from ${allSourceDefs.size} sources (${BUILTIN_SOURCES.size} built-in + ${remoteSources.size} remote)...")

        // Fetch all sources in parallel — NO validation
        val results = allSourceDefs.map { source ->
            async {
                try {
                    val channels = fetchFromSource(source.id)
                    Log.d(TAG, "  ${source.name}: ${channels.size} channels (unvalidated)")
                    SourceChannels(
                        source = source,
                        channels = channels,
                        isLoading = false,
                        error = if (channels.isEmpty()) "No channels" else null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "  ${source.name} error: ${e.message}")
                    SourceChannels(
                        source = source,
                        channels = emptyList(),
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }.awaitAll()

        val activeResults = results.filter { it.channels.isNotEmpty() }
        Log.d(TAG, "Phase 1 complete: ${activeResults.size} active sources")

        fastResults = activeResults
        fastTimestamp = now
        activeResults
    }

    // ========== Phase 2: Background Validation ==========

    /**
     * Validate remote playlist channels in the background.
     * Call this AFTER the homescreen has loaded.
     * Updates the validated results cache — LiveTVScreen will pick these up.
     * Safe to call multiple times — concurrent calls are deduplicated via semaphore.
     */
    suspend fun preloadAndValidate() = withContext(Dispatchers.IO) {
        // Skip if validation is already running
        if (!validationSemaphore.tryAcquire()) {
            Log.d(TAG, "Phase 2: Validation already in progress, skipping")
            return@withContext
        }

        try {
            isValidationRunning = true
            Log.d(TAG, "Phase 2: Starting background validation...")

            // Ensure phase 1 data exists
            val sources = fastResults ?: loadSources()
            if (sources.isEmpty()) {
                Log.w(TAG, "Phase 2: No sources to validate")
                return@withContext
            }

            // Build name map for logging
            val nameMap = sources.associate { it.source.id to it.source.name }

            // Validate remote playlist channels in parallel across sources
            val validated = sources.map { sc ->
                if (sc.source.id.startsWith("playlist_") && sc.channels.isNotEmpty()) {
                    val sourceName = nameMap[sc.source.id] ?: sc.source.id
                    async {
                        val validChannels = validateChannels(sc.channels, sourceName)
                        sc.copy(channels = validChannels)
                    }
                } else {
                    async { sc }
                }
            }.awaitAll()

            // Filter out sources that became empty after validation
            val activeValidated = validated.filter { it.channels.isNotEmpty() }
            Log.d(TAG, "Phase 2 complete: ${activeValidated.size} sources after validation")

            validatedResults = activeValidated
            validatedTimestamp = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Phase 2 validation error", e)
        } finally {
            isValidationRunning = false
            validationSemaphore.release()
        }
    }

    /**
     * Whether background validation is currently running.
     */
    fun isValidationInProgress(): Boolean = isValidationRunning

    // ========== Public Getters ==========

    /**
     * Get all source results. Returns validated results if available,
     * otherwise falls back to fast (unvalidated) results.
     * Used by LiveTVScreen.
     */
    suspend fun getAllSources(): List<SourceChannels> = withContext(Dispatchers.IO) {
        // Prefer validated results
        if (validatedResults != null) {
            val now = System.currentTimeMillis()
            if ((now - validatedTimestamp) < CACHE_TTL_MS) {
                Log.d(TAG, "Using validated results (${validatedResults!!.size} sources)")
                return@withContext validatedResults!!
            }
        }

        // Fall back to fast results (or load if needed)
        val fast = fastResults ?: loadSources()
        Log.d(TAG, "Using fast results (${fast.size} sources, ${if (validatedResults != null) "validated expired" else "not yet validated"})")
        fast
    }

    /**
     * Convenience: get channels from the first active source (for HomeScreen preview).
     * Uses fast (unvalidated) results for instant display.
     */
    suspend fun getChannels(): List<LiveChannel> {
        val results = fastResults ?: loadSources()
        return results.firstOrNull()?.channels ?: emptyList()
    }

    /**
     * Refresh a single source by ID.
     */
    suspend fun refreshSource(sourceId: String): SourceChannels {
        val source = allSources.firstOrNull { it.id == sourceId } ?: return SourceChannels(
            LiveTVSource(sourceId, sourceId), emptyList(), error = "Unknown source"
        )
        val channels = fetchFromSource(sourceId, forceRefresh = true)

        // Validate if remote playlist
        val validatedChannels = if (sourceId.startsWith("playlist_")) {
            validateChannels(channels, source.name)
        } else {
            channels
        }

        val result = SourceChannels(
            source = source,
            channels = validatedChannels,
            isLoading = false,
            error = if (validatedChannels.isEmpty()) "No channels" else null
        )
        // Update both caches
        updateCacheEntry(sourceId, result)
        return getCachedResult(sourceId) ?: result
    }

    /**
     * Refresh stream URL for a channel, routing to the correct extractor.
     */
    suspend fun refreshStreamUrl(channelId: String, channelName: String, sourceId: String): String? {
        return when {
            sourceId == "roarzone" -> withTimeoutOrNull(15000L) {
                roarZoneExtractor.refreshChannelStreamUrl(channelId, channelName)
            }
            sourceId == "redforce" || sourceId == "texastv" -> withTimeoutOrNull(15000L) {
                liveTvExtractor.refreshChannelStreamUrl(channelId, channelName)
            }
            sourceId.startsWith("playlist_") -> {
                val m3uUrl = remotePlaylistUrls[sourceId] ?: return null
                withTimeoutOrNull(20000L) {
                    val channels = M3uPlaylistParser.fetchAndParse(m3uUrl)
                    channels.firstOrNull { it.id == channelId || it.name == channelName }?.streamUrl
                }
            }
            else -> null
        }
    }

    suspend fun refreshChannels(): List<LiveChannel> {
        fastResults = null
        validatedResults = null
        fastTimestamp = 0
        validatedTimestamp = 0
        return getChannels()
    }

    // ========== Channel Validation ==========

    /**
     * Validate channels by testing their stream URLs.
     * Tries HEAD first, falls back to GET.
     * Logs detailed failure reason for EACH channel.
     */
    private suspend fun validateChannels(channels: List<LiveChannel>, sourceName: String): List<LiveChannel> =
        withContext(Dispatchers.IO) {
            if (channels.isEmpty()) return@withContext emptyList()

            val maxConcurrent = 8
            val passed = mutableListOf<LiveChannel>()
            var failedCount = 0
            val failureReasons = mutableMapOf<String, Int>() // reason -> count

            Log.d(TAG, "Validating ${channels.size} channels for '$sourceName'...")

            // Log first 3 channel URLs so user can see what's being tested
            channels.take(3).forEach { ch ->
                Log.d(TAG, "  Testing: ${ch.name} -> ${ch.streamUrl.take(120)}")
            }
            if (channels.size > 3) {
                Log.d(TAG, "  ... and ${channels.size - 3} more")
            }

            // Process in batches to limit concurrency
            channels.chunked(maxConcurrent).forEach { chunk ->
                val results: List<Pair<LiveChannel, ValidationResult>> = coroutineScope {
                    chunk.map { channel ->
                        async {
                            val result = checkChannelReachable(channel.streamUrl)
                            Pair(channel, result)
                        }
                    }.awaitAll()
                }

                results.forEach { (channel, result) ->
                    when (result) {
                        is ValidationResult.Reachable -> {
                            passed.add(channel)
                        }
                        is ValidationResult.Unreachable -> {
                            failedCount++
                            val reasonKey = result.reason
                            failureReasons[reasonKey] = (failureReasons[reasonKey] ?: 0) + 1
                            // Log first 5 failures with full detail
                            if (failedCount <= 5) {
                                Log.w(TAG, "  ✗ ${channel.name}: ${result.reason} | URL: ${channel.streamUrl.take(120)}")
                            }
                        }
                    }
                }
            }

            // Summary log
            val removed = channels.size - passed.size
            Log.d(TAG, "Validated: $sourceName — ${passed.size}/${channels.size} passed ($removed removed)")

            // Log failure reason breakdown
            if (failureReasons.isNotEmpty()) {
                Log.d(TAG, "  Failure reasons for '$sourceName':")
                failureReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                    Log.d(TAG, "    $count× $reason")
                }
            }

            passed
        }

    /**
     * Check if a single stream URL is reachable.
     * Tries HEAD first (fast), falls back to GET.
     * Returns detailed reason if unreachable.
     */
    private fun checkChannelReachable(url: String): ValidationResult {
        return try {
            // --- Attempt 1: HEAD request (fast, no body download) ---
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val headResponse = validationClient.newCall(headRequest).execute()
            val headCode = headResponse.code
            headResponse.close()

            if (headCode in 200..299 || headCode == 206 || headCode in 300..399) {
                return ValidationResult.Reachable
            }

            // HEAD returned non-success — try GET
            val getRequest = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val getResponse = validationClient.newCall(getRequest).execute()
            val getCode = getResponse.code
            val contentType = getResponse.header("Content-Type", "")
            val bodyLength = getResponse.body?.contentLength() ?: -1L
            getResponse.close()

            if (getCode in 200..299 || getCode == 206 || getCode in 300..399) {
                return ValidationResult.Reachable
            }

            ValidationResult.Unreachable(
                "HEAD=$headCode, GET=$getCode, type=$contentType, size=$bodyLength"
            )
        } catch (e: java.net.SocketTimeoutException) {
            ValidationResult.Unreachable("Timeout (server didn't respond within 5s)")
        } catch (e: java.net.UnknownHostException) {
            ValidationResult.Unreachable("DNS failed: ${e.message}")
        } catch (e: java.net.ConnectException) {
            ValidationResult.Unreachable("Connection refused: ${e.message}")
        } catch (e: javax.net.ssl.SSLException) {
            ValidationResult.Unreachable("SSL error: ${e.message}")
        } catch (e: java.io.IOException) {
            ValidationResult.Unreachable("IO error: ${e.message}")
        } catch (e: Exception) {
            ValidationResult.Unreachable("Error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private sealed class ValidationResult {
        object Reachable : ValidationResult()
        data class Unreachable(val reason: String) : ValidationResult()
    }

    // ========== Internal Helpers ==========

    private suspend fun fetchFromSource(sourceId: String, forceRefresh: Boolean = false): List<LiveChannel> {
        return when (sourceId) {
            "roarzone" -> {
                try {
                    withTimeoutOrNull(60000L) {
                        Log.d(TAG, "Fetching from RoarZone...")
                        val channels = roarZoneExtractor.fetchChannels(validateStreams = false)
                        Log.d(TAG, "RoarZone returned ${channels.size} channels")
                        channels.map { it.copy(sourceId = "roarzone") }
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "RoarZone error: ${e.message}")
                    emptyList()
                }
            }
            "redforce" -> {
                try {
                    withTimeoutOrNull(15000L) {
                        Log.d(TAG, "Fetching from RedForce...")
                        val channels = liveTvExtractor.fetchFromPrimarySource()
                        Log.d(TAG, "RedForce returned ${channels.size} channels")
                        channels.map { it.copy(sourceId = "redforce") }
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "RedForce error: ${e.message}")
                    emptyList()
                }
            }
            "texastv" -> {
                try {
                    withTimeoutOrNull(15000L) {
                        Log.d(TAG, "Fetching from TexasTV...")
                        val channels = liveTvExtractor.fetchFromTexasTv()
                        Log.d(TAG, "TexasTV returned ${channels.size} channels")
                        channels.map { it.copy(sourceId = "texastv") }
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "TexasTV error: ${e.message}")
                    emptyList()
                }
            }
            "fallback" -> {
                try {
                    withTimeoutOrNull(15000L) {
                        Log.d(TAG, "Fetching from Fallback...")
                        val channels = liveTvExtractor.fetchFromFallback()
                        Log.d(TAG, "Fallback returned ${channels.size} channels")
                        channels.map { it.copy(sourceId = "fallback") }
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback error: ${e.message}")
                    emptyList()
                }
            }
            // Remote M3U playlists (playlist_0, playlist_1, etc.)
            else -> {
                val m3uUrl = remotePlaylistUrls[sourceId]
                if (m3uUrl == null) {
                    Log.w(TAG, "Unknown source: $sourceId")
                    emptyList()
                } else {
                    try {
                        withTimeoutOrNull(20000L) {
                            Log.d(TAG, "Fetching remote playlist: $m3uUrl")
                            val channels = M3uPlaylistParser.fetchAndParse(m3uUrl)
                            Log.d(TAG, "Remote playlist returned ${channels.size} channels")
                            channels.map { it.copy(sourceId = sourceId) }
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Remote playlist error: ${e.message}")
                        emptyList()
                    }
                }
            }
        }
    }

    private fun updateCacheEntry(sourceId: String, newResult: SourceChannels) {
        fastResults = fastResults?.map {
            if (it.source.id == sourceId) newResult else it
        }?.filter { it.channels.isNotEmpty() }
        validatedResults = validatedResults?.map {
            if (it.source.id == sourceId) newResult else it
        }?.filter { it.channels.isNotEmpty() }
    }

    private fun getCachedResult(sourceId: String): SourceChannels? {
        return validatedResults?.firstOrNull { it.source.id == sourceId }
            ?: fastResults?.firstOrNull { it.source.id == sourceId }
    }
}