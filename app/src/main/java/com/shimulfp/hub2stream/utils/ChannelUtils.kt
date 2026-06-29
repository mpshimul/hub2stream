package com.shimulfp.hub2stream.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.shimulfp.hub2stream.extractor.models.MatchChannel
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import java.net.URLEncoder

/**
 * Helper functions for converting match channels to player channel format
 */

/**
 * Convert MatchChannel list to player channel JSON
 */
fun convertChannelsToPlayerJson(channels: List<MatchChannel>): String {
    val channelList = channels.map { channel ->
        mapOf(
            "url" to channel.url,
            "title" to channel.name,
            "id" to channel.id,
            "logo" to channel.logo,
            "cookies" to channel.cookies
        )
    }

    val mapper = jacksonObjectMapper()
    return mapper.writeValueAsString(channelList)
}

/**
 * Convert channels and encode for URL parameter
 */
fun convertAndEncodeChannels(channels: List<MatchChannel>): String {
    val json = convertChannelsToPlayerJson(channels)
    return URLEncoder.encode(json, "UTF-8")
}

/**
 * Get channels from SportsEvent or fallback to empty list
 */
fun getChannelsForPlayer(event: SportsEvent): List<MatchChannel> {
    return if (event.channels.isNotEmpty()) {
        event.channels
    } else {
        // Fallback: create a single channel from the streamUrl
        listOf(
            MatchChannel(
                id = event.id,
                name = event.name,
                url = event.streamUrl,
                logo = event.logo,
                cookies = event.cookies
            )
        )
    }
}

/**
 * Get channels from UpcomingMatch or fallback to empty list
 */
fun getChannelsForPlayer(match: UpcomingMatch): List<MatchChannel> {
    return if (match.channels.isNotEmpty()) {
        match.channels
    } else if (match.streamUrl.isNotBlank()) {
        // Fallback: create a single channel from the streamUrl
        listOf(
            MatchChannel(
                id = match.id,
                name = match.name,
                url = match.streamUrl,
                logo = match.logo,
                cookies = ""
            )
        )
    } else {
        emptyList()
    }
}