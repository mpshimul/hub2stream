package com.shimulfp.hub2stream.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.shimulfp.hub2stream.extractor.models.Episode
import com.shimulfp.hub2stream.extractor.models.Series
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.DarkBackground
import com.shimulfp.hub2stream.ui.theme.DarkSurface
import com.shimulfp.hub2stream.ui.theme.DarkSurfaceVariant
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.viewmodels.DetailUiState
import com.shimulfp.hub2stream.viewmodels.DetailViewModel
import com.shimulfp.hub2stream.data.ContinueWatchingRepository
import com.shimulfp.hub2stream.data.FavoritesRepository
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import com.shimulfp.hub2stream.models.FavoriteItem
import kotlinx.coroutines.launch

// ======================== Movie Detail Screen ========================

@Composable
fun MovieDetailScreen(
    navController: NavController,
    slug: String,
    resumePosition: Long = 0L
) {
    android.util.Log.d("MovieDetail", "MovieDetailScreen initialized - slug: $slug, resumePosition: ${resumePosition}ms")
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DetailViewModel(application) as T
            }
        }
    )

    LaunchedEffect(slug) {
        android.util.Log.d("MovieDetail", "LaunchedEffect calling viewModel.loadMovie - slug: $slug")
        viewModel.loadMovie(slug)
    }
    val uiState by viewModel.uiState.collectAsState()

    // Load continue watching data to get resume position
    val continueWatchingRepo = remember { ContinueWatchingRepository(application) }
    val continueWatchingItems by continueWatchingRepo.items.collectAsState(initial = emptyList())

    // Load favorites repository
    val favoritesRepo = remember { FavoritesRepository(application) }
    val favoritesItems by favoritesRepo.items.collectAsState(initial = emptyList())
    var isFavorite by remember(slug, favoritesItems) {
        mutableStateOf(favoritesItems.any { it.slug == slug && it.type == "movie" })
    }
    var isTogglingFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Get resume position for this movie
    val movieResumePosition = remember(continueWatchingItems, slug) {
        val item = continueWatchingItems.find { it.slug == slug && it.type == "movie" }
        (item?.positionSeconds ?: resumePosition / 1000) * 1000  // Convert to milliseconds
    }
    android.util.Log.d("MovieDetail", "Calculated movieResumePosition: ${movieResumePosition}ms from continue watching")

    // Check if this is a fresh navigation (resumePosition > 0 passed from continue watching click)
    // vs being restored from saved state (resumePosition = 0)
    val isFreshContinueWatching = resumePosition > 0
    android.util.Log.d("MovieDetail", "isFreshContinueWatching: $isFreshContinueWatching (passed resumePosition: $resumePosition)")

    // Flag to track if auto-play has already been attempted for this specific movie
    // Using rememberSaveable to persist across screen recreations
    var hasAutoPlayed by rememberSaveable(slug) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = uiState) {
            is DetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = FocusAccent)
            }
            is DetailUiState.MovieSuccess -> {
                val streamUrl = state.movie.streamUrl
                val hasStreamUrl = streamUrl.isNotBlank()
                android.util.Log.d("MovieDetail", "Movie loaded - slug: $slug, streamUrl: '$streamUrl', hasStreamUrl: $hasStreamUrl, title: ${state.movie.title}")
                android.util.Log.d("MovieDetail", "isFreshContinueWatching: $isFreshContinueWatching, resumePosition: $resumePosition, hasAutoPlayed: $hasAutoPlayed")

                // Auto-play only if:
                // 1. This is a fresh "continue watching" click (isFreshContinueWatching = true)
                // 2. Resume position > 0
                // 3. Stream URL exists (even if it's moviebox:// format)
                // 4. Haven't auto-played yet
                LaunchedEffect(slug, state) {
                    android.util.Log.d("MovieDetail", "=== LaunchedEffect START === slug: $slug, resumePosition: $movieResumePosition, hasAutoPlayed: $hasAutoPlayed, hasStreamUrl: $hasStreamUrl, isFreshContinueWatching: $isFreshContinueWatching")
                    android.util.Log.d("MovieDetail", "State check - is MovieSuccess: ${state is DetailUiState.MovieSuccess}, streamUrl value: '$streamUrl'")

                    if (isFreshContinueWatching && movieResumePosition > 0 && hasStreamUrl && !hasAutoPlayed) {
                        // FINAL SAFETY CHECK: Ensure streamUrl is not blank before navigating
                        if (streamUrl.isBlank()) {
                            android.util.Log.e("MovieDetail", "❌ CRITICAL ERROR: hasStreamUrl=true but streamUrl is BLANK! This is a bug!")
                            android.util.Log.e("MovieDetail", "❌ NOT navigating to PlayerScreen to avoid crash")
                            return@LaunchedEffect
                        }

                        android.util.Log.d("MovieDetail", "✅ Auto-playing movie from resumePosition: ${movieResumePosition}ms, URL: '${streamUrl}'")
                        navController.navigate(
                            Screen.Player.pass(
                                url = streamUrl,
                                title = state.movie.title,
                                isLive = false,
                                slug = slug,
                                poster = state.movie.posterUrl,
                                type = "movie",
                                resumePosition = movieResumePosition
                            )
                        )
                        hasAutoPlayed = true
                        android.util.Log.d("MovieDetail", "✅ Navigation initiated, hasAutoPlayed set to true")
                    } else {
                        android.util.Log.d("MovieDetail", "❌ Skipping auto-play - hasAutoPlayed: $hasAutoPlayed, resumePosition: $movieResumePosition, hasStreamUrl: $hasStreamUrl, isFreshContinueWatching: $isFreshContinueWatching")
                    }
                    android.util.Log.d("MovieDetail", "=== LaunchedEffect END ===")
                }

                if (!hasStreamUrl) {
                    // Show error when stream URL is not available
                    android.util.Log.e("MovieDetail", "ERROR: streamUrl is blank! Movie: ${state.movie.title}, slug: $slug")
                    Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Stream not available",
                                color = Color(0xFFCC4444),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "This movie's streaming information could not be loaded. The content may have been removed or is temporarily unavailable.",
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadMovie(slug) },
                                colors = ButtonDefaults.buttonColors(containerColor = FocusAccent)
                            ) { Text("Retry", color = Color.Black) }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                            ) { Text("Go Back", color = Color.White) }
                        }
                    }
                } else {
                    val toggleFavorite: () -> Unit = {
                        if (!isTogglingFavorite) {
                            isTogglingFavorite = true
                            val movie = state.movie
                            val favoriteItem = FavoriteItem(
                                contentId = slug,
                                slug = slug,
                                title = movie.title,
                                posterUrl = movie.posterUrl,
                                type = "movie"
                            )
                            android.util.Log.d("MovieDetail", "Toggling favorite - slug: $slug, isFavorite: $isFavorite")
                            scope.launch {
                                val newFavoriteState = favoritesRepo.toggleItem(favoriteItem)
                                isFavorite = newFavoriteState
                                isTogglingFavorite = false
                            }
                        }
                    }

                    MovieDetailLayout(
                        title = state.movie.title,
                        posterUrl = state.movie.posterUrl,
                        backdropUrl = state.movie.backdropUrl,
                        year = state.movie.year?.toString() ?: "",
                        rating = state.movie.rating?.toString() ?: "",
                        duration = state.movie.duration,
                        director = state.movie.director,
                        genres = state.movie.genres,
                        plot = state.movie.plot,
                        isFavorite = isFavorite,
                        onToggleFavorite = toggleFavorite,
                        resumePosition = movieResumePosition,
                        onPlayClick = {
                            android.util.Log.d("MovieDetail", "Play click - slug: $slug, streamUrl: '$streamUrl', resumePosition: ${movieResumePosition}ms")
                            if (streamUrl.isBlank()) {
                                android.util.Log.e("MovieDetail", "ERROR: Play clicked but streamUrl is blank! Not navigating to PlayerScreen.")
                            } else {
                                navController.navigate(
                                    Screen.Player.pass(
                                        url = streamUrl,
                                        title = state.movie.title,
                                        isLive = false,
                                        slug = slug,
                                        poster = state.movie.posterUrl,
                                        type = "movie",
                                        resumePosition = movieResumePosition
                                    )
                                )
                            }
                        },
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            is DetailUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = Color(0xFFCC4444), fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadMovie(slug) },
                        colors = ButtonDefaults.buttonColors(containerColor = FocusAccent)
                    ) { Text("Retry", color = Color.Black) }
                }
            }
            else -> {}
        }
    }
}

// ======================== Series Detail Screen ========================

@Composable
fun SeriesDetailScreen(
    navController: NavController,
    slug: String,
    season: Int = 0,
    episode: Int = 0,
    resumePosition: Long = 0L
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DetailViewModel(application) as T
            }
        }
    )

    LaunchedEffect(slug) { viewModel.loadSeries(slug) }
    val uiState by viewModel.uiState.collectAsState()

    // Flag to track if auto-play has already been attempted for this specific series/season/episode
    var hasAutoPlayed by rememberSaveable(slug, season, episode) { mutableStateOf(false) }

    // Load continue watching data for progress display and resume positions
    val continueWatchingRepo = remember { ContinueWatchingRepository(application) }
    val continueWatchingItems by continueWatchingRepo.items.collectAsState(initial = emptyList())

    // Load favorites repository
    val favoritesRepo = remember { FavoritesRepository(application) }
    val favoritesItems by favoritesRepo.items.collectAsState(initial = emptyList())
    var isFavorite by remember(slug, favoritesItems) {
        mutableStateOf(favoritesItems.any { it.slug == slug && it.type == "series" })
    }
    var isTogglingFavorite by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Create a map: (season, episode) -> progress percentage
    val episodeProgressMap = remember(continueWatchingItems) {
        continueWatchingItems
            .filter { it.slug == slug && it.type == "series" }
            .associate { "${it.seasonNumber}_${it.episodeNumber}" to it.progressPercentage }
    }

    // Create a map: (season, episode) -> resume position in milliseconds
    val episodeResumePositionMap = remember(continueWatchingItems) {
        continueWatchingItems
            .filter { it.slug == slug && it.type == "series" }
            .associate { "${it.seasonNumber}_${it.episodeNumber}" to (it.positionSeconds * 1000) } // Convert seconds to milliseconds
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (val state = uiState) {
            is DetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = FocusAccent)
            }
            is DetailUiState.SeriesSuccess -> {
                val series = state.series
                val allEpisodes = series.seasons.flatMap { it.episodes }
                val limitedEpisodes = allEpisodes.take(200)

                // Auto-play if season/episode specified and not already played
                LaunchedEffect(season, episode) {
                    android.util.Log.d("SeriesDetail", "LaunchedEffect triggered - season: $season, episode: $episode, hasAutoPlayed: $hasAutoPlayed")
                    if (season > 0 && episode > 0 && !hasAutoPlayed) {
                        android.util.Log.d("SeriesDetail", "Auto-playing episode $episode from season $season")
                        val targetSeason = series.seasons.find { it.seasonNumber == season }
                        val targetEpisode = targetSeason?.episodes?.find { it.episodeNumber == episode }
                        if (targetEpisode != null) {
                            // Get resume position from continue watching
                            val resumePos = episodeResumePositionMap["${season}_$episode"] ?: resumePosition
                            android.util.Log.d("SeriesDetail", "Auto-play resumePosition: ${resumePos}ms")
                            val episodesList = limitedEpisodes.mapIndexed { idx, ep ->
                                mapOf("url" to ep.filePath, "title" to "${series.title} - ${ep.title}")
                            }
                            val mapper = jacksonObjectMapper()
                            val episodesJson = mapper.writeValueAsString(episodesList)
                            val currentIndex = limitedEpisodes.indexOf(targetEpisode)
                            hasAutoPlayed = true
                            navController.navigate(
                                Screen.Player.pass(
                                    url = targetEpisode.filePath,
                                    title = "${series.title} - ${targetEpisode.title}",
                                    episodesJson = episodesJson,
                                    startIndex = currentIndex,
                                    isLive = false,
                                    slug = slug,
                                    poster = series.posterUrl,
                                    type = "series",
                                    season = season,
                                    episode = episode,
                                    resumePosition = resumePos
                                )
                            )
                        }
                    } else {
                        android.util.Log.d("SeriesDetail", "Skipping auto-play - hasAutoPlayed: $hasAutoPlayed, season: $season, episode: $episode")
                    }
                }

                val toggleFavorite: () -> Unit = {
                    if (!isTogglingFavorite) {
                        isTogglingFavorite = true
                        val favoriteItem = FavoriteItem(
                            contentId = slug,
                            slug = slug,
                            title = series.title,
                            posterUrl = series.posterUrl,
                            type = "series"
                        )
                        android.util.Log.d("SeriesDetail", "Toggling favorite - slug: $slug, isFavorite: $isFavorite")
                        scope.launch {
                            val newFavoriteState = favoritesRepo.toggleItem(favoriteItem)
                            isFavorite = newFavoriteState
                            isTogglingFavorite = false
                        }
                    }
                }

                // Find the latest watched episode (highest episode number) with progress
                // This is used for the main PLAY/RESUME button
                val seriesProgressItems = continueWatchingItems
                    .filter { it.slug == slug && it.type == "series" }

                // Find the highest episode number (considering season and episode)
                val latestWatchedEpisode = seriesProgressItems
                    .maxByOrNull { compareValues(it.seasonNumber, it.episodeNumber) }

                // Determine play episode:
                // Priority 1: If a specific season/episode was passed (e.g., from continue watching click), use that
                // Priority 2: Use the latest watched episode from continue watching
                // Priority 3: Default to first episode of first season
                val (playSeason, playEpisodeNum) = when {
                    season > 0 && episode > 0 -> season to episode
                    latestWatchedEpisode != null -> latestWatchedEpisode.seasonNumber to latestWatchedEpisode.episodeNumber
                    else -> 1 to 1
                }

                val playSeasonObj = series.seasons.find { it.seasonNumber == playSeason }
                val playEpisodeObj = playSeasonObj?.episodes?.find { it.episodeNumber == playEpisodeNum }
                    ?: playSeasonObj?.episodes?.firstOrNull()
                    ?: series.seasons.firstOrNull()?.episodes?.firstOrNull()

                // Get resume position for the play episode
                val playResumePosition = if (playEpisodeObj != null) {
                    val epSeason = series.seasons.find { it.episodes.contains(playEpisodeObj) }?.seasonNumber ?: 1
                    episodeResumePositionMap["${epSeason}_${playEpisodeObj.episodeNumber}"] ?: 0L
                } else 0L

                val onPlayClick: () -> Unit = {
                    if (playEpisodeObj != null) {
                        val resumePos = episodeResumePositionMap["${playSeason}_${playEpisodeObj.episodeNumber}"] ?: 0L
                        android.util.Log.d("SeriesDetail", "Play click - S${playSeason}E${playEpisodeObj.episodeNumber}, resumePosition: ${resumePos}ms")
                        val episodesList = limitedEpisodes.mapIndexed { idx, ep ->
                            mapOf("url" to ep.filePath, "title" to "${series.title} - ${ep.title}")
                        }
                        val mapper = jacksonObjectMapper()
                        val episodesJson = mapper.writeValueAsString(episodesList)
                        val currentIndex = limitedEpisodes.indexOf(playEpisodeObj)
                        navController.navigate(
                            Screen.Player.pass(
                                url = playEpisodeObj.filePath,
                                title = "${series.title} - ${playEpisodeObj.title}",
                                episodesJson = episodesJson,
                                startIndex = currentIndex,
                                isLive = false,
                                slug = slug,
                                poster = series.posterUrl,
                                type = "series",
                                season = playSeason,
                                episode = playEpisodeObj.episodeNumber,
                                resumePosition = resumePos
                            )
                        )
                    }
                }

                SeriesDetailLayout(
                    series = series,
                    onBackClick = { navController.popBackStack() },
                    initialSeason = if (season > 0) season else (latestWatchedEpisode?.seasonNumber ?: 1),
                    continueWatchingEpisode = episode,
                    episodeProgressMap = episodeProgressMap,
                    isFavorite = isFavorite,
                    onToggleFavorite = toggleFavorite,
                    resumePosition = playResumePosition,
                    onPlayClick = onPlayClick,
                    onEpisodeClick = { clickedEpisode ->
                        val clickedSeason = series.seasons.find { it.episodes.contains(clickedEpisode) }
                        val seasonNum = clickedSeason?.seasonNumber ?: 1
                        val episodeNum = clickedEpisode.episodeNumber
                        // Get resume position from continue watching, default to 0
                        val resumePos = episodeResumePositionMap["${seasonNum}_$episodeNum"] ?: 0L
                        android.util.Log.d("SeriesDetail", "Episode click - S${seasonNum}E${episodeNum}, resumePosition: ${resumePos}ms")
                        val episodesList = limitedEpisodes.mapIndexed { idx, ep ->
                            mapOf("url" to ep.filePath, "title" to "${series.title} - ${ep.title}")
                        }
                        val mapper = jacksonObjectMapper()
                        val episodesJson = mapper.writeValueAsString(episodesList)
                        val currentIndex = limitedEpisodes.indexOf(clickedEpisode)
                        navController.navigate(
                            Screen.Player.pass(
                                url = clickedEpisode.filePath,
                                title = "${series.title} - ${clickedEpisode.title}",
                                episodesJson = episodesJson,
                                startIndex = currentIndex,
                                isLive = false,
                                slug = slug,
                                poster = series.posterUrl,
                                type = "series",
                                season = seasonNum,
                                episode = episodeNum,
                                resumePosition = resumePos
                            )
                        )
                    }
                )
            }
            is DetailUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = Color(0xFFCC4444), fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadSeries(slug) },
                        colors = ButtonDefaults.buttonColors(containerColor = FocusAccent)
                    ) { Text("Retry", color = Color.Black) }
                }
            }
            else -> {}
        }
    }
}

// ======================== TV-Friendly Movie Detail Layout ========================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieDetailLayout(
    title: String,
    posterUrl: String,
    backdropUrl: String,
    year: String,
    rating: String,
    duration: Int?,
    director: String,
    genres: List<String>,
    plot: String,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    resumePosition: Long = 0L,
    onPlayClick: (() -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val backdropHeight = if (isLandscape) 420.dp else 320.dp
    val posterWidth = if (isLandscape) 140.dp else 110.dp
    val posterHeight = if (isLandscape) 210.dp else 165.dp

    val playFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    var favoriteFocused by remember { mutableStateOf(false) }
    var backFocused by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ---- Hero: Backdrop + Poster + Info ----
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backdropHeight)
            ) {
                // Backdrop image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl.ifBlank { posterUrl })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.95f)
                                ),
                                startY = 0.3f
                            )
                        )
                )

                // Back button (top-left, over backdrop)
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .focusRequester(backFocusRequester)
                        .onFocusChanged { backFocused = it.isFocused }
                        .then(
                            if (backFocused) Modifier.border(2.dp, FocusAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Bottom info row: Poster + Metadata
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Poster
                    Card(
                        modifier = Modifier
                            .width(posterWidth)
                            .height(posterHeight),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.width(20.dp))

                    // Title + metadata
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = if (isLandscape) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 30.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        // Info row: Year · Rating · Duration
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (year.isNotBlank()) {
                                DetailMetaChip(text = year)
                            }
                            if (rating.isNotBlank()) {
                                DetailMetaChip(text = "⭐ $rating")
                            }
                            if (duration != null && duration > 0) {
                                val hrs = duration / 60
                                val mins = duration % 60
                                DetailMetaChip(
                                    text = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                                )
                            }
                        }

                        // Genres
                        if (genres.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                genres.take(4).forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = FocusAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            genre,
                                            fontSize = 11.sp,
                                            color = FocusAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Director
                        if (director.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Director: $director",
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ---- Play and Favorite Buttons ----
        if (onPlayClick != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto-focus play button once it's composed
                    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

                    // Play Button
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { playFocused = it.isFocused },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playFocused) FocusAccent else Color(0xFFE8C200),
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (playFocused) 8.dp else 2.dp
                        )
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (resumePosition > 0) "RESUME" else "PLAY NOW",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Favorite Button
                    if (onToggleFavorite != null) {
                        val favoriteIcon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                        val favoriteTint = if (isFavorite) Color(0xFFFF4081) else if (favoriteFocused) Color.White else Color(0xFFAAAAAA)
                        val favoriteBg = if (favoriteFocused) FocusAccent else Color(0xFF333333)

                        Button(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .width(80.dp)
                                .height(56.dp)
                                .focusRequester(favoriteFocusRequester)
                                .onFocusChanged { favoriteFocused = it.isFocused },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = favoriteBg,
                                contentColor = favoriteTint
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (favoriteFocused) 8.dp else 2.dp
                            )
                        ) {
                            Icon(
                                favoriteIcon,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Synopsis ----
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Synopsis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        plot.ifBlank { "No synopsis available." },
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

// ======================== TV-Friendly Series Detail Layout ========================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailLayout(
    series: Series,
    onBackClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    initialSeason: Int = 1,
    continueWatchingEpisode: Int = 0,
    episodeProgressMap: Map<String, Int> = emptyMap(),
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    resumePosition: Long = 0L,
    onPlayClick: (() -> Unit)? = null
) {
    var selectedSeason by remember { mutableStateOf(initialSeason) }
    val currentSeason = series.seasons.find { it.seasonNumber == selectedSeason }
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val backdropHeight = if (isLandscape) 380.dp else 300.dp
    val posterWidth = if (isLandscape) 130.dp else 100.dp
    val posterHeight = if (isLandscape) 195.dp else 150.dp

    val backFocusRequester = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    // Season focus requesters map
    val seasonFocusRequesters = remember(series.seasons) {
        series.seasons.associate { it.seasonNumber to FocusRequester() }
    }

    // Play and Favorite focus requesters
    val playFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    var favoriteFocused by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ---- Hero: Backdrop + Poster + Info ----
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(backdropHeight)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(series.backdropUrl.ifBlank { series.posterUrl })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.95f)
                                ),
                                startY = 0.3f
                            )
                        )
                )

                // Back button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .focusRequester(backFocusRequester)
                        .onFocusChanged { backFocused = it.isFocused }
                        .then(
                            if (backFocused) Modifier.border(2.dp, FocusAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Bottom info row
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Card(
                        modifier = Modifier
                            .width(posterWidth)
                            .height(posterHeight),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(series.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = series.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = series.title,
                            fontSize = if (isLandscape) 26.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 30.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (series.year != null) {
                                DetailMetaChip(text = series.year.toString())
                            }
                            if (series.rating != null) {
                                DetailMetaChip(text = "⭐ ${series.rating}")
                            }
                            val totalEp = series.seasons.sumOf { it.episodes.size }
                            DetailMetaChip(text = "$totalEp Episodes")
                        }

                        if (series.genres.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                series.genres.take(4).forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = FocusAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            genre,
                                            fontSize = 11.sp,
                                            color = FocusAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Season Tabs ----
        if (series.seasons.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    series.seasons.forEachIndexed { index, season ->
                        var seasonFocused by remember { mutableStateOf(false) }
                        val fr = seasonFocusRequesters[season.seasonNumber]

                        // Auto-focus the watching season tab once composed
                        if (season.seasonNumber == initialSeason) {
                            LaunchedEffect(Unit) { fr?.requestFocus() }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                selectedSeason == season.seasonNumber -> FocusAccent
                                seasonFocused -> DarkSurfaceVariant
                                else -> DarkSurface
                            },
                            modifier = Modifier
                                .then(
                                    if (fr != null) Modifier.focusRequester(fr) else Modifier
                                )
                                .onFocusChanged { seasonFocused = it.isFocused }
                                .then(
                                    if (seasonFocused && selectedSeason != season.seasonNumber)
                                        Modifier.border(2.dp, FocusAccent, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedSeason = season.seasonNumber }
                        ) {
                            Text(
                                "S${season.seasonNumber}",
                                fontWeight = if (selectedSeason == season.seasonNumber) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp,
                                color = if (selectedSeason == season.seasonNumber) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Play and Favorite Buttons ----
        if (onPlayClick != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto-focus play button once it's composed
                    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

                    // Play Button
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(playFocusRequester)
                            .onFocusChanged { playFocused = it.isFocused },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playFocused) FocusAccent else Color(0xFFE8C200),
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (playFocused) 8.dp else 2.dp
                        )
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (resumePosition > 0) "RESUME" else "PLAY NOW",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Favorite Button
                    if (onToggleFavorite != null) {
                        val favoriteIcon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                        val favoriteTint = if (isFavorite) Color(0xFFFF4081) else if (favoriteFocused) Color.White else Color(0xFFAAAAAA)
                        val favoriteBg = if (favoriteFocused) FocusAccent else Color(0xFF333333)

                        Button(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .width(80.dp)
                                .height(56.dp)
                                .focusRequester(favoriteFocusRequester)
                                .onFocusChanged { favoriteFocused = it.isFocused },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = favoriteBg,
                                contentColor = favoriteTint
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = if (favoriteFocused) 8.dp else 2.dp
                            )
                        ) {
                            Icon(
                                favoriteIcon,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Synopsis ----
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Synopsis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        series.plot.ifBlank { "No synopsis available." },
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }

        // ---- Episode List ----
        if (currentSeason != null) {
            item {
                Text(
                    "Season ${currentSeason.seasonNumber} · ${currentSeason.episodes.size} Episodes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(currentSeason.episodes, key = { _, ep -> "ep_${currentSeason.seasonNumber}_${ep.episodeNumber}" }) { index, episode ->
                val episodeKey = "${currentSeason.seasonNumber}_${episode.episodeNumber}"
                EpisodeCard(
                    episode = episode,
                    onClick = { onEpisodeClick(episode) },
                    requestFocus = index == 0,
                    isContinueWatching = episode.episodeNumber == continueWatchingEpisode && currentSeason.seasonNumber == initialSeason,
                    watchProgress = episodeProgressMap[episodeKey]
                )
            }
        }
    }
}

// ======================== TV-Friendly Episode Card ========================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    requestFocus: Boolean = false,
    isContinueWatching: Boolean = false,
    watchProgress: Int? = null  // 0-100, null = not started
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier
                        .border(3.dp, FocusAccent, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) DarkSurfaceVariant else DarkSurface
        ),
        elevation = CardDefaults.cardElevation(if (isFocused) 12.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode thumbnail
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(6.dp)
            ) {
                Box {
                    if (episode.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(episode.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "EP${episode.episodeNumber}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF555555)
                            )
                        }
                    }

                    // Play overlay on focus
                    if (isFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Episode info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Episode number badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = FocusAccent.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "E${episode.episodeNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = FocusAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = episode.title,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = if (isFocused) Color.White else Color(0xFFCCCCCC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                val runtime = episode.runtime
                if (runtime != null && runtime > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$runtime min",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }

            // Play arrow on the right
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 4.dp),
                tint = if (isFocused) FocusAccent else Color(0xFF555555)
            )
        }
    }

    // Watch progress bar at bottom
    if (watchProgress != null && watchProgress > 0) {
        val isWatched = watchProgress >= 95
        val barWidth = if (isWatched) 1.0f else (watchProgress / 100f).coerceIn(0.1f, 1.0f)
        val percentageText = if (!isWatched) "$watchProgress%" else null

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barWidth)
                        .fillMaxHeight()
                        .background(Color(0xFFE8C200), RoundedCornerShape(2.dp))
                )
            }

            // Percentage text (only for in-progress episodes)
            if (percentageText != null) {
                Text(
                    percentageText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
    } else if (isContinueWatching) {
        // Fallback for continue watching without progress data (full gold bar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.7f)
                .padding(bottom = 6.dp)
                .height(3.dp)
                .background(Color(0xFFE8C200), RoundedCornerShape(2.dp))
        )
    }
    } // close Box wrapper

    if (requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

// ======================== Reusable Components ========================

@Composable
private fun DetailMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = Color(0xFFDDDDDD),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
