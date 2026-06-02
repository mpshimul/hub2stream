package com.shimulfp.hub2stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.shimulfp.hub2stream.extractor.LiveTVExtractor
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.viewmodels.LiveTVViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LiveTVScreen(
    navController: NavController,
    viewModel: LiveTVViewModel = viewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val scope = rememberCoroutineScope()
    val extractor = remember { LiveTVExtractor() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 8 else 4
    val firstItemFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live TV") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && channels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error")
                        Button(onClick = { viewModel.loadChannels() }) { Text("Retry") }
                    }
                }
            }
            channels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No channels available.") }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = {
                                scope.launch {
                                    val freshUrl = extractor.refreshChannelStreamUrl(channel.id, channel.name)
                                    val playUrl = freshUrl ?: channel.streamUrl
                                    playChannelWithPlaylist(navController, channels, channel, playUrl)
                                }
                            },
                            requestFocus = channels.indexOf(channel) == 0
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    requestFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                println("ChannelCard focus: ${channel.name} - isFocused=${focusState.isFocused}")
            }
            .then(
                if (isFocused) Modifier
                    .scale(1.05f)
                    .border(4.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isFocused) 16.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // ensures a square aspect ratio (logo typically square)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit // Fit the image inside without cropping
            )

            /**
            // Gradient overlay at bottom for text area only
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            // Channel name overlay with shadow
            Text(
                text = channel.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        blurRadius = 6f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f)
                    )
                )
            )
            **/
            // Play overlay on focus
            if (isFocused) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
        }
    }

    if (requestFocus) {
        LaunchedEffect(Unit) {
            try {
                delay(50) // Small delay to ensure node is attached
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures during composition
            }
        }
    }
}

private fun playChannelWithPlaylist(
    navController: NavController,
    allChannels: List<LiveChannel>,
    selected: LiveChannel,
    freshUrl: String
) {
    println("🔥🔥🔥 LIVE TV: playChannelWithPlaylist CALLED 🔥🔥🔥")
    val currentIndex = allChannels.indexOfFirst { it.id == selected.id }
    // Use fresh URL for the selected channel, keep original URLs for others
    val playlist = allChannels.map { ch ->
        val url = if (ch.id == selected.id) freshUrl else ch.streamUrl
        mapOf("url" to url, "title" to ch.name, "id" to ch.id)
    }
    val mapper = jacksonObjectMapper()
    val channelsJson = mapper.writeValueAsString(playlist)
    val encoded = URLEncoder.encode(channelsJson, "UTF-8")
    println("🔥🔥🔥 NAVIGATING TO LivePlayer 🔥🔥🔥")
    navController.navigate(
        Screen.LivePlayer.pass(
            url = freshUrl,
            title = selected.name,
            channelsJson = encoded,
            startIndex = currentIndex
        )
    )
}