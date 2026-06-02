// ContinueWatchingItem.kt
package com.shimulfp.hub2stream.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinueWatchingItem(
    val contentId: String,
    val slug: String,
    val title: String,
    val posterUrl: String,
    val type: String,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val positionSeconds: Long,
    val durationSeconds: Long,
    val progressPercentage: Int = 0,  // 0-100
    val timestamp: Long = System.currentTimeMillis()
) {
    val isWatched: Boolean
        get() = progressPercentage >= 95

    val isInProgress: Boolean
        get() = progressPercentage > 0 && progressPercentage < 95
}
