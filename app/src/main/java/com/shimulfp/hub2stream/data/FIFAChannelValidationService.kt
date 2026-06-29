package com.shimulfp.hub2stream.data

import com.shimulfp.hub2stream.extractor.FIFA26M3UParser
import com.shimulfp.hub2stream.extractor.models.MatchChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.absoluteValue

/**
 * Background service for FIFA World Cup channel validation
 *
 * Features:
 * - Skips validation during initial load for fast app startup
 * - Validates channels every 5 minutes after initial load completes
 * - Tracks which channels are working (playable)
 * - Provides validated channel list to player on demand
 * - Supports continuous validation to keep channel list fresh during playback
 * - Preserves API channels (Aoneroom) during M3U validation
 */
object FIFAChannelValidationService {
    private val m3uParser = FIFA26M3UParser()
    private val validationScope = CoroutineScope(Dispatchers.IO)

    // Validation state
    private var isInitialized = false
    private var validationJob: Job? = null

    // Track all fetched channels (from initial load)
    private var allChannels: List<MatchChannel> = emptyList()

    // Track validated/playable channels
    private var validatedChannels: Map<String, MatchChannel> = emptyMap()

    // Track API channels separately (Aoneroom "Live Stream")
    private var apiChannels: List<MatchChannel> = emptyList()

    // Track M3U channels separately
    private var m3uChannels: List<MatchChannel> = emptyList()

    // Validation statistics
    private var totalChannels = 0
    private var validatedCount = 0
    private var invalidCount = 0

    private val VALIDATION_INTERVAL = 5 * 60 * 1000L // 5 minutes

    /**
     * Initialize with channels from initial load (no validation yet)
     * This is called after UpcomingMatchesRepository loads matches
     * These channels include API channels (like Aoneroom "Live Stream") and M3U channels
     */
    fun initializeWithChannels(channels: List<MatchChannel>) {
        synchronized(this) {
            // Separate API channels from M3U channels
            // API channels: Aoneroom "Live Stream"
            // M3U channels: from FIFA26M3UParser
            apiChannels = channels.filter { channel ->
                // Aoneroom "Live Stream" channel
                channel.name == "Live Stream"
            }

            m3uChannels = channels.filterNot { channel ->
                apiChannels.any { it.url == channel.url }
            }.take(50) // Take up to 50 M3U channels

            allChannels = channels
            totalChannels = channels.size
            validatedChannels = channels.associateBy { it.url }
            validatedCount = channels.size
            invalidCount = 0
            isInitialized = true

            android.util.Log.d("FIFAChannelValidation", "========================================")
            android.util.Log.d("FIFAChannelValidation", "Initialized with ${channels.size} channels (validation skipped for fast load)")
            android.util.Log.d("FIFAChannelValidation", "API channels: ${apiChannels.size}")
            android.util.Log.d("FIFAChannelValidation", "M3U channels: ${m3uChannels.size}")
            android.util.Log.d("FIFAChannelValidation", "API channels detail:")
            apiChannels.forEach { channel ->
                android.util.Log.d("FIFAChannelValidation", "  [API] ${channel.name}: ${channel.url}")
            }
            android.util.Log.d("FIFAChannelValidation", "Initial channels:")
            channels.take(5).forEach { channel ->
                android.util.Log.d("FIFAChannelValidation", "  - ${channel.name}: ${channel.url}")
            }
            if (channels.size > 5) {
                android.util.Log.d("FIFAChannelValidation", "  ... and ${channels.size - 5} more")
            }
            android.util.Log.d("FIFAChannelValidation", "========================================")

            // Start background validation after a short delay (to avoid blocking UI)
            startBackgroundValidation()
        }
    }

    /**
     * Get channels for a match (returns validated channels)
     */
    fun getChannelsForMatch(matchId: String): List<MatchChannel> {
        synchronized(this) {
            if (!isInitialized) {
                android.util.Log.w("FIFAChannelValidation", "Not initialized, returning empty list for match: $matchId")
                return emptyList()
            }

            android.util.Log.d("FIFAChannelValidation", "getChannelsForMatch called for: $matchId")
            android.util.Log.d("FIFAChannelValidation", "Returning ${validatedChannels.size} validated channels")

            // Use matchId to select channels (round-robin distribution)
            // Different matches get different channel combinations for load balancing
            val channelIndex = matchId.hashCode().absoluteValue % allChannels.size
            android.util.Log.d("FIFAChannelValidation", "Channel index for match: $channelIndex (total: ${allChannels.size})")

            // Return all validated channels (player can choose which one to use)
            return validatedChannels.values.toList()
        }
    }

    /**
     * Get channels as a list of maps for player (JSON format)
     * This is used to refresh channels during playback
     * @return JSON string of channels
     */
    fun getChannelsJson(): String {
        synchronized(this) {
            val channelsList = validatedChannels.values.map { channel ->
                mapOf(
                    "url" to channel.url,
                    "title" to channel.name,
                    "id" to channel.id,
                    "logo" to channel.logo,
                    "cookies" to channel.cookies
                )
            }
            return try {
                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                mapper.writeValueAsString(channelsList)
            } catch (e: Exception) {
                android.util.Log.e("FIFAChannelValidation", "Error converting channels to JSON", e)
                "[]"
            }
        }
    }

    /**
     * Get validation statistics
     */
    fun getValidationStats(): ValidationStats {
        synchronized(this) {
            return ValidationStats(
                totalChannels = totalChannels,
                validatedCount = validatedCount,
                invalidCount = invalidCount
            )
        }
    }

    /**
     * Start background validation job (runs every 5 minutes)
     */
    private fun startBackgroundValidation() {
        validationJob?.cancel()
        validationJob = validationScope.launch {
            android.util.Log.d("FIFAChannelValidation", "Waiting 10 seconds before starting first validation...")

            // Wait before first validation (don't block initial load)
            delay(10_000L)

            while (isActive) {
                try {
                    android.util.Log.d("FIFAChannelValidation", "Starting channel validation...")
                    validateChannels()
                    android.util.Log.d("FIFAChannelValidation", "Validation cycle complete. Waiting ${VALIDATION_INTERVAL / 1000}s for next cycle...")

                    // Wait before next validation
                    delay(VALIDATION_INTERVAL)
                } catch (e: Exception) {
                    android.util.Log.e("FIFAChannelValidation", "Error during validation cycle", e)
                    delay(VALIDATION_INTERVAL)
                }
            }
        }
    }

    /**
     * Validate all channels and update the validated list
     * Validates both API channels (Aoneroom) and M3U channels
     */
    private suspend fun validateChannels() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FIFAChannelValidation", "========================================")
            android.util.Log.d("FIFAChannelValidation", "Starting channel validation cycle...")

            // 1. Validate API channels (Aoneroom "Live Stream")
            android.util.Log.d("FIFAChannelValidation", "Step 1: Validating ${apiChannels.size} API channels")
            val validatedApiChannels = validateChannelList(apiChannels, "API")

            // 2. Fetch and validate M3U channels
            android.util.Log.d("FIFAChannelValidation", "Step 2: Fetching and validating M3U channels")
            m3uParser.setPlayabilityChecking(true)
            val freshM3UChannels = m3uParser.getChannels(forceRefresh = true)
            android.util.Log.d("FIFAChannelValidation", "Received ${freshM3UChannels.size} channels from M3U parser")

            val m3uStats = m3uParser.getValidationStats()
            android.util.Log.d("FIFAChannelValidation", "M3U Validation stats: $m3uStats")

            // Convert M3U channels to MatchChannel format
            m3uChannels = freshM3UChannels.map { m3uChannel ->
                MatchChannel(
                    id = java.util.UUID.randomUUID().toString(),
                    name = m3uChannel.name,
                    url = m3uChannel.url,
                    logo = m3uChannel.logo,
                    cookies = m3uChannel.cookies
                )
            }

            // 3. Merge all validated channels
            val allValidatedChannels = mutableListOf<MatchChannel>()
            allValidatedChannels.addAll(validatedApiChannels)
            allValidatedChannels.addAll(m3uChannels)

            synchronized(this@FIFAChannelValidationService) {
                allChannels = allValidatedChannels
                validatedChannels = allValidatedChannels.associateBy { it.url }

                totalChannels = apiChannels.size + m3uStats.totalParsed
                validatedCount = validatedApiChannels.size + m3uStats.accessibleChannels
                invalidCount = (apiChannels.size - validatedApiChannels.size) + m3uStats.inaccessibleChannels

                android.util.Log.d("FIFAChannelValidation", "----------------------------------------")
                android.util.Log.d("FIFAChannelValidation", "Validation Summary:")
                android.util.Log.d("FIFAChannelValidation", "  API Channels: ${validatedApiChannels.size}/${apiChannels.size} working")
                if (apiChannels.isNotEmpty()) {
                    apiChannels.forEach { channel ->
                        val status = if (validatedApiChannels.any { it.url == channel.url }) "✓" else "✗"
                        android.util.Log.d("FIFAChannelValidation", "    $status [API] ${channel.name}")
                    }
                }
                android.util.Log.d("FIFAChannelValidation", "  M3U Channels: ${m3uStats.accessibleChannels}/${m3uStats.totalParsed} working")
                android.util.Log.d("FIFAChannelValidation", "  Total Validated: ${validatedChannels.size}")
                android.util.Log.d("FIFAChannelValidation", "  Total Invalid: $invalidCount")
                android.util.Log.d("FIFAChannelValidation", "----------------------------------------")
                android.util.Log.d("FIFAChannelValidation", "Validated channels (first 10):")
                validatedChannels.values.take(10).forEach { channel ->
                    val source = if (apiChannels.any { it.url == channel.url }) "API" else "M3U"
                    android.util.Log.d("FIFAChannelValidation", "  [$source] ${channel.name}: ${channel.url}")
                }
                if (validatedChannels.size > 10) {
                    android.util.Log.d("FIFAChannelValidation", "  ... and ${validatedChannels.size - 10} more")
                }
                android.util.Log.d("FIFAChannelValidation", "========================================")
            }

            // Disable playability checking for next regular fetch
            m3uParser.setPlayabilityChecking(false)

        } catch (e: Exception) {
            android.util.Log.e("FIFAChannelValidation", "Error validating channels", e)
        }
    }

    /**
     * Validate a list of channels by testing their URLs
     * @param channels List of channels to validate
     * @param source Source identifier for logging
     * @return List of channels that are accessible
     */
    private suspend fun validateChannelList(channels: List<MatchChannel>, source: String): List<MatchChannel> {
        if (channels.isEmpty()) {
            android.util.Log.d("FIFAChannelValidation", "No $source channels to validate")
            return emptyList()
        }

        val maxConcurrent = 5 // Limit concurrent checks
        val validatedChannels = mutableListOf<MatchChannel>()

        android.util.Log.d("FIFAChannelValidation", "Validating $source channels in batches of $maxConcurrent...")

        channels.chunked(maxConcurrent).forEachIndexed { batchIndex, chunk ->
            android.util.Log.d("FIFAChannelValidation", "Processing batch ${batchIndex + 1}/${(channels.size + maxConcurrent - 1) / maxConcurrent}")

            val results: List<Pair<MatchChannel, Boolean>> = coroutineScope {
                chunk.map { channel ->
                    async {
                        val isPlayable = checkChannelAccessibility(channel)
                        Pair(channel, isPlayable)
                    }
                }.awaitAll()
            }

            results.forEach { result ->
                val (channel, isPlayable) = result
                if (isPlayable) {
                    validatedChannels.add(channel)
                    android.util.Log.d("FIFAChannelValidation", "  ✓ [$source] ${channel.name}")
                } else {
                    android.util.Log.w("FIFAChannelValidation", "  ✗ [$source] ${channel.name} - INACCESSIBLE")
                }
            }
        }

        android.util.Log.d("FIFAChannelValidation", "$source validation complete: ${validatedChannels.size}/${channels.size} working")
        return validatedChannels
    }

    /**
     * Check if a channel is accessible by testing its URL
     * Uses HEAD request first, falls back to GET if HEAD fails
     * Applies proper headers for each channel type:
     *   - Aoneroom "Live Stream": Referer for h5.aoneroom.com
     *   - Others: Standard User-Agent
     */
    private suspend fun checkChannelAccessibility(channel: MatchChannel): Boolean {
        return kotlinx.coroutines.withTimeoutOrNull(10_000L) {
            try {
                // Create custom client for this check
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val requestBuilder = okhttp3.Request.Builder()
                    .url(channel.url)
                    .head()

                // Apply channel-specific headers
                when {
                    // Aoneroom "Live Stream" channel: requires Referer
                    channel.name == "Live Stream" || channel.url.contains("aisports.mobi") -> {
                        requestBuilder.addHeader("Referer", "https://h5.aoneroom.com/")
                        requestBuilder.addHeader("Origin", "https://h5.aoneroom.com")
                        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        android.util.Log.d("FIFAChannelValidation", "  [Aoneroom headers] Referer=https://h5.aoneroom.com/")
                    }
                    // Default: add cookies if available + standard User-Agent
                    else -> {
                        if (channel.cookies.isNotBlank()) {
                            requestBuilder.addHeader("Cookie", channel.cookies)
                        }
                        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                }

                val request = requestBuilder.build()

                val response = client.newCall(request).execute()

                // Check if HEAD was successful
                if (response.code in 200..299) {
                    response.close()
                    return@withTimeoutOrNull true
                }

                // HEAD failed - try GET with same headers
                response.close()
                val getRequest = request.newBuilder().get().build()
                val getResponse = client.newCall(getRequest).execute()

                val isSuccess = getResponse.code in 200..299 ||
                              getResponse.code == 206 ||
                              getResponse.code in 300..399

                getResponse.close()
                return@withTimeoutOrNull isSuccess

            } catch (e: Exception) {
                android.util.Log.d("FIFAChannelValidation", "Accessibility check failed for ${channel.name}: ${e.message}")
                return@withTimeoutOrNull false
            }
        } ?: false // Return false if timeout
    }

    /**
     * Stop background validation
     */
    fun stopValidation() {
        validationJob?.cancel()
        validationJob = null
        android.util.Log.d("FIFAChannelValidation", "Background validation stopped")
    }

    /**
     * Force immediate validation (useful for manual refresh)
     */
    suspend fun forceValidation() {
        validationScope.launch {
            validateChannels()
        }
    }

    /**
     * Validate a single channel with proper headers (used for Aoneroom "Live Stream" API channel)
     * This is called synchronously from the UI thread before merging channels
     * @param channel The channel to validate
     * @return true if accessible, false otherwise
     */
    suspend fun validateSingleChannel(channel: MatchChannel): Boolean {
        return checkChannelAccessibility(channel)
    }
}

/**
 * Validation statistics
 */
data class ValidationStats(
    val totalChannels: Int,
    val validatedCount: Int,
    val invalidCount: Int
)