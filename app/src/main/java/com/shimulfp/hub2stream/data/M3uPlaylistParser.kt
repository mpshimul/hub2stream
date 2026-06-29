package com.shimulfp.hub2stream.data

import android.util.Log
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Parses standard M3U/M3U8 playlists into LiveChannel lists.
 * Handles #EXTINF with tvg-logo, tvg-id, group-title attributes.
 * Also parses #KODIPROP tags for DRM (ClearKey, Widevine) configuration.
 */
object M3uPlaylistParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Fetch and parse an M3U playlist from a URL.
     * @param url The M3U playlist URL
     * @return List of LiveChannel
     */
    suspend fun fetchAndParse(url: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching M3U playlist: $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val response = withTimeoutOrNull(20000L) {
                client.newCall(request).execute()
            } ?: run {
                Log.w(TAG, "Timeout fetching: $url")
                return@withContext emptyList()
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch M3U: HTTP ${response.code} - $url")
                return@withContext emptyList()
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Log.w(TAG, "Empty M3U response: $url")
                return@withContext emptyList()
            }

            val channels = parse(body)
            Log.d(TAG, "Parsed ${channels.size} channels from: $url")
            channels
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching M3U: ${e.message} - $url")
            emptyList()
        }
    }

    /**
     * Parse M3U content string into LiveChannel list.
     * Supports:
     *   #EXTINF:-1 tvg-logo="URL" group-title="Sports",Channel Name
     *   #KODIPROP:inputstream=inputstream.adaptive
     *   #KODIPROP:inputstream.adaptive.manifest_type=mpd
     *   #KODIPROP:inputstream.adaptive.license_type=clearkey
     *   #KODIPROP:inputstream.adaptive.license_key=key_id:key_hex
     *   stream_url (HLS .m3u8 or DASH .mpd)
     */
    fun parse(content: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        val lines = content.lines()

        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentId = ""
        var currentLicenseType = ""
        var currentLicenseKey = ""

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("#EXTINF")) {
                // Parse attributes
                currentLogo = extractAttribute(trimmed, "tvg-logo")
                currentGroup = extractAttribute(trimmed, "group-title")
                currentId = extractAttribute(trimmed, "tvg-id")

                // Channel name is after the last comma
                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex >= 0) {
                    currentName = trimmed.substring(commaIndex + 1).trim()
                }
            } else if (trimmed.startsWith("#KODIPROP:")) {
                // Parse Kodi-style DRM properties
                parseKodiProp(trimmed) { key, value ->
                    when (key) {
                        "license_type" -> currentLicenseType = value.lowercase()
                        "license_key" -> currentLicenseKey = value
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // This is a stream URL line
                val streamUrl = trimmed
                if (currentName.isNotBlank() && streamUrl.isNotBlank()) {
                    channels.add(
                        LiveChannel(
                            id = currentId.ifBlank { "m3u_${channels.size}" },
                            name = currentName,
                            category = currentGroup.ifBlank { "All" },
                            logo = currentLogo,
                            streamUrl = streamUrl,
                            licenseType = currentLicenseType,
                            licenseKey = currentLicenseKey
                        )
                    )
                }
                // Reset for next entry
                currentName = ""
                currentLogo = ""
                currentGroup = ""
                currentId = ""
                currentLicenseType = ""
                currentLicenseKey = ""
            }
        }

        return channels
    }

    /**
     * Parse a #KODIPROP line and invoke callback for each key-value pair.
     * Format: #KODIPROP:inputstream.adaptive.license_type=clearkey
     * We strip the "inputstream.adaptive." prefix to get the property name.
     */
    private fun parseKodiProp(line: String, onProperty: (key: String, value: String) -> Unit) {
        // Remove "#KODIPROP:" prefix
        val prop = line.removePrefix("#KODIPROP:").trim()

        // Skip "inputstream=inputstream.adaptive" (not a key-value we need)
        if (!prop.contains("=")) return

        val eqIndex = prop.indexOf('=')
        val fullKey = prop.substring(0, eqIndex).trim()
        val value = prop.substring(eqIndex + 1).trim()

        // Strip "inputstream.adaptive." prefix to get the property name
        val kodiPropPrefix = "inputstream.adaptive."
        val shortKey = if (fullKey.startsWith(kodiPropPrefix)) {
            fullKey.removePrefix(kodiPropPrefix)
        } else {
            fullKey
        }

        onProperty(shortKey, value)
    }

    private fun extractAttribute(line: String, attr: String): String {
        // Match: tvg-logo="value" or tvg-logo='value'
        val doubleQuoteRegex = Regex("""$attr\s*=\s*"([^"]*)"""")
        val singleQuoteRegex = Regex("""$attr\s*=\s*'([^']*)'""")
        return doubleQuoteRegex.find(line)?.groupValues?.get(1)
            ?: singleQuoteRegex.find(line)?.groupValues?.get(1)
            ?: ""
    }

    private const val TAG = "M3uPlaylistParser"
}