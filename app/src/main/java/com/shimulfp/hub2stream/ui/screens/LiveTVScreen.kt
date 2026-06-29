package com.shimulfp.hub2stream.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.shimulfp.hub2stream.extractor.models.LiveChannel
import com.shimulfp.hub2stream.extractor.models.LiveTVSource
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.utils.isTv
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
    val context = LocalContext.current
    val isTv = remember { context.isTv() }

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
                    // Validation progress indicator
                    if (uiState.isValidating) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = FocusAccent
                            )
                            Text(uiState.validationProgress, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
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
            // Source tabs — horizontally scrollable for both phone and TV
            SourceTabRow(
                sources = uiState.sourceResults,
                selectedIndex = uiState.selectedSourceIndex,
                onSourceSelected = { viewModel.selectSource(it) },
                isTv = isTv,
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
                            Text(currentSource?.error ?: "All channels failed validation", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                }
                else -> {
                    val firstItemFocusRequester = remember { FocusRequester() }
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
                                requestFocus = index == 0
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
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    val listState = rememberLazyListState()

    // Auto-scroll to the selected source tab when it changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in sources.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(sources, key = { index, _ -> index }) { index, sourceResult ->
            SourceChip(
                source = sourceResult.source,
                channelCount = sourceResult.channels.size,
                isLoading = sourceResult.isLoading,
                isSelected = index == selectedIndex,
                onClick = { onSourceSelected(index) },
                isTv = isTv,
                onFocusGained = { if (isTv) onSourceSelected(index) }
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
    isTv: Boolean,
    onFocusGained: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
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
                val wasFocused = isFocused == 1
                isFocused = if (state.isFocused) 1 else 0
                // Auto-select on TV when focus enters the chip (not when leaving)
                if (isFocused == 1 && !wasFocused) {
                    onFocusGained()
                }
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
    requestFocus: Boolean = false
) {
    var isFocused by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    androidx.compose.material3.Card(
        modifier = modifier
            .focusRequester(focusRequester)
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logo)
                    .crossfade(true)
                    .build(),
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
                focusRequester.requestFocus()
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
    val playlist = allChannels.map { ch ->
        val url = if (ch.id == selected.id && ch.sourceId == selected.sourceId) freshUrl else ch.streamUrl
        mapOf(
            "url" to url, "title" to ch.name, "id" to ch.id,
            "logo" to ch.logo, "sourceId" to ch.sourceId,
            "licenseType" to ch.licenseType, "licenseKey" to ch.licenseKey
        )
    }
    val mapper = jacksonObjectMapper()
    val channelsJson = mapper.writeValueAsString(playlist)
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