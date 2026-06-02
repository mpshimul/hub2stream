package com.shimulfp.hub2stream.models

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    val contentId: String,
    val slug: String,
    val title: String,
    val posterUrl: String,
    val type: String, // "movie" or "series"
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)