package com.shimulfp.hub2stream.ui.screens

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.shimulfp.hub2stream.viewmodels.FifaWorldCupViewModel
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import com.shimulfp.hub2stream.extractor.models.MatchStatus
import com.shimulfp.hub2stream.ui.navigation.Screen
import java.net.URLEncoder

enum class MatchFilter {
    LIVE, TODAY, UPCOMING, ENDED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FifaWorldCupCategoryScreen(
    navController: NavController,
    viewModel: FifaWorldCupViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val allMatches by viewModel.allMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.error.collectAsState()

    // Filter state - default to LIVE (not ALL)
    var currentFilter by remember { mutableStateOf(MatchFilter.LIVE) }

    // Timer trigger - updates every second to refresh timer displays
    var timerTrigger by remember { mutableStateOf(0) }

    // Real-time timer update - every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            timerTrigger++
        }
    }

    // Filter matches based on current selection (from ALL matches, not just paginated)
    val filteredMatches by remember(allMatches, currentFilter) {
        derivedStateOf {
            val currentTime = System.currentTimeMillis()
            allMatches.filter { match ->
                val realTimeStatus = match.getRealTimeStatus()
                when (currentFilter) {
                    MatchFilter.LIVE -> realTimeStatus == MatchStatus.LIVE
                    MatchFilter.TODAY -> {
                        val diffMs = match.startTimeMs - currentTime
                        diffMs in 0..(24 * 60 * 60 * 1000) && realTimeStatus != MatchStatus.ENDED
                    }
                    MatchFilter.UPCOMING -> realTimeStatus == MatchStatus.UPCOMING || realTimeStatus == MatchStatus.SOON
                    MatchFilter.ENDED -> realTimeStatus == MatchStatus.ENDED
                }
            }
        }
    }

    // Limit displayed matches to avoid performance issues (show first 104 filtered matches)
    val displayedMatches by remember(filteredMatches) {
        derivedStateOf {
            filteredMatches.take(104)
        }
    }

    // Count matches by status for filter buttons (from ALL matches)
    val filterCounts by remember(allMatches, timerTrigger) {
        derivedStateOf {
            val currentTime = System.currentTimeMillis()
            mapOf(
                MatchFilter.LIVE to allMatches.count { it.getRealTimeStatus() == MatchStatus.LIVE },
                MatchFilter.TODAY to allMatches.count {
                    val diffMs = it.startTimeMs - currentTime
                    diffMs in 0..(24 * 60 * 60 * 1000) && it.getRealTimeStatus() != MatchStatus.ENDED
                },
                MatchFilter.UPCOMING to allMatches.count {
                    val status = it.getRealTimeStatus()
                    status == MatchStatus.UPCOMING || status == MatchStatus.SOON
                },
                MatchFilter.ENDED to allMatches.count { it.getRealTimeStatus() == MatchStatus.ENDED }
            )
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 2 else 1

    val gridState = rememberLazyGridState()

    // Load data when screen opens (ViewModel handles duplicate prevention)
    LaunchedEffect(Unit) {
        viewModel.loadMatches()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FIFA World Cup") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMsg != null && allMatches.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: $errorMsg")
                    Button(onClick = { viewModel.loadMatches() }) {
                        Text("Retry")
                    }
                }
            }
            allMatches.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("No upcoming FIFA World Cup matches found.")
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Filter chips
                    FilterChipsRow(
                        currentFilter = currentFilter,
                        filterCounts = filterCounts,
                        onFilterSelected = { filter -> currentFilter = filter }
                    )

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, if (filteredMatches.size > 104) 80.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedMatches, key = { it.id }) { match ->
                            UpcomingMatchCard(
                                match = match,
                                orientation = if (isLandscape) CardOrientation.Landscape else CardOrientation.Portrait,
                                timerTrigger = timerTrigger,  // Pass timer trigger for real-time updates
                                onClick = {
                                    playUpcomingMatchWithChannels(navController, allMatches, match)
                                }
                            )
                        }
                        if (filteredMatches.size > 104) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Showing first 104 of ${filteredMatches.size} matches",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    currentFilter: MatchFilter,
    filterCounts: Map<MatchFilter, Int>,
    onFilterSelected: (MatchFilter) -> Unit
) {
    val filters = listOf(MatchFilter.LIVE, MatchFilter.TODAY, MatchFilter.UPCOMING, MatchFilter.ENDED)

    // Create focus requesters for each filter chip
    val focusRequesters = remember(filters) { filters.map { FocusRequester() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEachIndexed { index, filter ->
            val focusRequester = focusRequesters[index]
            var isFocused by remember { mutableStateOf(false) }

            FilterChip(
                selected = currentFilter == filter,
                onClick = {
                    onFilterSelected(filter)
                },
                label = {
                    val count = filterCounts[filter] ?: 0
                    Text("${filter.name} ($count)")
                },
                modifier = Modifier
                    .focusable()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            // Auto-select when focused for better TV navigation
                            onFilterSelected(filter)
                        }
                    },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (currentFilter == filter) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (isFocused) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    labelColor = if (currentFilter == filter || isFocused) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            )
        }
    }
}

/**
 * Navigate to player with upcoming match channels and cookies
 */
private fun playUpcomingMatchWithChannels(
    navController: NavController,
    allMatches: List<UpcomingMatch>,
    selected: UpcomingMatch
) {
    Log.d("FifaWorldCupScreen", "playUpcomingMatchWithChannels called")
    Log.d("FifaWorldCupScreen", "Selected match: ${selected.name}, channels count: ${selected.channels.size}")

    // Get validated channels from validation service (these are the working M3U links)
    val validatedChannels = com.shimulfp.hub2stream.data.FIFAChannelValidationService.getChannelsForMatch(selected.id)
    Log.d("FifaWorldCupScreen", "Validated channels count: ${validatedChannels.size}")

    // Merge API channel (from match channels) with validated channels
    // API channel: "Live Stream" from Aoneroom (only in live matches)
    // Validated channels: M3U from validation service
    val channelsToUse = if (selected.status == "LIVE") {
        // For live matches, check if match has API "Live Stream" channel
        val apiChannel = selected.channels.find { it.name == "Live Stream" }
        if (apiChannel != null) {
            Log.d("FifaWorldCupScreen", "Merging: API channel (${apiChannel.url}) + ${validatedChannels.size} validated channels")
            // Add API channel at the beginning, then validated channels
            listOf(apiChannel) + validatedChannels
        } else {
            Log.d("FifaWorldCupScreen", "Live match has no API channel, using validated channels only")
            validatedChannels
        }
    } else {
        // For non-live matches, only use validated channels
        Log.d("FifaWorldCupScreen", "Non-live match, using validated channels only")
        validatedChannels
    }

    Log.d("FifaWorldCupScreen", "Final channels to use: ${channelsToUse.size}")

    // Create playlist from channels
    val playlist = if (channelsToUse.isNotEmpty()) {
        // Use validated or match-specific channels
        channelsToUse.map { channel ->
            Log.d("FifaWorldCupScreen", "  Channel: ${channel.name}, cookies length: ${channel.cookies.length}")
            mapOf(
                "url" to channel.url,
                "title" to channel.name,
                "id" to channel.id,
                "logo" to channel.logo,
                "cookies" to channel.cookies
            )
        }
    } else {
        // Fallback to default stream URL
        Log.d("FifaWorldCupScreen", "No channels in match, using fallback")
        listOf(mapOf(
            "url" to selected.streamUrl,
            "title" to selected.name,
            "id" to selected.id,
            "logo" to selected.logo,
            "cookies" to ""
        ))
    }

    val mapper = jacksonObjectMapper()
    val channelsJson = mapper.writeValueAsString(playlist)
    Log.d("FifaWorldCupScreen", "Channels JSON length: ${channelsJson.length}")

    val encoded = URLEncoder.encode(channelsJson, "UTF-8")

    // Find the selected channel index
    val startIndex = if (channelsToUse.isNotEmpty()) {
        0 // Default to first validated channel
    } else {
        allMatches.indexOf(selected)
    }

    val defaultChannel = channelsToUse.firstOrNull()
    Log.d("FifaWorldCupScreen", "Navigating to player for match: ${selected.name}")
    Log.d("FifaWorldCupScreen", "Default channel: ${defaultChannel?.name}")
    Log.d("FifaWorldCupScreen", "Total channels: ${channelsToUse.size}, Cookies embedded in channelsJson")

    navController.navigate(
        Screen.LivePlayer.pass(
            url = selected.streamUrl,
            title = selected.name,
            channelsJson = encoded,
            startIndex = startIndex
        )
    )
}