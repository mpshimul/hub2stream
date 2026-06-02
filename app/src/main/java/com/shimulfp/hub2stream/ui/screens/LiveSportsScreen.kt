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
import com.shimulfp.hub2stream.extractor.models.SportsEvent
import com.shimulfp.hub2stream.ui.navigation.Screen
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.utils.isTv
import com.shimulfp.hub2stream.viewmodels.SportsViewModel
import kotlinx.coroutines.delay
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LiveSportsScreen(
    navController: NavController,
    viewModel: SportsViewModel = viewModel()
) {
    val events by viewModel.events.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns = if (isLandscape) 4 else 2
    val firstItemFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Sports") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && events.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error")
                        Button(onClick = { viewModel.loadEvents() }) { Text("Retry") }
                    }
                }
            }
            events.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No live events at the moment.") }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(events) { event ->
                        SportsEventCard(
                            event = event,
                            onClick = { playSportsEventWithPlaylist(navController, events, event) },
                            requestFocus = events.indexOf(event) == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SportsEventCard(
    event: SportsEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    requestFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .then(
                if (isFocused) Modifier
                    .scale(1.05f)
                    .zIndex(1f)
                    .border(3.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isFocused) 20.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(event.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = event.name,
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            // Gradient overlay at bottom for text area only
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)
            ) {
                Text(
                    event.name,
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
                if (event.tournament.isNotBlank()) {
                    Text(
                        event.tournament,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(56.dp))
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SportsEventLargeCard(
    event: SportsEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    requestFocus: Boolean = false
) {
    val context = LocalContext.current
    val cardHeight = if (context.isTv()) 450.dp else 300.dp   // adjust as needed

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = modifier
            .height(cardHeight)   // <-- dynamic height
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .then(
                if (isFocused) Modifier
                    .scale(1.02f)
                    .zIndex(1f)
                    .border(4.dp, FocusAccent, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isFocused) 16.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(event.logo)
                    .crossfade(true)
                    .build(),
                contentDescription = event.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient overlay at bottom for text area only
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(cardHeight * 0.6f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = event.name,
                    color = Color.White,
                    fontSize = 16.sp,
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
                if (event.tournament.isNotBlank()) {
                    Text(
                        text = event.tournament,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
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
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(56.dp))
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

private fun playSportsEventWithPlaylist(
    navController: NavController,
    allEvents: List<SportsEvent>,
    selected: SportsEvent
) {
    val playlist = allEvents.map { mapOf("url" to it.streamUrl, "title" to it.name, "id" to it.id) }
    val mapper = jacksonObjectMapper()
    val channelsJson = mapper.writeValueAsString(playlist)
    val encoded = URLEncoder.encode(channelsJson, "UTF-8")
    navController.navigate(
        Screen.LivePlayer.pass(
            url = selected.streamUrl,
            title = selected.name,
            channelsJson = encoded,
            startIndex = allEvents.indexOf(selected)
        )
    )
}