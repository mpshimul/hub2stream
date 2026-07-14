package com.shimulfp.hub2stream.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.utils.Json
import com.shimulfp.hub2stream.extractor.models.LiveTVSource
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.viewmodels.LiveTVViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveTVScreen(
    navController: NavController,
    viewModel: LiveTVViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 8 else 4
    val scope = rememberCoroutineScope()
    val gridFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live TV") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshCurrentSource() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Source tabs
            SourceTabRow(
                sources = uiState.sourceResults,
                selectedIndex = uiState.selectedSourceIndex,
                onSourceSelected = { viewModel.selectSource(it) },
                onDownPressed = {
                    try { gridFocusRequester.requestFocus() } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Content
            val currentChannels = uiState.currentChannels
            val currentSource = uiState.sourceResults.getOrNull(uiState.selectedSourceIndex)

            when {
                currentSource?.isLoading == true && currentChannels.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading ${currentSource.source.name}...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                currentChannels.isEmpty() && !uiState.isGlobalLoading -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No channels available", color = Color.Gray, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(currentSource?.error ?: "", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(currentChannels, key = { index, channel -> "${channel.sourceId}_${channel.id}_$index" }) { index, channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = {
                                    scope.launch {
                                        val freshUrl = viewModel.refreshStreamUrl(channel)
                                        val playUrl = freshUrl ?: channel.streamUrl
                                        playChannelWithPlaylist(navController, currentChannels, channel, playUrl)
                                    }
                                },
                                requestFocus = index == 0,
                                focusRequester = if (index == 0) gridFocusRequester else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SourceTabRow(
    sources: List<com.shimulfp.hub2stream.data.SourceChannels>,
    selectedIndex: Int,
    onSourceSelected: (Int) -> Unit,
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    val chipFocusRequesters = remember(sources.size) {
        List(sources.size) { FocusRequester() }
    }

    // Track which chip has focus so we can cycle left/right
    var focusedChipIndex by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onPreviewKeyEvent { event ->
                // Only handle ACTION_DOWN
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode

                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val next = if (focusedChipIndex < 0) selectedIndex else focusedChipIndex + 1
                        if (next in sources.indices) {
                            focusedChipIndex = next
                            chipFocusRequesters[next].requestFocus()
                            onSourceSelected(next)
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        val prev = if (focusedChipIndex < 0) selectedIndex else focusedChipIndex - 1
                        if (prev in sources.indices) {
                            focusedChipIndex = prev
                            chipFocusRequesters[prev].requestFocus()
                            onSourceSelected(prev)
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDownPressed()
                        false
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sources.forEachIndexed { index, sourceResult ->
            SourceChip(
                source = sourceResult.source,
                channelCount = sourceResult.channels.size,
                isLoading = sourceResult.isLoading,
                isSelected = index == selectedIndex,
                onClick = { onSourceSelected(index) },
                focusRequester = chipFocusRequesters[index],
                onFocusGained = { focusedChipIndex = index }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SourceChip(
    source: LiveTVSource,
    channelCount: Int,
    isLoading: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit = {}
) {
    var isFocused by remember { mutableIntStateOf(0) }

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else if (isFocused == 1) 2.dp else 1.dp,
        animationSpec = tween(200),
        label = "border"
    )
    val borderColor = when {
        isSelected -> FocusAccent
        isFocused == 1 -> Color.White.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.15f)
    }
    val bgColor = when {
        isSelected -> FocusAccent.copy(alpha = 0.15f)
        isFocused == 1 -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }

    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                val wasFocused = isFocused
                isFocused = if (state.isFocused) 1 else 0
                if (isFocused == 1 && wasFocused == 0) onFocusGained()
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        isLoading -> Color(0xFFFFA500)
                        channelCount > 0 -> Color(0xFF4CAF50)
                        else -> Color(0xFF666666)
                    },
                    shape = CircleShape
                )
        )

        // Name
        Text(
            text = source.name,
            color = if (isSelected) FocusAccent else Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )

        // Count
        if (!isLoading) {
            Text(
                text = "($channelCount)",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    requestFocus: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableIntStateOf(0) }
    val internalFocusRequester = remember { FocusRequester() }
    val activeRequester = focusRequester ?: internalFocusRequester

    androidx.compose.material3.Card(
        modifier = modifier
            .focusRequester(activeRequester)
            .onFocusChanged { focusState ->
                isFocused = if (focusState.isFocused) 1 else 0
            }
            .then(
                if (isFocused == 1) Modifier
                    .scale(1.05f)
                    .border(4.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(if (isFocused == 1) 16.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            if (isFocused == 1) {
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
                delay(50)
                activeRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
}

private fun playChannelWithPlaylist(
    navController: NavController,
    allChannels: List<LiveChannel>,
    selected: LiveChannel,
    freshUrl: String
) {
    val currentIndex = allChannels.indexOfFirst { it.id == selected.id && it.sourceId == selected.sourceId }
    // Logos already resolved by LiveTVRepository.resolveLogos() at load time
    val playlist = allChannels.map { ch ->
        val url = if (ch.id == selected.id && ch.sourceId == selected.sourceId) freshUrl else ch.streamUrl
        mapOf(
            "url" to url, "title" to ch.name, "id" to ch.id,
            "logo" to ch.logo, "sourceId" to ch.sourceId,
            "licenseType" to ch.licenseType, "licenseKey" to ch.licenseKey
        )
    }
    val channelsJson = Json.toJson(playlist)
    val encoded = URLEncoder.encode(channelsJson, "UTF-8")
    navController.navigate(
        Screen.LivePlayer.pass(
            url = freshUrl,
            title = selected.name,
            channelsJson = encoded,
            startIndex = currentIndex
        )
    )
}