package com.shimulfp.hub2stream.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shimulfp.hub2stream.data.LiveTVRepository
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

private const val TAG = "LivePlayerScreen"

data class LiveChannelItem(
    val url: String,
    val title: String,
    val id: String = "",
    val logo: String = ""
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    navController: NavController,
    url: String,
    title: String,
    channelsJson: String,
    startIndex: Int
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val liveTvRepo = remember { LiveTVRepository() }

    val headers = remember {
        mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "http://dhakamovie.com/",
            "Origin" to "http://dhakamovie.com"
        )
    }

    fun resolveLogoUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else if (rawUrl.startsWith("//")) {
            "https:$rawUrl"
        } else if (rawUrl.startsWith("/")) {
            "http://redforce.live$rawUrl"
        } else {
            rawUrl
        }
    }

    val channels = remember(channelsJson) {
        if (channelsJson.isNotBlank()) {
            try {
                val decoded = URLDecoder.decode(channelsJson, "UTF-8")
                val mapper = jacksonObjectMapper()
                val list: List<Map<String, String>> = mapper.readValue(decoded)
                list.map {
                    val rawLogo = it["logo"] ?: it["tvg-logo"] ?: it["tvgLogo"] ?: it["logo_url"] ?: it["logoUrl"] ?: it["Logo"] ?: it["image"] ?: ""
                    val resolvedLogo = resolveLogoUrl(rawLogo)
                    Log.d(TAG, "Channel '${it["title"]}' logo: $resolvedLogo")
                    LiveChannelItem(
                        url = it["url"] ?: "",
                        title = it["title"] ?: "",
                        id = it["id"] ?: "",
                        logo = resolvedLogo
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing channels", e)
                emptyList()
            }
        } else {
            listOf(LiveChannelItem(url, title))
        }
    }

    // Check if device is a TV
    val isTv = remember {
        val uiMode = context.resources.configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    // Check if PIP is supported (requires Android 8.0+ and non-TV device)
    val supportsPip = remember {
        !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    var currentIndex by remember { mutableStateOf(startIndex) }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var infoText by remember { mutableStateOf("") }
    var exoPlayerState by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewState by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    val failedChannels = remember { mutableSetOf<Int>() }
    val ioErrorRetries = remember { mutableMapOf<Int, Int>() }
    val refreshRetries = remember { mutableMapOf<Int, Int>() }
    var currentScaleMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isInPipMode by remember { mutableStateOf(false) }

    var totalDragX by remember { mutableStateOf(0f) }
    val swipeThreshold = 150f

    var focusRow by remember { mutableIntStateOf(1) }
    var topFocusIdx by remember { mutableIntStateOf(0) }
    var bottomFocusIdx by remember { mutableIntStateOf(startIndex) }

    val topBarItems = remember(supportsPip) {
        buildList {
            add("back")
            if (supportsPip) {
                add("pip")
            }
            add("scale")
        }
    }
    val scaleModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Fixed Width",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fixed Height"
    )

    fun resetAutoHideTimer() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5000)
            isControlsVisible = false
        }
    }

    fun showControlsPermanently() {
        hideJob?.cancel()
        isControlsVisible = true
    }

    fun showControlsTemporarily() {
        showControlsPermanently()
        resetAutoHideTimer()
    }

    fun showInfoTemporarily(text: String) {
        infoText = text
        showInfo = true
        scope.launch {
            delay(2000)
            showInfo = false
        }
    }

    fun cycleScaleMode() {
        val currentIdx = scaleModes.indexOfFirst { it.first == currentScaleMode }
        val nextIdx = (currentIdx + 1) % scaleModes.size
        currentScaleMode = scaleModes[nextIdx].first
        playerViewState?.resizeMode = currentScaleMode
        showInfoTemporarily("Scale: ${scaleModes[nextIdx].second}")
        showControlsTemporarily()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureInPictureMode() {
        if (!supportsPip) {
            Log.w(TAG, "PIP mode not supported on this device (TV or Android < 8.0)")
            if (isTv) {
                showInfoTemporarily("PIP not supported on TV")
            } else {
                showInfoTemporarily("PIP requires Android 8.0+")
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val act = activity ?: return

                // Remove fullscreen flags before entering PIP
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                // Hide controls before PIP
                isControlsVisible = false

                // Configure PIP params
                val aspectRatio = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Rational(16, 9)
                } else {
                    Rational(9, 16)
                }

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()

                // Set params first
                act.setPictureInPictureParams(params)

                // Enter PIP mode
                val entered = act.enterPictureInPictureMode(params)

                if (entered) {
                    Log.d(TAG, "Successfully entered PIP mode")
                    isInPipMode = true
                } else {
                    Log.w(TAG, "Failed to enter PIP mode - enterPictureInPictureMode returned false")
                    showInfoTemporarily("PIP not available")
                    // Restore fullscreen if PIP failed
                    act.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "PIP not supported by activity: ${e.message}")
                showInfoTemporarily("PIP not available")
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter PIP mode: ${e.message}", e)
                showInfoTemporarily("PIP error")
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    // Silent channel change (no UI controls)
    fun changeChannelSilent(index: Int) {
        if (index in channels.indices) {
            failedChannels.clear()
            ioErrorRetries.clear()
            refreshRetries.clear()
            currentIndex = index
            bottomFocusIdx = index
            currentTitle = channels[index].title
            exoPlayerState?.seekToDefaultPosition(index)
            exoPlayerState?.prepare()
            exoPlayerState?.playWhenReady = true
            exoPlayerState?.play()
            showInfoTemporarily("Channel: ${channels[index].title}")
        }
    }

    fun selectChannelFromGuide(index: Int) {
        if (index in channels.indices) {
            changeChannelSilent(index)
            // Immediately hide controls if they were visible
            if (isControlsVisible) {
                isControlsVisible = false
                hideJob?.cancel()  // also cancel any pending auto-hide
            }
        }
    }

    fun switchToNextChannel() {
        if (channels.isNotEmpty()) {
            val nextIndex = (currentIndex + 1) % channels.size
            changeChannelSilent(nextIndex)
        }
    }

    fun switchToPreviousChannel() {
        if (channels.isNotEmpty()) {
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else channels.size - 1
            changeChannelSilent(prevIndex)
        }
    }

    fun skipToNextAvailableChannel(failedName: String) {
        var nextIndex = (currentIndex + 1) % channels.size
        var attempts = 0
        while (nextIndex in failedChannels && attempts < channels.size) {
            nextIndex = (nextIndex + 1) % channels.size
            attempts++
        }
        if (attempts < channels.size) {
            changeChannelSilent(nextIndex)
            showInfoTemporarily("Switching channel...")
        } else {
            showInfoTemporarily("All channels unavailable")
            resetAutoHideTimer()
        }
    }

    fun activateFocusedControl() {
        when (focusRow) {
            0 -> when (topBarItems.getOrNull(topFocusIdx)) {
                "back" -> navController.popBackStack()
                "pip" -> enterPictureInPictureMode()
                "scale" -> cycleScaleMode()
            }
            1 -> {
                if (bottomFocusIdx in channels.indices) {
                    selectChannelFromGuide(bottomFocusIdx)
                }
            }
        }
    }

    val keyChannel = remember { kotlinx.coroutines.channels.Channel<Int>(kotlinx.coroutines.channels.Channel.BUFFERED) }

    fun handleKeyCode(code: Int): Boolean {
        if (!isControlsVisible) {
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    focusRow = 1
                    bottomFocusIdx = currentIndex
                    showControlsTemporarily()
                    return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    switchToPreviousChannel()
                    return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    switchToNextChannel()
                    return true
                }
                AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
                    switchToNextChannel()
                    return true
                }
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN, AndroidKeyEvent.KEYCODE_PAGE_UP -> {
                    switchToPreviousChannel()
                    return true
                }
                AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                    navController.popBackStack()
                    return true
                }
            }
            return false
        }

        showControlsTemporarily()
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                if (focusRow == 1) {
                    focusRow = 0
                    topFocusIdx = 0
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusRow == 0) {
                    focusRow = 1
                    bottomFocusIdx = currentIndex
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                when (focusRow) {
                    0 -> topFocusIdx = if (topFocusIdx > 0) topFocusIdx - 1 else topBarItems.size - 1
                    1 -> bottomFocusIdx = if (bottomFocusIdx > 0) bottomFocusIdx - 1 else channels.size - 1
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                when (focusRow) {
                    0 -> topFocusIdx = (topFocusIdx + 1) % topBarItems.size
                    1 -> bottomFocusIdx = (bottomFocusIdx + 1) % channels.size
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                activateFocusedControl()
                return true
            }
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                isControlsVisible = false
                return true
            }
        }
        return false
    }

    LaunchedEffect(Unit) {
        for (code in keyChannel) {
            handleKeyCode(code)
        }
    }

    val httpDataSourceFactory = remember(headers) {
        DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: "Mozilla/5.0")
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
    }

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,   // min buffer (30 seconds)
                60000,   // max buffer (60 seconds)
                5000,    // buffer for playback (5 seconds)
                5000     // buffer for playback after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(50 * 1024 * 1024)
            .build()
    }

    val mediaItems = remember(channels) {
        channels.map { channel ->
            MediaItem.Builder()
                .setUri(Uri.parse(channel.url))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3000)
                        .setMaxOffsetMs(10000)
                        .setMinOffsetMs(1000)
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .build()
                )
                .build()
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
            .build()
            .apply {
                if (mediaItems.isNotEmpty()) {
                    setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                }
                prepare()
                playWhenReady = true
                play()
            }.also { exoPlayerState = it }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250)
            isPlaying = exoPlayer.isPlaying
        }
    }

    // Lifecycle-aware player control - pause when activity goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (exoPlayer.isPlaying) {
                        Log.d(TAG, "Activity paused/stopped - pausing playback")
                        playerViewState?.keepScreenOn = false
                        // Try to enter PIP mode if supported and playing
                        if (supportsPip && !isInPipMode) {
                            activity?.setPictureInPictureParams(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(
                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                            Rational(16, 9)
                                        } else {
                                            Rational(9, 16)
                                        }
                                    )
                                    .setAutoEnterEnabled(true)
                                    .build()
                            )
                        } else {
                            // Pause playback if PIP is not supported
                            exoPlayer.pause()
                            exoPlayer.playWhenReady = false
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "Activity resumed/started")
                    playerViewState?.keepScreenOn = true
                    isInPipMode = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Stop playback when navigating away from screen
    BackHandler(enabled = true) {
        exoPlayer.pause()
        exoPlayer.playWhenReady = false
        navController.popBackStack()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (channels.isNotEmpty() && newIndex != currentIndex) {
                    currentIndex = newIndex
                    bottomFocusIdx = newIndex
                    currentTitle = channels.getOrNull(newIndex)?.title ?: ""
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    failedChannels.clear()
                    ioErrorRetries.clear()
                    refreshRetries.clear()
                    resetAutoHideTimer()
                } else if (state == Player.STATE_ENDED) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.play()
                }
            }

            @RequiresApi(Build.VERSION_CODES.N)
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val errorCode = error.errorCode
                val channelName = channels.getOrNull(currentIndex)?.title ?: "Unknown"

                if (errorCode == 1002 || error.cause is androidx.media3.exoplayer.audio.AudioSink.UnexpectedDiscontinuityException) {
                    Log.w(TAG, "Audio discontinuity, seeking to current position")
                    scope.launch {
                        delay(300)
                        exoPlayer.seekTo(currentIndex, exoPlayer.currentPosition)
                        exoPlayer.play()
                    }
                    return
                }

                if (errorCode == 1002) {
                    scope.launch {
                        delay(500)
                        try {
                            val channel = channels.getOrNull(currentIndex)
                            if (channel != null) {
                                val newMediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(channel.url))
                                    .setLiveConfiguration(
                                        MediaItem.LiveConfiguration.Builder()
                                            .setTargetOffsetMs(3000).setMaxOffsetMs(10000).setMinOffsetMs(1000)
                                            .setMaxPlaybackSpeed(1.02f).setMinPlaybackSpeed(0.98f).build()
                                    ).build()
                                exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        } catch (e: Exception) {
                            failedChannels.add(currentIndex)
                            skipToNextAvailableChannel(channelName)
                        }
                    }
                    return
                }

                val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                if (responseCode == 403) {
                    val channel = channels.getOrNull(currentIndex)
                    val channelId = channel?.id ?: ""
                    if (channelId.isNotBlank() && refreshRetries.getOrDefault(currentIndex, 0) < 2) {
                        refreshRetries[currentIndex] = refreshRetries.getOrDefault(currentIndex, 0) + 1
                        showInfoTemporarily("Refreshing stream...")
                        scope.launch {
                            val freshUrl = withTimeoutOrNull(10000L) {
                                liveTvRepo.refreshStreamUrl(channelId, channelName)
                            }
                            if (!freshUrl.isNullOrBlank()) {
                                val newMediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(freshUrl))
                                    .setLiveConfiguration(
                                        MediaItem.LiveConfiguration.Builder()
                                            .setTargetOffsetMs(3000).setMaxOffsetMs(10000).setMinOffsetMs(1000)
                                            .setMaxPlaybackSpeed(1.02f).setMinPlaybackSpeed(0.98f).build()
                                    ).build()
                                exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            } else {
                                failedChannels.add(currentIndex)
                                skipToNextAvailableChannel(channelName)
                            }
                        }
                        return
                    }
                    failedChannels.add(currentIndex)
                    skipToNextAvailableChannel(channelName)
                    return
                }

                if (responseCode in 400..499) {
                    failedChannels.add(currentIndex)
                    skipToNextAvailableChannel(channelName)
                    return
                }

                val isRetryableIOError = errorCode == 2000 || errorCode == 2004
                if (isRetryableIOError) {
                    val retryCount = ioErrorRetries.getOrDefault(currentIndex, 0)
                    if (retryCount < 3) {
                        ioErrorRetries[currentIndex] = retryCount + 1
                        scope.launch {
                            delay(1000)
                            exoPlayer.seekToDefaultPosition(currentIndex)
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }
                        return
                    }
                }

                failedChannels.add(currentIndex)
                skipToNextAvailableChannel(channelName)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    @Composable
    fun LivePlayerButton(
        isFocused: Boolean,
        icon: @Composable () -> Unit,
        label: String? = null,
        onClick: () -> Unit = {}
    ) {
        Surface(
            color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            border = if (isFocused) BorderStroke(2.5.dp, FocusAccent) else null,
            modifier = Modifier.size(52.dp).clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    icon()
                    if (label != null) {
                        Text(
                            label,
                            color = if (isFocused) FocusAccent else Color.White.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose {}
        val window = act.window
        val originalCallback = window.callback
        val handledKeys = setOf(
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE,
            AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            AndroidKeyEvent.KEYCODE_PAGE_UP, AndroidKeyEvent.KEYCODE_PAGE_DOWN
        )
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode in handledKeys && event.action == AndroidKeyEvent.ACTION_DOWN) {
                    keyChannel.trySend(event.keyCode)
                    return true
                }
                return originalCallback.dispatchKeyEvent(event)
            }
        }
        onDispose {
            window.callback = originalCallback
            keyChannel.close()
        }
    }

    LaunchedEffect(Unit) { showControlsTemporarily() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -swipeThreshold) {
                            switchToNextChannel()
                        } else if (totalDragX > swipeThreshold) {
                            switchToPreviousChannel()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    resizeMode = currentScaleMode
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnClickListener { showControlsTemporarily() }
                }.also { playerViewState = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().zIndex(2f),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LivePlayerButton(
                            isFocused = focusRow == 0 && topFocusIdx == 0,
                            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(28.dp)) },
                            onClick = { navController.popBackStack() }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(3f)
                        ) {
                            if (channels.size > 1) {
                                Text(
                                    "${currentIndex + 1}/${channels.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                currentTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // PIP button (if supported)
                        if (supportsPip) {
                            val pipIndex = topBarItems.indexOf("pip")
                            LivePlayerButton(
                                isFocused = focusRow == 0 && topFocusIdx == pipIndex,
                                icon = { Icon(Icons.Filled.PictureInPicture, "PIP", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                label = "PIP",
                                onClick = { enterPictureInPictureMode() }
                            )
                        }

                        LivePlayerButton(
                            isFocused = focusRow == 0 && topFocusIdx == (if (supportsPip) 2 else 1),
                            icon = { Icon(Icons.Filled.ZoomOutMap, "Scale", tint = Color.White, modifier = Modifier.size(26.dp)) },
                            label = "Scale",
                            onClick = { cycleScaleMode() }
                        )
                    }
                }

                // Bottom channel guide
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        /**
                        Text(
                            text = "CHANNEL GUIDE",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                        **/

                        val listState = rememberLazyListState()

                        LaunchedEffect(bottomFocusIdx) {
                            if (isControlsVisible && channels.isNotEmpty() && bottomFocusIdx in channels.indices) {
                                listState.animateScrollToItem(bottomFocusIdx)
                            }
                        }

                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                hideJob?.cancel()
                                isControlsVisible = true
                            } else {
                                resetAutoHideTimer()
                            }
                        }

                        LazyRow(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(channels) { idx, channel ->
                                val isItemFocused = (focusRow == 1 && bottomFocusIdx == idx)
                                val isCurrentlyPlaying = (currentIndex == idx)
                                var imageLoadError by remember(channel.logo) { mutableStateOf(false) }

                                Surface(
                                    color = when {
                                        isItemFocused -> Color.White.copy(alpha = 0.25f)
                                        isCurrentlyPlaying -> FocusAccent.copy(alpha = 0.15f)
                                        else -> Color.White.copy(alpha = 0.05f)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        width = if (isItemFocused) 2.5.dp else if (isCurrentlyPlaying) 1.5.dp else 1.dp,
                                        color = when {
                                            isItemFocused -> FocusAccent
                                            isCurrentlyPlaying -> FocusAccent.copy(alpha = 0.6f)
                                            else -> Color.White.copy(alpha = 0.15f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(80.dp)
                                        .clickable { selectChannelFromGuide(idx) }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (channel.logo.isNotBlank() && !imageLoadError) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(channel.logo)
                                                    .addHeader("User-Agent", headers["User-Agent"] ?: "")
                                                    .addHeader("Referer", headers["Referer"] ?: "")
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = channel.title,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                                onError = {
                                                    imageLoadError = true
                                                    Log.e(TAG, "Failed to load logo for ${channel.title}: ${channel.logo}")
                                                },
                                                onSuccess = { imageLoadError = false }
                                            )
                                        } else {
                                            Surface(
                                                color = FocusAccent.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = channel.title.take(1).uppercase(),
                                                        color = FocusAccent,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold
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
            }
        }

        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 10.dp)
        ) {
            Surface(
                color = Color.Red.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(16.dp)) {
                Text(infoText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
        }
    }
}