package com.shimulfp.hub2stream.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File

/**
 * Converts a base64 data URI (e.g. data:image/svg+xml;base64,...) to a local
 * cache file and returns its Uri so Coil's SvgDecoder can pick it up.
 *
 * Non-data-URI strings are returned as-is (Coil loads them as URLs).
 */
object DataUriHelper {

    private val cache = mutableMapOf<String, String>()

    /** Resolve to Any (Uri for data URIs, original String for URLs). Used by Composable callers. */
    fun resolve(context: Context, logo: String): Any {
        return resolveToString(context, logo)
    }

    /** Resolve to String. Safe to pass through JSON/URLEncoder for navigation args. */
    fun resolveToString(context: Context, logo: String): String {
        if (!logo.startsWith("data:", ignoreCase = true)) return logo

        // Return cached file URI if we already decoded this logo
        cache[logo]?.let { return it }

        val headerEnd = logo.indexOf(',')
        if (headerEnd < 0) return "" // malformed

        val meta = logo.substring(5, headerEnd) // strip "data:"
        val extension = when {
            meta.contains("svg") -> "svg"
            meta.contains("png") -> "png"
            meta.contains("jpeg") || meta.contains("jpg") -> "jpg"
            meta.contains("webp") -> "webp"
            meta.contains("gif") -> "gif"
            else -> "bin"
        }

        val payload = logo.substring(headerEnd + 1)
        val bytes = if (meta.contains(";base64")) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            java.net.URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
        }

        val file = File(context.cacheDir, "data_uri_${logo.hashCode()}.$extension")
        file.writeBytes(bytes)

        val uriStr = Uri.fromFile(file).toString()
        cache[logo] = uriStr
        return uriStr
    }
}