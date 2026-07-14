package com.shimulfp.hub2stream.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manager for Lovetier stream token refresh
 * Handles automatic token refresh every 2 minutes during playback
 */
class LovetierTokenManager {
    companion object {
        private const val TAG = "LovetierTokenManager"
        private const val REFRESH_INTERVAL_MS = 120000L // 2 minutes
        private const val TOKEN_API = "https://lovetier.bz/api/refresh_token.php"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val activeRefreshJobs = mutableMapOf<String, TokenRefreshJob>()

    /**
     * Data class for tracking token refresh jobs
     */
    private data class TokenRefreshJob(
        val job: Job,
        var currentToken: String,
        val onTokenRefreshed: (String) -> Unit
    )

    /**
     * Start periodic token refresh for a channel
     * @param channelId Unique identifier for the channel (e.g., "FOXSPORTS1")
     * @param currentToken Initial token
     * @param onTokenRefreshed Callback when token is refreshed with new token
     * @param scope Coroutine scope for the refresh job
     */
    fun startTokenRefresh(
        channelId: String,
        currentToken: String,
        onTokenRefreshed: (String) -> Unit,
        scope: CoroutineScope
    ) {
        Log.d(TAG, "Starting token refresh for channel: $channelId")

        // Cancel existing job if any
        stopTokenRefresh(channelId)

        val job = scope.launch(Dispatchers.IO) {
            var token = currentToken

            while (isActive) {
                try {
                    Log.d(TAG, "Waiting ${REFRESH_INTERVAL_MS / 1000}s before refreshing token for $channelId")
                    delay(REFRESH_INTERVAL_MS)

                    Log.d(TAG, "Refreshing token for channel: $channelId")
                    val newToken = refreshToken(channelId, token)

                    if (newToken != null && newToken != token) {
                        Log.d(TAG, "✓ Token refreshed successfully for $channelId")
                        token = newToken

                        // Update the job's token and notify callback
                        activeRefreshJobs[channelId]?.currentToken = token
                        withContext(Dispatchers.Main) {
                            onTokenRefreshed(token)
                        }
                    } else if (newToken == null) {
                        Log.w(TAG, "⚠ Token refresh failed for $channelId, retrying...")
                    } else {
                        Log.d(TAG, "Token unchanged for $channelId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in token refresh loop for $channelId: ${e.message}", e)
                }
            }
        }

        activeRefreshJobs[channelId] = TokenRefreshJob(job, currentToken, onTokenRefreshed)
        Log.d(TAG, "Token refresh job started for $channelId")
    }

    /**
     * Stop token refresh for a specific channel
     */
    fun stopTokenRefresh(channelId: String) {
        Log.d(TAG, "Stopping token refresh for channel: $channelId")
        activeRefreshJobs[channelId]?.job?.cancel()
        activeRefreshJobs.remove(channelId)
    }

    /**
     * Stop all active token refresh jobs
     */
    fun stopAll() {
        Log.d(TAG, "Stopping all token refresh jobs")
        activeRefreshJobs.values.forEach { it.job.cancel() }
        activeRefreshJobs.clear()
    }

    /**
     * Refresh token from Lovetier API
     * @param channelId Channel identifier (e.g., "FOXSPORTS1")
     * @param currentToken Current token to refresh
     * @return New token or null if refresh failed
     */
    suspend fun refreshToken(channelId: String, currentToken: String): String? = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(15000L) {
                val jsonBody = JSONObject().apply {
                    put("channel", channelId)
                    put("current_token", currentToken)
                }

                Log.d(TAG, "Requesting token refresh from API")
                Log.d(TAG, "  Channel: $channelId")
                Log.d(TAG, "  Current token: ${currentToken.take(30)}...")

                val mediaType = "application/json".toMediaType()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(TOKEN_API)
                    .post(requestBody)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Token API failed: ${response.code}")
                    return@withTimeoutOrNull null
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Token API response is empty")
                    return@withTimeoutOrNull null
                }

                Log.d(TAG, "Token API response: $responseBody")

                // Parse response
                val jsonResponse = JSONObject(responseBody)

                // Check for different response formats
                val newToken = when {
                    jsonResponse.has("token") -> jsonResponse.getString("token")
                    jsonResponse.has("data") -> {
                        val data = jsonResponse.getJSONObject("data")
                        if (data.has("token")) data.getString("token") else null
                    }
                    else -> {
                        Log.w(TAG, "Unknown response format")
                        null
                    }
                }

                if (newToken != null) {
                    Log.d(TAG, "✓ New token received: ${newToken.take(30)}...")
                } else {
                    Log.e(TAG, "No token found in response")
                }

                newToken
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token: ${e.message}", e)
            null
        }
    }

    /**
     * Check if a URL is a Lovetier stream
     */
    fun isLovetierStream(url: String): Boolean {
        return url.contains("lovely.lovetier.bz") || url.contains("lovetier.bz")
    }

    /**
     * Extract channel ID from Lovetier stream URL
     * @param url Stream URL
     * @return Channel ID or null if not a Lovetier stream
     */
    fun extractChannelId(url: String): String? {
        if (!isLovetierStream(url)) return null

        try {
            // URL format: https://lovely.lovetier.bz/FOXSPORTS1/index.m3u8?token=...
            val pathParts = url.removePrefix("https://").removePrefix("http://").split("/")
            if (pathParts.size >= 2) {
                return pathParts[1] // FOXSPORTS1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting channel ID from URL: ${url}", e)
        }

        return null
    }

    /**
     * Extract current token from Lovetier stream URL
     * @param url Stream URL
     * @return Current token or null if not found
     */
    fun extractToken(url: String): String? {
        if (!isLovetierStream(url)) return null

        try {
            val tokenParam = url.substringAfter("token=", "")
            if (tokenParam.isNotBlank()) {
                return tokenParam.substringBefore("&")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting token from URL: ${url}", e)
        }

        return null
    }

    /**
     * Build new stream URL with updated token
     * @param originalUrl Original stream URL
     * @param newToken New token to use
     * @return New URL with updated token
     */
    fun updateTokenInUrl(originalUrl: String, newToken: String): String {
        if (!isLovetierStream(originalUrl)) return originalUrl

        return originalUrl.replace(Regex("token=[^&]+"), "token=$newToken")
    }
}