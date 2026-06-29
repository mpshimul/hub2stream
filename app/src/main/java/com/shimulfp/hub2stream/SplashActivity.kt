package com.shimulfp.hub2stream

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shimulfp.hub2stream.data.cache.CacheManager
import com.shimulfp.hub2stream.data.ContinueWatchingRepository
import com.shimulfp.hub2stream.data.FavoritesRepository
import com.shimulfp.hub2stream.data.LiveTVRepository
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.data.SportsRepository
import com.shimulfp.hub2stream.ui.theme.Hub2StreamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SplashActivity : ComponentActivity() {

    private val SPLASH_DURATION = 7000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Hub2StreamTheme {
                SplashScreenContent()
            }
        }

        preloadContent()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMainActivity()
        }, SPLASH_DURATION)
    }

    private fun preloadContent() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val cacheManager = CacheManager(application)

                launch {
                    try {
                        val (cached, stale) = cacheManager.getMovies()
                        if (stale || cached.isEmpty()) cacheManager.saveMovies(MovieRepository().getHomePageRows())
                    } catch (_: Exception) {}
                }
                launch {
                    try {
                        val (cached, stale) = cacheManager.getLiveTv()
                        if (stale || cached.isEmpty()) cacheManager.saveLiveTv(LiveTVRepository.getChannels())
                    } catch (_: Exception) {}
                }
                launch {
                    try {
                        val (cached, stale) = cacheManager.getSports()
                        if (stale || cached.isEmpty()) cacheManager.saveSports(SportsRepository().getLiveEvents())
                    } catch (_: Exception) {}
                }
                launch {
                    try { ContinueWatchingRepository(application).items.first() } catch (_: Exception) {}
                }
                launch {
                    try { FavoritesRepository(application).items.first() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun SplashScreenContent() {
    val bgColor = Color(0xFF000C12)
    val accent = Color(0xFF1A8FFF)
    val track = Color(0xFF1A2A3A)

    // Logo entrance
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.5f) }

    // Progress (fills over 6.5s)
    val progress = remember { Animatable(0f) }
    var percent by remember { mutableFloatStateOf(0f) }
    val displayPercent by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(200),
        label = "pct"
    )

    // Shimmer sweep
    val infinite = rememberInfiniteTransition(label = "inf")
    val shimmerX by infinite.animateFloat(-300f, 300f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), "sx")

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { logoScale.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { progress.animateTo(1f, tween(6500, easing = FastOutSlowInEasing)) }
        launch {
            val steps = 65
            for (i in 1..steps) { delay(100); percent = i / steps.toFloat() }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        // 1. Logo — Stays perfectly centered on the screen
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(150.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        )

        // 2. Loading indicators — Anchored to the bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter) // Anchors the column to the bottom
                .padding(bottom = 48.dp)       // Spacing from the bottom edge of the screen
                .padding(horizontal = 48.dp)
        ) {
            // "Loading..." text
            androidx.compose.material3.Text(
                text = "Loading...",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(track)
                    .clipToBounds()
            ) {
                // Filled portion
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(15.dp))
                        .background(accent)
                )
                // Shimmer
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxSize()
                        .offset { IntOffset(x = shimmerX.roundToInt(), y = 0) }
                        .alpha(0.45f)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Percentage
            androidx.compose.material3.Text(
                text = "${(displayPercent * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}