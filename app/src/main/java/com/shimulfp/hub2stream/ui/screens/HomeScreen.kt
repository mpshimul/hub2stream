package com.shimulfp.hub2stream.ui.screens

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shimulfp.hub2stream.utils.DataUriHelper
import com.shimulfp.hub2stream.utils.Json

import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.extractor.models.MediaItemPreview
import com.shimulfp.hub2stream.extractor.models.MatchStatus
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import com.shimulfp.hub2stream.extractor.models.UpcomingMatch
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import com.shimulfp.hub2stream.models.FavoriteItem
import com.shimulfp.hub2stream.ui.components.CategoryRow
import com.shimulfp.hub2stream.ui.components.ContinueWatchingCard
import com.shimulfp.hub2stream.ui.components.MoviePoster
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.DarkPrimaryContainer
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.utils.CategoryCache
import com.shimulfp.hub2stream.utils.isTv
import com.shimulfp.hub2stream.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Set the overall visual style here (Landscape fits sports beautifully)
    val sportsCardOrientation = CardOrientation.Landscape

    // Timer trigger for real-time updates - updates every second
    var timerTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            timerTrigger++
        }
    }

    val searchButtonFocusRequester = remember { FocusRequester() }
    val liveSportsMoreFocusRequester = remember { FocusRequester() }
    val clearButtonFocusRequester = remember { FocusRequester() }
    var isSearchButtonFocused by remember { mutableStateOf(false) }
    var isLiveSportsMoreFocused by remember { mutableStateOf(false) }
    var isClearButtonFocused by remember { mutableStateOf(false) }

    // Auto-refresh sports data every 2 minutes
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(120000) // 2 minutes
            viewModel.refreshSportsData()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hub2Stream") },
                actions = {
                    IconButton(
                        onClick = { navController.navigate(Screen.Search.route) },
                        modifier = Modifier
                            .focusRequester(searchButtonFocusRequester)
                            .onFocusChanged { focusState -> isSearchButtonFocused = focusState.isFocused }
                            .then(
                                if (isSearchButtonFocused) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = FocusAccent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = if (isSearchButtonFocused) FocusAccent else MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoadingMovies && uiState.isLoadingLiveTV && uiState.isLoadingSports && uiState.isLoadingUpcoming &&
            uiState.movieRows.isEmpty() && uiState.liveChannels.isEmpty() && uiState.liveEvents.isEmpty() && uiState.upcomingMatches.isEmpty() &&
            uiState.continueWatchingItems.isEmpty() && uiState.favoriteItems.isEmpty()
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 1. Live Sports Carousel
            if (uiState.liveEvents.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Live & Upcoming",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.refreshSportsData() },
                                    enabled = !uiState.isLoadingSports
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refresh sports",
                                        modifier = if (uiState.isLoadingSports) Modifier.size(16.dp) else Modifier.size(20.dp),
                                        tint = if (uiState.isLoadingSports) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(
                                    onClick = { navController.navigate(Screen.LiveSports.route) },
                                    modifier = Modifier
                                        .focusRequester(liveSportsMoreFocusRequester)
                                        .onFocusChanged { focusState -> isLiveSportsMoreFocused = focusState.isFocused }
                                        .then(
                                            if (isLiveSportsMoreFocused) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = FocusAccent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    Text("More", color = if (isLiveSportsMoreFocused) FocusAccent else MaterialTheme.colorScheme.primary)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = if (isLiveSportsMoreFocused) FocusAccent else MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .focusProperties { up = liveSportsMoreFocusRequester }
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.liveEvents.forEach { event ->
                                SportsEventCardForCarousel(
                                    event = event,
                                    orientation = sportsCardOrientation,
                                    onClick = { playSportsEventWithPlaylist(navController, context, uiState.liveEvents, event) }
                                )
                            }
                        }
                    }
                }
            } else if (uiState.isLoadingSports) {
                item { Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) { CircularProgressIndicator() } }
            }

            // 2. FIFA World Cup - Upcoming Matches
            if (uiState.upcomingMatches.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { navController.navigate(Screen.FifaWorldCup.route) }
                            ) {
                                Text(
                                    "FIFA World Cup",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                // Countdown timer next to title
                                if (uiState.upcomingMatches.isNotEmpty()) {
                                    val nextMatch = uiState.upcomingMatches.firstOrNull()
                                    if (nextMatch != null && nextMatch.startTimeMs > 0L) {
                                        MatchCountdown(
                                            startTimeMs = nextMatch.startTimeMs,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { navController.navigate(Screen.FifaWorldCup.route) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.upcomingMatches.take(10).forEach { match ->
                                UpcomingMatchCard(
                                    match = match,
                                    orientation = sportsCardOrientation,
                                    timerTrigger = timerTrigger,  // Pass timer for real-time updates
                                    onClick = { playUpcomingMatch(navController, context, match, uiState.upcomingMatches) }
                                )
                            }
                        }
                    }
                }
            } else if (uiState.isLoadingUpcoming) {
                item { Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) { CircularProgressIndicator() } }
            }

            // 3. Live TV row
            if (uiState.liveChannels.isNotEmpty()) {
                item {
                    CategoryRow(
                        title = "Live TV",
                        items = uiState.liveChannels,
                        onItemClick = { channel -> playChannelWithPlaylist(navController, context, uiState.liveChannels, channel) },
                        onMoreClick = { navController.navigate(Screen.LiveTV.route) },
                        posterContent = { channel, requestFocus ->
                            ChannelCard(
                                channel = channel,
                                onClick = { playChannelWithPlaylist(navController, context, uiState.liveChannels, channel) },
                                modifier = Modifier.width(90.dp).height(90.dp),
                                requestFocus = requestFocus
                            )
                        },
                        isFirstRow = true
                    )
                }
            } else if (uiState.isLoadingLiveTV) {
                item { Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() } }
            }

            // 3. Continue Watching row
            if (uiState.continueWatchingItems.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Continue Watching", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = { showClearDialog = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .focusRequester(clearButtonFocusRequester)
                                    .onFocusChanged { focusState -> isClearButtonFocused = focusState.isFocused }
                                    .then(
                                        if (isClearButtonFocused) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = FocusAccent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear All",
                                    tint = if (isClearButtonFocused) FocusAccent else Color(0xFFAAAAAA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.continueWatchingItems.forEachIndexed { index, item ->
                                Box(
                                    modifier = Modifier.focusProperties { up = clearButtonFocusRequester }
                                ) {
                                    ContinueWatchingCard(
                                        item = item,
                                        onClick = {
                                            when (item.type) {
                                                "movie" -> navController.navigate(Screen.MovieDetail.pass(item.slug, item.positionSeconds * 1000L))
                                                "series" -> navController.navigate(Screen.SeriesDetail.pass(slug = item.slug, season = item.seasonNumber, episode = item.episodeNumber, resumePosition = item.positionSeconds * 1000L))
                                            }
                                        },
                                        requestFocus = index == 0
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Favorites row
            if (uiState.favoriteItems.isNotEmpty()) {
                item {
                    CategoryRow(
                        title = "My Favorites",
                        items = uiState.favoriteItems.map { fav ->
                            if (fav.type == "movie") {
                                MediaItemPreview.MoviePreview(slug = fav.slug, title = fav.title, posterUrl = fav.posterUrl, year = null)
                            } else {
                                MediaItemPreview.SeriesPreview(slug = fav.slug, title = fav.title, posterUrl = fav.posterUrl, year = null)
                            }
                        },
                        onItemClick = { item ->
                            when (item) {
                                is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                            }
                        },
                        onMoreClick = { },
                        posterContent = { item, requestFocus ->
                            MoviePoster(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                        is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                                    }
                                },
                                requestFocus = requestFocus
                            )
                        }
                    )
                }
            }

            // 5. Movie & Series rows
            if (uiState.movieRows.isNotEmpty()) {
                items(uiState.movieRows.size) { idx ->
                    val row = uiState.movieRows[idx]
                    val categoryForMore = when (row.title) {
                        "Korean TV Series" -> "Korean+Series"
                        "Indian TV Series" -> "Indian+Series"
                        "Turkish TV Series" -> "Turkish+Series"
                        "Bangla Drama" -> "Bangla+Drama"
                        "Hindi Series" -> "Hindi+Series"
                        "English Series" -> "English+Series"
                        else -> null
                    }

                    CategoryRow(
                        title = row.title,
                        items = row.items,
                        onItemClick = { item ->
                            when (item) {
                                is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                            }
                        },
                        onMoreClick = {
                            if (categoryForMore != null) {
                                navController.navigate(Screen.Category.pass(row.title, true, categoryForMore))
                            } else {
                                CategoryCache.currentItems = row.items
                                CategoryCache.categoryType = row.categoryType
                                CategoryCache.categoryData = row.categoryData
                                CategoryCache.currentTitle = row.title
                                navController.navigate(Screen.Category.pass(row.title, false))
                            }
                        },
                        posterContent = { item, requestFocus ->
                            MoviePoster(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is MediaItemPreview.MoviePreview -> navController.navigate(Screen.MovieDetail.pass(item.slug))
                                        is MediaItemPreview.SeriesPreview -> navController.navigate(Screen.SeriesDetail.pass(item.slug))
                                    }
                                },
                                requestFocus = requestFocus
                            )
                        }
                    )
                }
            } else if (uiState.isLoadingMovies) {
                item { Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }

    if (showClearDialog) {
        val confirmFocusRequester = remember { FocusRequester() }
        val cancelFocusRequester = remember { FocusRequester() }

        LaunchedEffect(showClearDialog) {
            try {
                delay(50)
                confirmFocusRequester.requestFocus()
            } catch (e: Exception) { /* No-op */ }
        }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Continue Watching") },
            text = { Text("Are you sure you want to clear all items from your continue watching list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.clearContinueWatching()
                            showClearDialog = false
                        }
                    },
                    modifier = Modifier
                        .focusRequester(confirmFocusRequester)
                        .focusProperties {
                            left = cancelFocusRequester
                            right = cancelFocusRequester
                        }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .focusProperties {
                            left = confirmFocusRequester
                            right = confirmFocusRequester
                        }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SportsEventCardForCarousel(
    event: SportsEvent,
    orientation: CardOrientation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = context.isTv()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isPlayable = event.streamUrl.isNotBlank()

    // Adaptive Size logic handling TV and both Phone positions
    // Unified Adaptive Size logic handling TV, Phone Landscape, and Phone Portrait explicitly
    val sizeModifier = if (isTv || isLandscape) {
        // Shared 3-card split layout for TV and Phone-Landscape
        val screenWidthDp = configuration.screenWidthDp.dp
        val totalHorizontalPadding = 32.dp // Row padding bounds (16.dp * 2)
        val horizontalSpacingGaps = 24.dp  // Spacing gaps between components (12.dp * 2)

        val calculatedWidth = (screenWidthDp - totalHorizontalPadding - horizontalSpacingGaps) / 3
        val calculatedHeight = if (orientation == CardOrientation.Landscape) {
            calculatedWidth * 9 / 16  // 16:9 widescreen
        } else {
            calculatedWidth * 4 / 3   // 3:4 portrait poster
        }
        Modifier.width(calculatedWidth).height(calculatedHeight)
    } else {
        // FIX FOR PORTRAIT MODE INSIDE HORIZONTAL SCROLL:
        // Calculate 100% of the available screen width explicitly so it doesn't expand infinitely
        val screenWidthDp = configuration.screenWidthDp.dp
        val totalHorizontalPadding = 32.dp // Matches your Row's horizontal padding (16.dp * 2)
        val calculatedWidth = screenWidthDp - totalHorizontalPadding

        val mobileHeight = if (orientation == CardOrientation.Landscape) 260.dp else 360.dp

        Modifier.width(calculatedWidth).height(mobileHeight)
    }

    Card(
        modifier = modifier
            .then(sizeModifier)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .focusable()
            .then(
                if (isFocused) Modifier
                    .scale(1.03f)
                    .border(3.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(
                enabled = isPlayable,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (isPlayable) onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isFocused) 16.dp else 4.dp)
    ) {
        // CRITICAL FIX: Changed the root layout of the card to a Box with fillMaxSize()
        // to force the AsyncImage to stretch completely across the calculated width.
        Box(modifier = Modifier.fillMaxSize()) {

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(event.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = event.name,
                modifier = Modifier.fillMaxSize(), // Spans across the entire card area
                contentScale = ContentScale.Crop   // Crops the image to match perfectly
            )

            // STATUS BADGE - Shows Live or Upcoming status
            if (event.status.isNotBlank()) {
                val isLive = event.status.equals("Live", ignoreCase = true)
                val isUpcoming = event.status.equals("Upcoming", ignoreCase = true)

                if (isLive || isUpcoming) {
                    // Pulsing animation for Live badge
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.7f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 8.dp)
                            .then(if (isLive) Modifier.alpha(alpha) else Modifier),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLive) Color(0xFFE53935) else Color(0xFF2196F3)
                    ) {
                        Text(
                            if (isLive) "LIVE" else "UPCOMING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // COUNTDOWN TIMER FOR UPCOMING MATCHES
            if (event.status.equals("Upcoming", ignoreCase = true) && event.startTime.isNotBlank()) {
                val startTimeMs = try {
                    // Try multiple date formats
                    val formats = listOf(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd'T'HH:mm:ssZ",
                        "yyyy-MM-dd'T'HH:mm:ss.SSS",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd"
                    )

                    var timestamp = 0L
                    for (format in formats) {
                        try {
                            val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            timestamp = sdf.parse(event.startTime)?.time ?: 0L
                            if (timestamp > 0) break
                        } catch (e: Exception) {
                            // Try next format
                        }
                    }
                    timestamp
                } catch (e: Exception) {
                    0L
                }

                if (startTimeMs > 0) {
                    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            currentTime = System.currentTimeMillis()
                        }
                    }

                    val timeRemainingMs = startTimeMs - currentTime

                    if (timeRemainingMs > 0) {
                        val days = timeRemainingMs / (1000 * 60 * 60 * 24)
                        val hours = (timeRemainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                        val minutes = (timeRemainingMs % (1000 * 60 * 60)) / (1000 * 60)
                        val seconds = (timeRemainingMs % (1000 * 60)) / 1000

                        val countdownText = when {
                            days > 0 -> "${days}d ${hours}h"
                            hours > 0 -> "${hours}h ${minutes}m"
                            else -> "${minutes}m ${seconds}s"
                        }

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 8.dp, top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8C200)
                        ) {
                            Text(
                                countdownText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Dynamic background scrim matching content layouts
            val overlayFraction = if (orientation == CardOrientation.Landscape) {
                if (isLandscape && !isTv) 0.70f else 0.55f
            } else 0.35f

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(overlayFraction)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(if (orientation == CardOrientation.Landscape) 8.dp else 14.dp)
            ) {
                Text(
                    text = event.name,
                    color = Color.White,
                    fontSize = if (isTv) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (orientation == CardOrientation.Landscape) 1 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 6f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                        )
                    )
                )
                if (event.tournament.isNotBlank()) {
                    Text(
                        text = event.tournament,
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = if (isTv) 13.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 4f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f)
                            )
                        )
                    )
                }
            }

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlayable) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (orientation == CardOrientation.Landscape) 42.dp else 56.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Lock,
                            "Not available yet",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(if (orientation == CardOrientation.Landscape) 42.dp else 56.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun playChannelWithPlaylist(navController: NavController, context: Context, allChannels: List<LiveChannel>, selected: LiveChannel) {
    val playlist = allChannels.map { mapOf("url" to it.streamUrl, "title" to it.name, "logo" to DataUriHelper.resolveToString(context, it.logo), "sourceId" to it.sourceId) }
    val json = Json.toJson(playlist)
    navController.navigate(Screen.LivePlayer.pass(url = selected.streamUrl, title = selected.name, channelsJson = URLEncoder.encode(json, "UTF-8"), startIndex = allChannels.indexOf(selected)))
}

private fun playSportsEventWithPlaylist(navController: NavController, context: Context, allEvents: List<SportsEvent>, selected: SportsEvent) {
    if (selected.streamUrl.isBlank()) {
        // Selected event doesn't have a stream URL (likely upcoming)
        return
    }

    // Use the match-specific channels if available, otherwise fall back to single channel
    val channels = if (selected.channels.isNotEmpty()) {
        // Use match's channel list from M3U
        selected.channels.map { channel ->
            mapOf(
                "url" to channel.url,
                "title" to channel.name,
                "id" to channel.id,
                "logo" to DataUriHelper.resolveToString(context, channel.logo),
                "cookies" to channel.cookies  // Include cookies for authenticated streams
            )
        }
    } else {
        // Fallback: single channel
        listOf(mapOf("url" to selected.streamUrl, "title" to selected.name, "id" to selected.id, "logo" to DataUriHelper.resolveToString(context, selected.logo), "cookies" to selected.cookies))
    }

    // Find the index (default to first channel)
    val startIndex = 0

    val json = Json.toJson(channels)
    navController.navigate(Screen.LivePlayer.pass(url = selected.streamUrl, title = selected.name, channelsJson = URLEncoder.encode(json, "UTF-8"), startIndex = startIndex))
}

/**
 * Play an upcoming match with match-specific channels from M3U
 */
private fun playUpcomingMatch(
    navController: NavController,
    context: Context,
    selected: UpcomingMatch,
    allMatches: List<UpcomingMatch>
) {
    if (selected.streamUrl.isBlank()) {
        Log.w("HomeScreen", "Selected match has no stream URL: ${selected.name}")
        return
    }

    // Get validated channels from validation service (these are the working M3U links)
    val validatedChannels = com.shimulfp.hub2stream.data.FIFAChannelValidationService.getChannelsForMatch(selected.id)
    Log.d("HomeScreen", "Validated channels count: ${validatedChannels.size}")

    // Merge API channel (from match channels) with validated channels
    // API channel: "Live Stream" from Aoneroom (only in live matches)
    // Validated channels: M3U from validation service
    val channelsToUse = if (selected.status == "LIVE") {
        // For live matches, check if match has API "Live Stream" channel
        val apiChannel = selected.channels.find { it.name == "Live Stream" }
        if (apiChannel != null) {
            Log.d("HomeScreen", "Merging: API channel (${apiChannel.url}) + ${validatedChannels.size} validated channels")
            // Add API channel at the beginning, then validated channels
            listOf(apiChannel) + validatedChannels
        } else {
            Log.d("HomeScreen", "Live match has no API channel, using validated channels only")
            validatedChannels
        }
    } else if (validatedChannels.isNotEmpty()) {
        Log.d("HomeScreen", "Non-live match, using validated channels only")
        validatedChannels
    } else if (selected.channels.isNotEmpty()) {
        Log.d("HomeScreen", "Using original match channels (no validated channels available)")
        selected.channels
    } else {
        Log.d("HomeScreen", "Using fallback stream URL")
        emptyList()
    }

    Log.d("HomeScreen", "Final channels to use: ${channelsToUse.size}")

    // Create playlist from channels
    val channels = if (channelsToUse.isNotEmpty()) {
        channelsToUse.map { channel ->
            mapOf(
                "url" to channel.url,
                "title" to channel.name,
                "id" to channel.id,
                "logo" to DataUriHelper.resolveToString(context, channel.logo),
                "cookies" to channel.cookies  // Include cookies for authenticated streams
            )
        }
    } else {
        // Fallback: single channel from streamUrl
        listOf(mapOf("url" to selected.streamUrl, "title" to selected.name, "id" to selected.id, "logo" to DataUriHelper.resolveToString(context, selected.logo), "cookies" to ""))
    }

    val channelsJson = Json.toJson(channels)
    val encoded = URLEncoder.encode(channelsJson, "UTF-8")

    // Default to first channel
    val startIndex = 0

    navController.navigate(
        Screen.LivePlayer.pass(
            url = channels.first()["url"]!!,
            title = selected.name,
            channelsJson = encoded,
            startIndex = startIndex
        )
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UpcomingMatchCard(
    match: UpcomingMatch,
    orientation: CardOrientation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    timerTrigger: Int = 0  // Trigger for real-time timer updates (screen-controlled)
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = context.isTv()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Direct raw CDN link for Coil to fetch the asset safely
    val centerOverlayImageUrl = "https://raw.githubusercontent.com/mpshimul/hub2stream/main/WC26_Logo.webp"

    // Performance optimization for gradients
    val team1Gradient = remember {
        Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
        )
    }
    val team2Gradient = remember {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
        )
    }
    val verticalBackgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.8f),
                Color.Black.copy(alpha = 0.4f),
                Color.Black.copy(alpha = 0.8f)
            )
        )
    }

    // Dynamic width / height measurements
    val sizeModifier = if (isTv || isLandscape) {
        val screenWidthDp = configuration.screenWidthDp.dp
        val totalHorizontalPadding = 24.dp
        val horizontalSpacingGaps = 24.dp
        val calculatedWidth = (screenWidthDp - totalHorizontalPadding - horizontalSpacingGaps) / 2
        val calculatedHeight = calculatedWidth * 5 / 15
        Modifier.width(calculatedWidth).height(calculatedHeight)
    } else {
        val screenWidthDp = configuration.screenWidthDp.dp
        val totalHorizontalPadding = 32.dp
        val calculatedWidth = screenWidthDp - totalHorizontalPadding
        Modifier.width(calculatedWidth).height(150.dp)
    }

    Card(
        modifier = modifier
            .then(sizeModifier)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .focusable()
            .then(
                if (isFocused) Modifier
                    .scale(1.03f)
                    .border(3.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .border(
                width = 3.dp,
                color = DarkPrimaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isFocused) 16.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // LAYER 1: Split background with both team flags
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Team 1 flag background (left half)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (match.team1Logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.team1Logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().background(team1Gradient))
                    }

                    // Team 2 flag background (right half)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (match.team2Logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.team2Logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().background(team2Gradient))
                    }
                }

                // Vertical gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(verticalBackgroundGradient)
                )
            }

            // LAYER 2: Tournament Custom Logo Overlay centered above backgrounds
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(centerOverlayImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Tournament Watermark",
                contentScale = ContentScale.Fit,
                alpha = 0.7f, // Made translucent to subtle up the center visual behind text fields
                modifier = Modifier
                    .fillMaxHeight(1f) // Automatically scales naturally relative to your card sizing
                    .align(Alignment.Center)

            )

            // LAYER 3: Status badge on top-left edge (based on real-time status)
            val realTimeStatus = match.getRealTimeStatus()
            val (badgeText, badgeColor) = when (realTimeStatus) {
                MatchStatus.LIVE -> "LIVE" to Color.Red
                MatchStatus.SOON -> "SOON" to Color(0xFFFF6B00)
                MatchStatus.TODAY -> "TODAY" to Color(0xFF2196F3)
                MatchStatus.UPCOMING -> "UPCOMING" to Color(0xFFE8C200)
                MatchStatus.ENDED -> "ENDED" to Color.Gray
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = badgeColor
            ) {
                Text(
                    badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // LAYER 4: Foreground content elements
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.2f))

                // Date pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Green.copy(alpha = 0.30f)
                ) {
                    Text(
                        match.date.uppercase(),
                        fontSize = if (isTv) 18.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 8f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                            )
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Time with real-time countdown
                Text(
                    "${match.time} (${match.getFormattedTimeInfo()})",
                    fontSize = if (isTv) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            blurRadius = 10f,
                            offset = androidx.compose.ui.geometry.Offset(0f, 5f)
                        )
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Show score for ENDED matches above team names
                if (realTimeStatus == MatchStatus.ENDED && match.team1Score.isNotBlank() && match.team2Score.isNotBlank()) {
                    Text(
                        "${match.team1Score} - ${match.team2Score}",
                        fontSize = if (isTv) 36.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 12f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 3f)
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Team details row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team 1 configuration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = match.team1,
                            fontSize = if (isTv) 20.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End, // Ensures long text still aligns nicely against the right side
                            modifier = Modifier.weight(1f), // <--- FIX: Forces text to occupy remaining space & wrap properly
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 10f,
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                                )
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (match.team1Logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.team1Logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = match.team1,
                                modifier = Modifier
                                    .size(50.dp) // Now safely guaranteed to stay at 50.dp
                                    .border(
                                        width = 2.dp,
                                        color = FocusAccent,
                                        shape = RoundedCornerShape(25.dp)
                                    )
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(25.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // VS text
                    Text(
                        "VS",
                        fontSize = if (isTv) 30.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE8C200),
                        modifier = Modifier.padding(horizontal = 6.dp),
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 12f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f)
                            )
                        )
                    )

                    // Team 2 configuration
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (match.team2Logo.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(match.team2Logo)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = match.team2,
                                modifier = Modifier
                                    .size(50.dp) // Safe from shrinking
                                    .border(
                                        width = 2.dp,
                                        color = FocusAccent,
                                        shape = RoundedCornerShape(25.dp)
                                    )
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(25.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = match.team2,
                            fontSize = if (isTv) 20.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start, // Aligns beautifully next to the logo on the left
                            modifier = Modifier.weight(1f), // <--- FIX: Keeps Team 2 name bounded and forces 2-line wrapping
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    blurRadius = 10f,
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                                )
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Tournament/round label at bottom
                if (match.tournament.isNotBlank()) {
                    Text(
                        match.tournament,
                        fontSize = if (isTv) 11.sp else 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                blurRadius = 4f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f)
                            )
                        )
                    )
                }
            }

            // LAYER 5: Interactive D-Pad Focus overlay mask
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
    }
}
@Composable
fun MatchCountdown(
    startTimeMs: Long,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Update current time every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val timeRemainingMs = startTimeMs - currentTime

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE8C200)
    ) {
        if (timeRemainingMs <= 0) {
            // Match has started
            Text(
                "LIVE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                letterSpacing = 1.sp
            )
        } else {
            // Countdown
            val days = timeRemainingMs / (1000 * 60 * 60 * 24)
            val hours = (timeRemainingMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
            val minutes = (timeRemainingMs % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (timeRemainingMs % (1000 * 60)) / 1000

            val countdownText = when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                else -> "${minutes}m ${seconds}s"
            }

            Text(
                countdownText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                letterSpacing = 0.5.sp
            )
        }
    }
}
