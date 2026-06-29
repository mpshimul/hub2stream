package com.shimulfp.hub2stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .width(120.dp)
            .height(225.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Poster image with progress bar overlay
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                elevation = CardDefaults.cardElevation(if (isFocused) 16.dp else 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            ContinueWatchingPlaceholder(
                                title = item.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        error = {
                            ContinueWatchingPlaceholder(
                                title = item.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )

                    // Progress bar overlay at the bottom
                    val isWatched = item.progressPercentage >= 95
                    val progressWidth = if (isWatched) 1.0f else (item.progressPercentage / 100f).coerceIn(0.1f, 1.0f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressWidth)
                                .fillMaxHeight()
                                .background(Color(0xFFE8C200))
                        )
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
                                "Resume",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            // Title below the poster (outside)
            Text(
                text = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Focus border overlay — outside the card, doesn't affect layout
        if (isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(3.dp, FocusAccent, RoundedCornerShape(8.dp))
            )
        }
    }

    if (requestFocus) {
        LaunchedEffect(Unit) {
            try {
                delay(50)
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures during composition
            }
        }
    }
}
