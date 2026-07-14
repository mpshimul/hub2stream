package com.shimulfp.hub2stream.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Lightweight JSON utility backed by Gson.
 * Works on all Android API levels (no BootstrapMethodError).
 */
object Json {
    val gson = Gson()

    inline fun <reified T> fromJson(json: String): T {
        return gson.fromJson(json, object : TypeToken<T>() {}.type)
    }

    fun toJson(obj: Any?): String = gson.toJson(obj)
}