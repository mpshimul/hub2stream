package com.shimulfp.hub2stream.ui.screens

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.view.Window
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.shimulfp.hub2stream.ui.navigation.Screen
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.shimulfp.hub2stream.data.MovieRepository
import com.shimulfp.hub2stream.data.ContinueWatchingRepository
import com.shimulfp.hub2stream.extractor.models.QualityInfo
import com.shimulfp.hub2stream.extractor.models.SubtitleInfo
import com.shimulfp.hub2stream.extractor.models.DubInfo
import com.shimulfp.hub2stream.models.ContinueWatchingItem
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder

private const val TAG = "PlayerScreen"

data class EpisodeItem(val url: String, val title: String)
data class TrackInfo(val trackGroupIndex: Int, val trackIndex: Int, val label: String, val language: String? = null)

@RequiresApi(Build.VERSION_CODES.O)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    url: String,
    title: String,
    episodesJson: String,
    startIndex: Int,
    isLive: Boolean,
    slug: String = "",
    poster: String = "",
    type: String = "",
    season: Int = 0,
    episode: Int = 0,
    resumePosition: Long = 0L
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as Application
    val continueWatchingRepo = remember { ContinueWatchingRepository(application) }
    val movieRepo = remember { MovieRepository(application) }

    // Check if device is a TV
    val isTv = remember {
        val uiMode = context.resources.configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    // Check if PIP is supported (requires Android 8.0+ and non-TV device)
    val supportsPip = remember {
        !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    // State
    var availableQualities by remember { mutableStateOf<List<QualityInfo>>(emptyList()) }
    var currentStreamUrl by remember { mutableStateOf("") }
    var currentStreamId by remember { mutableStateOf("") }
    var isLoadingStream by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var subtitleList by remember { mutableStateOf<List<SubtitleInfo>>(emptyList()) }
    var subjectId by remember { mutableStateOf("") }
    var detailPath by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(false) }

    // UI dropdown panels (instead of dialogs)
    var openDropdown by remember { mutableStateOf<String?>(null) } // "quality", "audio", "subtitle", or null
    var dropdownFocusIdx by remember { mutableIntStateOf(0) }
    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var selectedAudioTrack by remember { mutableStateOf<TrackInfo?>(null) }
    var selectedSubtitleTrack by remember { mutableStateOf<TrackInfo?>(null) }
    
    // Dub support - audio source switching
    var availableDubs by remember { mutableStateOf<List<DubInfo>>(emptyList()) }
    var selectedDub by remember { mutableStateOf<DubInfo?>(null) }
    var currentSeason by remember { mutableIntStateOf(0) }
    var currentEpisode by remember { mutableIntStateOf(0) }

    // Parse episodes
    val episodes = remember(episodesJson) {
        if (episodesJson.isNotBlank()) {
            try {
                val decoded = URLDecoder.decode(episodesJson, "UTF-8")
                val mapper = jacksonObjectMapper()
                val list: List<Map<String, String>> = mapper.readValue(decoded)
                list.map { EpisodeItem(it["url"] ?: "", it["title"] ?: "") }
            } catch (e: Exception) { emptyList() }
        } else emptyList()
    }
    val isSeries = episodes.isNotEmpty()

    var currentIndex by remember { mutableStateOf(startIndex) }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showChannelInfo by remember { mutableStateOf(false) }
    var channelInfoText by remember { mutableStateOf("") }
    var exoPlayerState by remember { mutableStateOf<ExoPlayer?>(null) }
    var trackSelectorState by remember { mutableStateOf<DefaultTrackSelector?>(null) }
    var playerViewState by remember { mutableStateOf<PlayerView?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentScaleMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentSpeedIndex by remember { mutableIntStateOf(2) } // Start at 1.0x
    var isInPipMode by remember { mutableStateOf(false) }
    val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val scaleModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "Fixed Width",
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to "Fixed Height"
    )

    // ========== TV Remote: Virtual Focus System ==========
    // Rows: 0=top bar, 1=dropdown panel (when open), 2=next episode button (series only), 3=bottom buttons
    // When dropdown closed: 0=top bar, 2=next ep (if series & has next), 3=bottom buttons
    var focusRow by remember { mutableIntStateOf(3) } // Start on bottom buttons (center on play/pause)
    var topFocusIdx by remember { mutableIntStateOf(0) }
    var bottomFocusIdx by remember { mutableIntStateOf(if (isSeries) 1 else 0) } // center on play/pause
    var dialogFocusIdx by remember { mutableIntStateOf(0) }

    // Check if next episode is available
    val hasNextEpisode = remember(currentIndex, episodes) {
        isSeries && currentIndex < episodes.size - 1
    }

    // Build control item lists (reactive to state changes)
    val topBarItems = remember(isLive, supportsPip) {
        buildList {
            add("back")
            if (!isLive) {
                if (supportsPip) {
                    add("pip")
                }
                add("quality")
                add("audio")
                add("subtitle")
                add("speed")
                add("scale")
            }
        }
    }
    val bottomBarItems = remember(isSeries) {
        buildList {
            add("play_pause")
            // next_ep is now a separate row (row 2) for series
        }
    }

    // ========== Headers / DataSource / Player ==========
    val headers = remember {
        mutableMapOf(
            "Referer" to "https://themoviebox.org/",
            "Origin" to "https://themoviebox.org",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
    }
    val dataSourceFactory = remember(headers) {
        DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setUserAgent(headers["User-Agent"] ?: "ExoPlayer")
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15000, 50000, 500, 500).build()

    val exoPlayer = remember {
        val trackSelector = DefaultTrackSelector(context)
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .build()
    }.also { exoPlayerState = it; trackSelectorState = it.trackSelector as DefaultTrackSelector }

    // ========== Lifecycle: Pause playback when app goes to background ==========
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "App paused")
                    // Try to enter PIP mode if supported and playing
                    if (supportsPip && exoPlayer.isPlaying && !isLive) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                        }
                    } else {
                        // Pause playback if PIP is not supported or if it's a live stream
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            exoPlayer.playWhenReady = false
                        }
                    }
                    isPlaying = exoPlayer.isPlaying
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "App resumed")
                    isInPipMode = false
                    // Optional: Resume playback when app returns to foreground
                    // Uncomment if you want auto-resume behavior
                    // Log.d(TAG, "App resumed - playback remains paused")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // ========== Helper functions ==========
    fun resetAutoHideTimer() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5000)
            // Don't auto-hide if dropdown is open on phone/touch devices
            if (!isTv && openDropdown != null) {
                return@launch
            }
            isControlsVisible = false
            // Reset focus to play/pause when auto-hiding
            openDropdown = null
            focusRow = 3
            bottomFocusIdx = 0
            topFocusIdx = 0
            dropdownFocusIdx = 0
        }
    }

    fun showControlsTemporarily() {
        hideJob?.cancel()
        isControlsVisible = true
        // Reset all state when showing controls again
        openDropdown = null
        focusRow = 3  // Start on bottom buttons (play/pause)
        bottomFocusIdx = 0
        topFocusIdx = 0
        dropdownFocusIdx = 0
        resetAutoHideTimer()
    }

    fun showChannelInfoTemporarily(text: String) {
        channelInfoText = text
        showChannelInfo = true
        scope.launch {
            delay(2000)
            showChannelInfo = false
        }
        resetAutoHideTimer()
    }



    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    fun cycleScaleMode() {
        val currentIdx = scaleModes.indexOfFirst { it.first == currentScaleMode }
        val nextIdx = (currentIdx + 1) % scaleModes.size
        currentScaleMode = scaleModes[nextIdx].first
        playerViewState?.resizeMode = currentScaleMode
        showChannelInfoTemporarily("Scale: ${scaleModes[nextIdx].second}")
    }

    fun cycleSpeedMode() {
        val nextIdx = (currentSpeedIndex + 1) % playbackSpeeds.size
        currentSpeedIndex = nextIdx
        val newSpeed = playbackSpeeds[nextIdx]
        exoPlayerState?.setPlaybackSpeed(newSpeed)
        exoPlayerState?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(newSpeed, 1.0f))
        showChannelInfoTemporarily("Speed: ${newSpeed}x")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureInPictureMode() {
        if (!supportsPip) {
            Log.w(TAG, "PIP mode not supported on this device (TV or Android < 8.0)")
            if (isTv) {
                showChannelInfoTemporarily("PIP not supported on TV")
            } else {
                showChannelInfoTemporarily("PIP requires Android 8.0+")
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
                    showChannelInfoTemporarily("PIP not available")
                    // Restore fullscreen if PIP failed
                    act.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "PIP not supported by activity: ${e.message}")
                showChannelInfoTemporarily("PIP not available")
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter PIP mode: ${e.message}", e)
                showChannelInfoTemporarily("PIP error")
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    // ========== Build MediaItem ==========
    fun buildMediaItem(videoUrl: String, subtitles: List<SubtitleInfo>): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))
            .setCustomCacheKey(videoUrl)
        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType("application/x-subrip")
                    .setLanguage(sub.languageCode)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    // ========== Episode Navigation ==========
    fun playEpisode(index: Int) {
        if (index < 0 || index >= episodes.size) {
            if (index >= episodes.size) showChannelInfoTemporarily("No more episodes")
            return
        }
        val ep = episodes[index]
        currentIndex = index
        currentTitle = ep.title
        isLoadingStream = true
        streamError = null
        // Hide controls and reset all state when playing new episode
        openDropdown = null
        focusRow = 3
        bottomFocusIdx = 0
        topFocusIdx = 0
        dropdownFocusIdx = 0
        isControlsVisible = false

        scope.launch {
            Log.d(TAG, "Playing episode $index: ${ep.title}")
            val linkData = ep.url.removePrefix("moviebox://")
            val parts = linkData.split("|")
            subjectId = parts.getOrNull(0) ?: ""
            currentSeason = parts.getOrNull(1)?.toIntOrNull() ?: 0
            currentEpisode = parts.getOrNull(2)?.toIntOrNull() ?: 0
            detailPath = parts.getOrNull(3) ?: ""
            Log.d(TAG, "  -> linkData=$linkData, subjectId=$subjectId, se=$currentSeason, ep=$currentEpisode, detailPath=$detailPath")

            Log.d(TAG, "  -> Calling getStreams...")
            val qualities = movieRepo.getStreams(linkData)
            Log.d(TAG, "  -> getStreams returned ${qualities.size} qualities")

            if (qualities.isNotEmpty()) {
                val best = qualities.maxByOrNull { it.resolution } ?: qualities.first()
                currentStreamUrl = best.url
                currentStreamId = best.id
                Log.d(TAG, "  -> Selected quality: ${best.label}, URL: ${best.url.take(100)}")

                // Fetch dubs for audio source switching
                val dubsDeferred = async(Dispatchers.IO) {
                    if (detailPath.isNotBlank()) {
                        Log.d(TAG, "  -> Fetching dubs for detailPath=$detailPath")
                        movieRepo.fetchDubs(detailPath)
                    } else {
                        Log.w(TAG, "  -> Skipping dubs fetch - detailPath is blank")
                        emptyList()
                    }
                }

                val subsDeferred = async(Dispatchers.IO) {
                    movieRepo.fetchSubtitles(subjectId, best.id, detailPath)
                }

                val player = exoPlayerState ?: return@launch
                val mediaItem = buildMediaItem(best.url, emptyList())
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                Log.d(TAG, "  -> ExoPlayer prepare called")

                val dubs = dubsDeferred.await()
                availableDubs = dubs
                Log.d(TAG, "  -> Fetched ${dubs.size} dubs: ${dubs.map { it.languageName }}")
                
                // Set selected dub based on current subjectId
                if (dubs.isNotEmpty()) {
                    val currentDub = dubs.find { it.subjectId == subjectId }
                    selectedDub = currentDub ?: dubs.firstOrNull { it.isOriginal }
                    Log.d(TAG, "  -> Selected dub: ${selectedDub?.languageName} (subjectId=${selectedDub?.subjectId})")
                } else {
                    Log.w(TAG, "  -> No dubs available, will use player audio tracks")
                }

                val subs = subsDeferred.await()
                subtitleList = subs
                Log.d(TAG, "  -> Fetched ${subs.size} subtitles")

                if (subs.isNotEmpty() && player.playbackState == Player.STATE_BUFFERING) {
                    Log.d(TAG, "  -> Re-setting media item with subtitles")
                    player.setMediaItem(buildMediaItem(best.url, subs), false)
                }
                showChannelInfoTemporarily("${ep.title}")
            } else {
                Log.e(TAG, "  -> No streams available for episode")
                streamError = "No streams available"
            }
            isLoadingStream = false
        }
    }

    fun playNextEpisode() = playEpisode(currentIndex + 1)
    fun playPreviousEpisode() = playEpisode(currentIndex - 1)

    // ========== Dub Audio Selection ==========
    fun selectDub(dub: DubInfo) {
        if (selectedDub?.subjectId == dub.subjectId) return
        Log.d(TAG, "Selecting dub: ${dub.languageName} (subjectId=${dub.subjectId})")
        val player = exoPlayerState ?: return
        val wasPlaying = player.isPlaying
        val currentPos = player.currentPosition
        
        selectedDub = dub
        
        scope.launch {
            Log.d(TAG, "  -> Fetching streams for dub subjectId=${dub.subjectId}, se=$currentSeason, ep=$currentEpisode")
            val linkData = "${dub.subjectId}|$currentSeason|$currentEpisode|${dub.detailPath}"
            val qualities = movieRepo.getStreams(linkData)
            
            if (qualities.isNotEmpty()) {
                val best = qualities.maxByOrNull { it.resolution } ?: qualities.first()
                currentStreamUrl = best.url
                currentStreamId = best.id
                subjectId = dub.subjectId
                detailPath = dub.detailPath
                Log.d(TAG, "  -> Selected quality: ${best.label}")
                
                val subsDeferred = async(Dispatchers.IO) {
                    movieRepo.fetchSubtitles(dub.subjectId, best.id, dub.detailPath)
                }
                
                val mediaItem = buildMediaItem(best.url, emptyList())
                player.setMediaItem(mediaItem)
                player.prepare()
                
                val subs = subsDeferred.await()
                subtitleList = subs
                
                if (subs.isNotEmpty() && player.playbackState == Player.STATE_BUFFERING) {
                    player.setMediaItem(buildMediaItem(best.url, subs), false)
                }
                
                scope.launch {
                    while (player.playbackState != Player.STATE_READY) delay(50)
                    player.seekTo(currentPos)
                    if (wasPlaying) player.play()
                    showChannelInfoTemporarily("Audio: ${dub.languageName}")
                }
            } else {
                Log.e(TAG, "  -> No streams available for dub")
            }
        }
    }

    // ========== Quality / Track Selection ==========
    fun selectQuality(quality: QualityInfo) {
        if (currentStreamUrl == quality.url) return
        val player = exoPlayerState ?: return
        val wasPlaying = player.isPlaying
        val currentPos = player.currentPosition
        currentStreamUrl = quality.url
        currentStreamId = quality.id
        val mediaItem = buildMediaItem(quality.url, subtitleList)
        player.setMediaItem(mediaItem)
        player.prepare()
        scope.launch {
            while (player.playbackState != Player.STATE_READY) delay(50)
            player.seekTo(currentPos)
            if (wasPlaying) player.play()
            showChannelInfoTemporarily("Quality: ${quality.label}")
        }
    }

    fun selectAudioTrack(track: TrackInfo) {
        val selector = trackSelectorState ?: return
        var idx = 0
        exoPlayer.currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO && idx++ == track.trackGroupIndex) {
                selector.setParameters(selector.buildUponParameters().setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)))
                selectedAudioTrack = track
                showChannelInfoTemporarily("Audio: ${track.label}")
            }
        }
    }

    fun selectSubtitleTrack(track: TrackInfo) {
        val selector = trackSelectorState ?: return
        if (track.trackGroupIndex < 0) {
            selector.setParameters(selector.buildUponParameters().setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT)))
            selectedSubtitleTrack = track
            showChannelInfoTemporarily("Subtitles off")
        } else {
            var idx = 0
            exoPlayer.currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_TEXT && idx++ == track.trackGroupIndex) {
                    selector.setParameters(selector.buildUponParameters().setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)))
                    selectedSubtitleTrack = track
                    showChannelInfoTemporarily("Subtitles: ${track.label}")
                }
            }
        }
    }

    // ========== Activate Focused Control ==========
    fun activateFocusedControl() {
        if (focusRow == 0) {
            when (topBarItems.getOrNull(topFocusIdx)) {
                "back" -> navController.popBackStack()
                "pip" -> enterPictureInPictureMode()
                "quality" -> { openDropdown = "quality"; dropdownFocusIdx = 0; focusRow = 1 }
                "audio" -> { openDropdown = "audio"; dropdownFocusIdx = 0; focusRow = 1 }
                "subtitle" -> { openDropdown = "subtitle"; dropdownFocusIdx = 0; focusRow = 1 }
                "speed" -> cycleSpeedMode()
                "scale" -> cycleScaleMode()
            }
        } else if (focusRow == 3) {
            when (bottomBarItems.getOrNull(bottomFocusIdx)) {
                "play_pause" -> { val p = exoPlayerState; if (p != null && p.isPlaying) p.pause() else p?.play() }
            }
        }
    }

    // ========== TV Remote Key Handler ==========
    val keyChannel = remember { Channel<Int>(Channel.BUFFERED) }

    fun keyName(code: Int): String = when (code) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> "UP"
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        AndroidKeyEvent.KEYCODE_DPAD_CENTER -> "CENTER"
        AndroidKeyEvent.KEYCODE_ENTER -> "ENTER"
        AndroidKeyEvent.KEYCODE_BACK -> "BACK"
        AndroidKeyEvent.KEYCODE_ESCAPE -> "ESC"
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "PLAY_PAUSE"
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> "REWIND"
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "FAST_FORWARD"
        else -> "KEY_$code"
    }

    fun handleKeyCode(code: Int): Boolean {
        Log.d(TAG, "handleKeyCode: ${keyName(code)} | controlsVisible=$isControlsVisible focusRow=$focusRow topIdx=$topFocusIdx botIdx=$bottomFocusIdx isLive=$isLive")

        // --- Controls hidden: up/down/center show controls, left/right SEEK ---
        if (!isControlsVisible) {
            Log.d(TAG, "  -> controls hidden")
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    // showControlsTemporarily() handles all the resetting
                    showControlsTemporarily(); return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    exoPlayerState?.seekBack(); return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    exoPlayerState?.seekForward(); return true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                    Log.d(TAG, "  -> rewind button pressed")
                    exoPlayerState?.seekBack(); return true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    Log.d(TAG, "  -> fast forward button pressed")
                    exoPlayerState?.seekForward(); return true
                }
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    val p = exoPlayerState; if (p != null && p.isPlaying) p.pause() else p?.play(); return true
                }
                AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                    navController.popBackStack(); return true
                }
            }
            return false
        }

        // --- Controls visible: virtual focus navigation ---
        resetAutoHideTimer()
        val hasDropdown = openDropdown != null
        val maxRow = if (hasDropdown && hasNextEpisode) 3 else if (hasNextEpisode) 3 else 2
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                when {
                    focusRow == 3 -> {
                        if (hasNextEpisode) {
                            Log.d(TAG, "  -> bottom -> next episode")
                            focusRow = 2
                        } else if (hasDropdown) {
                            Log.d(TAG, "  -> bottom -> dropdown")
                            focusRow = 1
                            dropdownFocusIdx = 0
                        } else {
                            Log.d(TAG, "  -> bottom -> top bar")
                            focusRow = 0
                            topFocusIdx = minOf(topFocusIdx, topBarItems.size - 1)
                        }
                    }
                    focusRow == 2 -> {
                        Log.d(TAG, "  -> next episode -> top bar")
                        focusRow = 0
                        topFocusIdx = minOf(topFocusIdx, topBarItems.size - 1)
                    }
                    focusRow == 1 && hasDropdown -> {
                        // If at first dropdown item, go to top bar
                        if (dropdownFocusIdx == 0) {
                            Log.d(TAG, "  -> dropdown -> top bar")
                            focusRow = 0
                        } else {
                            // Move to previous dropdown item
                            Log.d(TAG, "  -> dropdown up -> idx=$dropdownFocusIdx")
                            dropdownFocusIdx = maxOf(0, dropdownFocusIdx - 1)
                        }
                    }
                    else -> {
                        Log.d(TAG, "  -> UP ignored (row=$focusRow, maxRow=$maxRow)")
                    }
                }
                Log.d(TAG, "  AFTER: focusRow=$focusRow topIdx=$topFocusIdx dropIdx=$dropdownFocusIdx botIdx=$bottomFocusIdx")
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                when {
                    focusRow == 0 && hasDropdown -> {
                        Log.d(TAG, "  -> top bar -> dropdown")
                        focusRow = 1
                        dropdownFocusIdx = 0
                    }
                    focusRow == 0 && !hasDropdown -> {
                        Log.d(TAG, "  -> top bar -> bottom buttons")
                        focusRow = 3
                        bottomFocusIdx = minOf(bottomFocusIdx, bottomBarItems.size - 1)
                    }
                    focusRow == 1 && hasDropdown -> {
                        val itemCount = when (openDropdown) {
                            "quality" -> availableQualities.sortedByDescending { it.resolution }.size
                            "audio" -> availableDubs.size
                            "subtitle" -> subtitleTracks.size
                            else -> 0
                        }
                        // If at last dropdown item, go to next episode (if available) or bottom buttons
                        if (dropdownFocusIdx >= itemCount - 1) {
                            if (hasNextEpisode) {
                                Log.d(TAG, "  -> dropdown -> next episode")
                                focusRow = 2
                            } else {
                                Log.d(TAG, "  -> dropdown -> bottom buttons")
                                focusRow = 3
                                bottomFocusIdx = minOf(bottomFocusIdx, bottomBarItems.size - 1)
                            }
                        } else {
                            // Move to next dropdown item
                            Log.d(TAG, "  -> dropdown down -> idx=$dropdownFocusIdx")
                            dropdownFocusIdx = minOf(itemCount - 1, dropdownFocusIdx + 1)
                        }
                    }
                    focusRow == 2 && hasNextEpisode -> {
                        Log.d(TAG, "  -> next episode -> bottom buttons")
                        focusRow = 3
                        bottomFocusIdx = minOf(bottomFocusIdx, bottomBarItems.size - 1)
                    }
                    else -> {
                        Log.d(TAG, "  -> DOWN ignored (row=$focusRow, maxRow=$maxRow)")
                    }
                }
                Log.d(TAG, "  AFTER: focusRow=$focusRow topIdx=$topFocusIdx dropIdx=$dropdownFocusIdx botIdx=$bottomFocusIdx")
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                when {
                    focusRow == 0 -> {
                        topFocusIdx = if (topFocusIdx > 0) topFocusIdx - 1 else topBarItems.size - 1
                        val item = topBarItems.getOrNull(topFocusIdx)
                        Log.d(TAG, "  -> top bar left -> idx=$topFocusIdx item=$item")
                        // Auto-open dropdown for quality/audio/subtitle buttons (keep focus on top bar)
                        if (item in listOf("quality", "audio", "subtitle")) {
                            openDropdown = item
                            dropdownFocusIdx = 0
                            focusRow = 0  // Keep focus on top bar
                        } else {
                            // Close dropdown when navigating away from quality/audio/subtitle
                            openDropdown = null
                        }
                    }
                    focusRow == 1 && hasDropdown -> {
                        // In dropdown, LEFT moves to previous item (optional - for horizontal nav)
                        val itemCount = when (openDropdown) {
                            "quality" -> availableQualities.sortedByDescending { it.resolution }.size
                            "audio" -> availableDubs.size
                            "subtitle" -> subtitleTracks.size
                            else -> 0
                        }
                        dropdownFocusIdx = maxOf(0, dropdownFocusIdx - 1)
                        resetAutoHideTimer()
                        Log.d(TAG, "  -> dropdown left -> idx=$dropdownFocusIdx")
                    }
                    focusRow == 1 && !hasDropdown || focusRow == 2 || focusRow == 3 -> {
                        // Direct seek backward 10 seconds (no focus movement)
                        if (!isLive) {
                            exoPlayerState?.seekBack()
                            Log.d(TAG, "  -> seek backward 10s")
                        }
                    }
                }
                Log.d(TAG, "  AFTER: focusRow=$focusRow topIdx=$topFocusIdx dropIdx=$dropdownFocusIdx botIdx=$bottomFocusIdx")
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                when {
                    focusRow == 0 -> {
                        topFocusIdx = (topFocusIdx + 1) % topBarItems.size
                        val item = topBarItems.getOrNull(topFocusIdx)
                        Log.d(TAG, "  -> top bar right -> idx=$topFocusIdx item=$item")
                        // Auto-open dropdown for quality/audio/subtitle buttons (keep focus on top bar)
                        if (item in listOf("quality", "audio", "subtitle")) {
                            openDropdown = item
                            dropdownFocusIdx = 0
                            focusRow = 0  // Keep focus on top bar
                        } else {
                            // Close dropdown when navigating away from quality/audio/subtitle
                            openDropdown = null
                        }
                    }
                    focusRow == 1 && hasDropdown -> {
                        // In dropdown, RIGHT moves to next item (optional - for horizontal nav)
                        val itemCount = when (openDropdown) {
                            "quality" -> availableQualities.sortedByDescending { it.resolution }.size
                            "audio" -> availableDubs.size
                            "subtitle" -> subtitleTracks.size
                            else -> 0
                        }
                        dropdownFocusIdx = minOf(itemCount - 1, dropdownFocusIdx + 1)
                        resetAutoHideTimer()
                        Log.d(TAG, "  -> dropdown right -> idx=$dropdownFocusIdx")
                    }
                    focusRow == 1 && !hasDropdown || focusRow == 2 || focusRow == 3 -> {
                        // Direct seek forward 10 seconds (no focus movement)
                        if (!isLive) {
                            exoPlayerState?.seekForward()
                            Log.d(TAG, "  -> seek forward 10s")
                        }
                    }
                }
                Log.d(TAG, "  AFTER: focusRow=$focusRow topIdx=$topFocusIdx dropIdx=$dropdownFocusIdx botIdx=$bottomFocusIdx")
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                if (focusRow == 0) {
                    val item = topBarItems.getOrNull(topFocusIdx)
                    Log.d(TAG, "  -> activate: row=$focusRow item=$item")
                    activateFocusedControl()
                } else if (focusRow == 1 && hasDropdown) {
                    // Select dropdown item
                    when (openDropdown) {
                        "quality" -> {
                            val sorted = availableQualities.sortedByDescending { it.resolution }
                            if (dropdownFocusIdx < sorted.size) selectQuality(sorted[dropdownFocusIdx])
                        }
                        "audio" -> { if (dropdownFocusIdx < availableDubs.size) selectDub(availableDubs[dropdownFocusIdx]) }
                        "subtitle" -> { if (dropdownFocusIdx < subtitleTracks.size) selectSubtitleTrack(subtitleTracks[dropdownFocusIdx]) }
                    }
                    openDropdown = null
                    focusRow = 0
                    Log.d(TAG, "  -> dropdown selected, closed, back to top bar")
                } else if (focusRow == 2 && hasNextEpisode) {
                    // Activate next episode button
                    Log.d(TAG, "  -> activate: next episode")
                    playNextEpisode()
                } else if (focusRow == 3) {
                    val item = bottomBarItems.getOrNull(bottomFocusIdx)
                    Log.d(TAG, "  -> activate: row=$focusRow item=$item")
                    activateFocusedControl()
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Log.d(TAG, "  -> media play/pause toggle")
                val p = exoPlayerState; if (p != null && p.isPlaying) p.pause() else p?.play()
                return true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                Log.d(TAG, "  -> rewind button pressed")
                if (!isLive) {
                    exoPlayerState?.seekBack()
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                Log.d(TAG, "  -> fast forward button pressed")
                if (!isLive) {
                    exoPlayerState?.seekForward()
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                if (focusRow == 1 && hasDropdown) {
                    // Close dropdown and return to top bar
                    openDropdown = null
                    focusRow = 0
                    Log.d(TAG, "  -> back: close dropdown, return to top bar")
                } else if (focusRow == 2 && hasNextEpisode) {
                    // Go to bottom buttons
                    focusRow = 3
                    bottomFocusIdx = 0
                    Log.d(TAG, "  -> back: next episode -> bottom buttons")
                } else {
                    // Hide controls and reset focus to play/pause
                    Log.d(TAG, "  -> back: hide controls, reset focus")
                    isControlsVisible = false
                    openDropdown = null
                    focusRow = 3
                    bottomFocusIdx = 0
                    topFocusIdx = 0
                    dropdownFocusIdx = 0
                }
                return true
            }
        }
        Log.d(TAG, "  -> key not handled: ${keyName(code)}")
        return false
    }

    // Handle key events in a Compose coroutine (guarantees fresh state access)
    LaunchedEffect(Unit) {
        Log.d(TAG, "keyChannel LaunchedEffect started, waiting for keys...")
        for (code in keyChannel) {
            Log.d(TAG, "keyChannel received: ${keyName(code)}")
            handleKeyCode(code)
        }
    }

    var hasInitialLoad by remember { mutableStateOf(false) }
    var hasInitializedStreams by remember { mutableStateOf(false) }

    // ========== Initial Stream Load ==========
    LaunchedEffect(Unit) {
        if (hasInitializedStreams) {
            Log.d(TAG, "=== PlayerScreen LaunchedEffect SKIP - Already initialized ===")
            return@LaunchedEffect
        }

        Log.d(TAG, "=== PlayerScreen LaunchedEffect START ===")
        Log.d(TAG, "url='$url', title='$title', slug='$slug', type='$type', resumePosition=$resumePosition")
        Log.d(TAG, "url.isBlank=${url.isBlank()}, url.startsWith('moviebox://')=${url.startsWith("moviebox://")}")
        if (url.startsWith("moviebox://")) {
            Log.d(TAG, "Starting initial stream load for moviebox:// URL")
            val linkData = url.removePrefix("moviebox://")
            val parts = linkData.split("|")
            subjectId = parts.getOrNull(0) ?: ""
            currentSeason = parts.getOrNull(1)?.toIntOrNull() ?: 0
            currentEpisode = parts.getOrNull(2)?.toIntOrNull() ?: 0
            detailPath = parts.getOrNull(3) ?: ""
            Log.d(TAG, "Fetching streams for subjectId=$subjectId, se=$currentSeason, ep=$currentEpisode, detailPath=$detailPath, linkData='$linkData'")

            // Properly await the async result
            scope.launch {
                Log.d(TAG, "Calling movieRepo.getStreams...")
                val qualities = movieRepo.getStreams(linkData)
                Log.d(TAG, "getStreams returned ${qualities.size} qualities")

                if (qualities.isNotEmpty()) {
                    availableQualities = qualities
                    val best = qualities.maxByOrNull { it.resolution } ?: qualities.first()
                    currentStreamUrl = best.url
                    currentStreamId = best.id
                    Log.d(TAG, "Available qualities: ${qualities.map { it.label }}, Selected: ${best.label}, URL: ${best.url.take(100)}")

                    // Fetch dubs for audio source switching
                    val dubsDeferred = async(Dispatchers.IO) {
                        if (detailPath.isNotBlank()) {
                            Log.d(TAG, "Fetching dubs for detailPath=$detailPath")
                            movieRepo.fetchDubs(detailPath)
                        } else {
                            Log.w(TAG, "Skipping dubs fetch - detailPath is blank")
                            emptyList()
                        }
                    }

                    val subsDeferred = async(Dispatchers.IO) {
                        movieRepo.fetchSubtitles(subjectId, best.id, detailPath)
                    }

                    val mediaItem = buildMediaItem(best.url, emptyList())
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    Log.d(TAG, "ExoPlayer prepare called, playing stream")

                    val dubs = dubsDeferred.await()
                    availableDubs = dubs
                    Log.d(TAG, "Fetched ${dubs.size} dubs: ${dubs.map { it.languageName }}")
                    
                    // Set selected dub based on current subjectId
                    if (dubs.isNotEmpty()) {
                        val currentDub = dubs.find { it.subjectId == subjectId }
                        selectedDub = currentDub ?: dubs.firstOrNull { it.isOriginal }
                        Log.d(TAG, "Selected dub: ${selectedDub?.languageName} (subjectId=${selectedDub?.subjectId})")
                    } else {
                        Log.w(TAG, "No dubs available, will use player audio tracks")
                    }

                    val subs = subsDeferred.await()
                    subtitleList = subs
                    Log.d(TAG, "Fetched ${subs.size} subtitles")

                    if (subs.isNotEmpty() && exoPlayer.playbackState == Player.STATE_BUFFERING) {
                        Log.d(TAG, "Re-setting media item with subtitles")
                        exoPlayer.setMediaItem(buildMediaItem(best.url, subs), false)
                    }
                    hasInitializedStreams = true
                    Log.d(TAG, "✅ Streams initialized, hasInitializedStreams = true")
                } else {
                    Log.e(TAG, "No streams available!")
                    streamError = "No streams available"
                    hasInitializedStreams = true
                }
                isLoadingStream = false
            }
        } else {
            // Direct URL (movie streamUrl, etc.) - load directly into player
            Log.d(TAG, "Loading direct stream: url='$url', isBlank=${url.isBlank()}")
            if (url.isBlank()) {
                Log.e(TAG, "ERROR: URL is blank! This should not happen!")
            }
            currentStreamUrl = url
            val mediaItem = buildMediaItem(url, emptyList())
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            isLoadingStream = false
            hasInitializedStreams = true
            Log.d(TAG, "✅ Direct stream initialized, hasInitializedStreams = true")
        }
    }

    // Mark initial load done
    LaunchedEffect(exoPlayer) {
        while (exoPlayer.playbackState != Player.STATE_READY && exoPlayer.playbackState != Player.STATE_ENDED) delay(100)
        if (exoPlayer.playbackState == Player.STATE_READY) hasInitialLoad = true
    }

    // Resume from saved position
    LaunchedEffect(exoPlayer, resumePosition) {
        Log.d(TAG, "=== Resume Position LaunchedEffect START === resumePosition: $resumePosition, isLive: $isLive")
        if (resumePosition > 0 && !isLive) {
            Log.d(TAG, "Waiting for player to be ready...")
            while (exoPlayer.duration <= 0 && exoPlayer.playbackState != Player.STATE_READY) delay(100)
            Log.d(TAG, "Player ready - duration: ${exoPlayer.duration}, seeking to: $resumePosition")
            if (exoPlayer.duration > 0 && resumePosition < exoPlayer.duration) {
                exoPlayer.seekTo(resumePosition)
                currentPosition = resumePosition
                Log.d(TAG, "✅ Seeked to position: $resumePosition")
            } else {
                Log.w(TAG, "❌ Cannot seek - duration: ${exoPlayer.duration}, resumePosition: $resumePosition")
            }
        } else {
            Log.d(TAG, "Skipping resume - resumePosition: $resumePosition, isLive: $isLive")
        }
        Log.d(TAG, "=== Resume Position LaunchedEffect END ===")
    }

    // Extract tracks + force English subtitle
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(500)
            if (exoPlayer.currentTracks.groups.isNotEmpty()) {
                val aList = mutableListOf<TrackInfo>()
                val sList = mutableListOf<TrackInfo>(TrackInfo(-1, -1, "Off", null))
                var aIdx = 0; var sIdx = 0
                exoPlayer.currentTracks.groups.forEach { group ->
                    when (group.type) {
                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                aList.add(TrackInfo(aIdx, i, f.label ?: f.language ?: "Audio $i", f.language))
                            }
                            aIdx++
                        }
                        C.TRACK_TYPE_TEXT -> {
                            for (i in 0 until group.length) {
                                val f = group.getTrackFormat(i)
                                sList.add(TrackInfo(sIdx, i, f.label ?: f.language ?: "Sub $i", f.language))
                            }
                            sIdx++
                        }
                    }
                }
                audioTracks = aList; subtitleTracks = sList
                selectedAudioTrack = aList.firstOrNull()
                val eng = sList.find { it.language == "en" || it.label.contains("English", ignoreCase = true) }
                selectedSubtitleTrack = eng ?: sList.firstOrNull()

                if (eng != null && eng.trackGroupIndex >= 0) {
                    while (exoPlayer.playbackState != Player.STATE_READY) delay(100)
                    var gi = 0
                    exoPlayer.currentTracks.groups.forEach { group ->
                        if (group.type == C.TRACK_TYPE_TEXT && gi++ == eng.trackGroupIndex) {
                            trackSelectorState?.setParameters(trackSelectorState!!.buildUponParameters()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, eng.trackIndex)))
                        }
                    }
                }
                break
            }
        }
    }

    // Save progress
    LaunchedEffect(exoPlayer, slug, type, currentSeason, currentEpisode, title, poster) {
        var lastSaved = 0L
        while (true) {
            delay(5000)
            if (!isLive && slug.isNotBlank() && exoPlayer.duration > 0) {
                val pos = exoPlayer.currentPosition; val dur = exoPlayer.duration
                if (kotlin.math.abs(pos - lastSaved) > 5000 && pos < dur - 5000) {
                    lastSaved = pos
                    // Calculate progress percentage (0-100)
                    val progressPct = if (dur > 0) ((pos * 100) / dur).toInt() else 0

                    continueWatchingRepo.addOrUpdateItem(ContinueWatchingItem(
                        contentId = if (type == "movie") slug else "${slug}_s${currentSeason}e${currentEpisode}",
                        slug = slug, title = title, posterUrl = poster, type = type,
                        seasonNumber = currentSeason, episodeNumber = currentEpisode,
                        episodeTitle = if (type == "series") title.substringAfter(" - ") else "",
                        positionSeconds = pos / 1000, durationSeconds = dur / 1000,
                        progressPercentage = progressPct,
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    // Update UI state
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(200)
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            isPlaying = exoPlayer.isPlaying
        }
    }

    // Player events + auto-play next episode
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Do NOT update currentIndex here — we use setMediaItem() (replace),
                // so currentMediaItemIndex is always 0. playEpisode() already sets
                // currentIndex correctly before loading. Updating it here would
                // reset it to 0 after every episode switch.
                resetAutoHideTimer()
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) resetAutoHideTimer()
                if (state == Player.STATE_ENDED && isSeries && currentIndex < episodes.size - 1) {
                    Log.d(TAG, "Episode ended, auto-playing next")
                    playNextEpisode()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    // Fullscreen / orientation
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

    // ========== Focus Highlight Composable ==========
    @Composable
    fun PlayerButton(
        isFocused: Boolean,
        icon: @Composable () -> Unit,
        label: String? = null,
        isLarge: Boolean = false,
        onClick: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val size = if (isLarge) 72.dp else 52.dp
        Surface(
            color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            border = if (isFocused) BorderStroke(2.5.dp, FocusAccent) else null,
            modifier = modifier.then(if (modifier == Modifier) Modifier.size(size) else Modifier.padding(4.dp)).clickable(onClick = onClick)
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
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DialogItem(isFocused: Boolean, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
        Surface(
            color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            border = if (isFocused) BorderStroke(2.dp, FocusAccent) else null,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = if (isFocused) FocusAccent else Color.White, fontSize = 15.sp)
                if (isSelected) Icon(Icons.Filled.Check, null, tint = FocusAccent, modifier = Modifier.size(20.dp))
            }
        }
    }

    @Composable
    fun DropdownItem(isFocused: Boolean, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
        Surface(
            color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            shape = RoundedCornerShape(6.dp),
            border = if (isFocused) BorderStroke(2.dp, FocusAccent) else null,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = if (isFocused) FocusAccent else Color.White, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                if (isSelected) Icon(Icons.Filled.Check, null, tint = FocusAccent, modifier = Modifier.size(18.dp))
            }
        }
    }

    // ========== UI ==========
    // Intercept keys at Window.Callback level — BEFORE Compose's internal focus system
    // can consume D-pad events. setOnKeyListener runs too late (inside View.dispatchKeyEvent),
    // but Window.Callback.dispatchKeyEvent runs before the entire view hierarchy.
    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose {}
        val window = act.window
        val originalCallback = window.callback
        // Keys we want to handle ourselves (D-pad, center, enter, back, escape, media play/pause)
        // Volume keys, channel up/down etc. are NOT in this set — they pass through to the system.
        val handledKeys = setOf(
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE,
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        )
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode in handledKeys && event.action == AndroidKeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "WindowCallback: keyCode=${event.keyCode}(${keyName(event.keyCode)}) action=DOWN -> intercepted")
                    keyChannel.trySend(event.keyCode)
                    return true // consume — prevent Compose from stealing D-pad
                }
                return originalCallback.dispatchKeyEvent(event) // let volume, UP events etc. pass through
            }
        }
        onDispose {
            window.callback = originalCallback
            keyChannel.close()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoadingStream -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = FocusAccent) }
            streamError != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: $streamError", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("Back") }
                }
            }
            else -> {
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

                // Buffering overlay
                if (isBuffering) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FocusAccent)
                    }
                }

                // ========== Controls Overlay ==========
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // ---- Top Bar ----
                        Surface(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding(),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back button
                                PlayerButton(
                                    isFocused = focusRow == 0 && topFocusIdx == 0,
                                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(28.dp)) },
                                    onClick = {
                                        // For series, just pop back to SeriesDetail (it's already on back stack)
                                        if (type == "series" && slug.isNotBlank()) {
                                            Log.d(TAG, "Back button - popping back stack for series: $slug")
                                            navController.popBackStack()
                                        } else {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                // Title
                                Text(
                                    currentTitle,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp
                                )

                                // Top bar action buttons (quality, audio, subtitle, speed, scale)
                                if (!isLive) {
                                    topBarItems.forEachIndexed { idx, id ->
                                        if (idx == 0) return@forEachIndexed // skip "back", already rendered
                                        val fFocused = focusRow == 0 && topFocusIdx == idx
                                        when (id) {
                                            "pip" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.PictureInPicture, "PIP", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "PIP",
                                                onClick = { enterPictureInPictureMode() }
                                            )
                                            "quality" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.Hd, "Quality", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "Quality",
                                                onClick = { openDropdown = "quality"; dropdownFocusIdx = 0; focusRow = 1 }
                                            )
                                            "audio" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.VolumeUp, "Audio", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "Audio",
                                                onClick = { openDropdown = "audio"; dropdownFocusIdx = 0; focusRow = 1 }
                                            )
                                            "subtitle" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.ClosedCaption, "Sub", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "Subtitle",
                                                onClick = { openDropdown = "subtitle"; dropdownFocusIdx = 0; focusRow = 1 }
                                            )
                                            "speed" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.Speed, "Speed", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "Speed",
                                                onClick = { cycleSpeedMode() }
                                            )
                                            "scale" -> PlayerButton(fFocused,
                                                icon = { Icon(Icons.Filled.ZoomOutMap, "Scale", tint = Color.White, modifier = Modifier.size(26.dp)) },
                                                label = "Scale",
                                                onClick = { cycleScaleMode() }
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }
                            }
                        }

                        // ---- Bottom Bar ----
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 62.dp, vertical = 0.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Progress indicator (seekbar - non-focusable, visual only)
                                if (!isLive) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                        Slider(
                                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                            onValueChange = { exoPlayer.seekTo((it * duration).toLong()) },
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = FocusAccent,
                                                thumbColor = FocusAccent,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                            )
                                        )
                                        Text(formatTime(duration), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }

                                // Control buttons (below seekbar)
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Render all buttons manually to control focus
                                    var focusIdx = 0

                                    // Rewind button (NOT focusable, only for touch)
                                    if (!isLive) {
                                        PlayerButton(
                                            isFocused = false, // Never focusable
                                            icon = { Icon(Icons.Filled.FastRewind, "Rewind", tint = Color.White, modifier = Modifier.size(32.dp)) },
                                            onClick = { exoPlayer.seekBack(); showChannelInfoTemporarily("⏪ Rewind 10s") }
                                        )
                                    }

                                    // Play/Pause button (always focusable)
                                    PlayerButton(
                                        isFocused = focusRow == 3 && bottomFocusIdx == focusIdx,
                                        isLarge = true,
                                        icon = {
                                            Icon(
                                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                "Play/Pause",
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        },
                                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
                                    )
                                    focusIdx++

                                    // Forward button (NOT focusable, only for touch)
                                    if (!isLive) {
                                        PlayerButton(
                                            isFocused = false, // Never focusable
                                            icon = { Icon(Icons.Filled.FastForward, "Forward", tint = Color.White, modifier = Modifier.size(32.dp)) },
                                            onClick = { exoPlayer.seekForward(); showChannelInfoTemporarily("⏩ Forward 10s") }
                                        )
                                    }
                                }
                            }
                        }

                        // Next Episode Button (for series, separate from bottom bar, on right side above)
                        if (isSeries && hasNextEpisode) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 32.dp, bottom = 180.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                // Semi-transparent background for Next Episode button
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (focusRow == 2) BorderStroke(2.5.dp, FocusAccent) else BorderStroke(1.5.dp, FocusAccent.copy(alpha = 0.3f)),
                                    modifier = Modifier.clickable(onClick = { playNextEpisode() })
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Next Episode",
                                            color = if (focusRow == 2) FocusAccent else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (focusRow == 2) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Icon(
                                            Icons.Filled.ArrowForward,
                                            contentDescription = "Next",
                                            tint = if (focusRow == 2) FocusAccent else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                // Episode count outside the button background
                                Text(
                                    "Episode ${currentIndex + 1} of ${episodes.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // ---- Dropdown Panel (appears below top bar, rendered last for z-index) ----
                    // Keep this inside the outer Box, but apply alignment conditionally or wrap properly:
                    if (openDropdown != null) {
                        Log.d(TAG, "DropdownRendering: openDropdown=$openDropdown")

                        // Wrapping it ensures BoxScope alignment rules apply perfectly
                        Box(
                            modifier = Modifier
                                .fillMaxSize() // Fills the parent Box to give alignment context
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd) // This will now strictly snap to the top right
                                    .padding(top = 60.dp, end = 16.dp)
                                    .requiredWidth(300.dp)
                                    .heightIn(max = 450.dp)
                            ) {
                                Log.d(TAG, "DropdownBox: Creating box with required 300dp width")
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Black.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, FocusAccent.copy(alpha = 0.5f))
                                ) {
                                    Log.d(TAG, "DropdownSurface: Rendering surface")
                                    Column(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Log.d(TAG, "DropdownColumn: Starting column with scroll")
                                        when (openDropdown) {
                                            "quality" -> {
                                                val sorted = availableQualities.sortedByDescending { it.resolution }
                                                sorted.forEachIndexed { idx, quality ->
                                                    DropdownItem(
                                                        isFocused = focusRow == 1 && dropdownFocusIdx == idx,
                                                        label = quality.label,
                                                        isSelected = currentStreamUrl == quality.url,
                                                        onClick = { selectQuality(quality); openDropdown = null; focusRow = 0 }
                                                    )
                                                }
                                            }
                                            "audio" -> {
                                                if (availableDubs.isNotEmpty()) {
                                                    availableDubs.forEachIndexed { idx, dub ->
                                                        DropdownItem(
                                                            isFocused = focusRow == 1 && dropdownFocusIdx == idx,
                                                            label = dub.languageName + if (dub.isOriginal) " (Original)" else "",
                                                            isSelected = selectedDub?.subjectId == dub.subjectId,
                                                            onClick = { selectDub(dub); openDropdown = null; focusRow = 0 }
                                                        )
                                                    }
                                                } else {
                                                    audioTracks.forEachIndexed { idx, track ->
                                                        DropdownItem(
                                                            isFocused = focusRow == 1 && dropdownFocusIdx == idx,
                                                            label = track.label,
                                                            isSelected = selectedAudioTrack?.trackGroupIndex == track.trackGroupIndex && selectedAudioTrack?.trackIndex == track.trackIndex,
                                                            onClick = { selectAudioTrack(track); openDropdown = null; focusRow = 0 }
                                                        )
                                                    }
                                                }
                                            }
                                            "subtitle" -> {
                                                subtitleTracks.forEachIndexed { idx, track ->
                                                    DropdownItem(
                                                        isFocused = focusRow == 1 && dropdownFocusIdx == idx,
                                                        label = track.label,
                                                        isSelected = selectedSubtitleTrack?.trackGroupIndex == track.trackGroupIndex && selectedSubtitleTrack?.trackIndex == track.trackIndex,
                                                        onClick = { selectSubtitleTrack(track); openDropdown = null; focusRow = 0 }
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

                // ========== Info Overlay ==========
                AnimatedVisibility(
                    visible = showChannelInfo,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp)
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp)) {
                        Text(channelInfoText, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

