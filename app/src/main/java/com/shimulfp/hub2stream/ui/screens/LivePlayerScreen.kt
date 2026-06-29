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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import com.shimulfp.hub2stream.MainActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Check
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import coil.request.ImageRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.readValue
import com.shimulfp.hub2stream.data.LiveTVRepository
import com.shimulfp.hub2stream.ui.theme.FocusAccent
import com.shimulfp.hub2stream.utils.LovetierTokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import java.util.Base64

private const val TAG = "LivePlayerScreen"

data class LiveChannelItem(
    val url: String,
    val title: String,
    val id: String = "",
    val logo: String = "",
    val cookies: String = "",
    val sourceId: String = "",
    val licenseType: String = "",  // "clearkey", "widevine", etc.
    val licenseKey: String = ""   // "key_id:key_hex" for ClearKey
)

/**
 * Build a MediaItem with optional DRM (ClearKey) configuration.
 * For ClearKey: licenseKey format is "key_id_hex:key_hex"
 * ExoPlayer expects a JSON ClearKey key set: {"keys":[{"kty":"oct","kid":"<base64>","k":"<base64>"}],"type":"temporary"}
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun buildMediaItemForChannel(channel: LiveChannelItem): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(Uri.parse(channel.url))
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(5000)
                .setMaxOffsetMs(30000)
                .setMinPlaybackSpeed(0.9f)
                .setMaxPlaybackSpeed(1.1f)
                .build()
        )

    if (channel.licenseType.equals("clearkey", ignoreCase = true) && channel.licenseKey.isNotBlank()) {
        try {
            val parts = channel.licenseKey.split(":")
            if (parts.size == 2) {
                val keyIdHex = parts[0]
                // Convert hex to Base64url (W3C ClearKey spec, RFC 4648 §5)
                val kidB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hexToBytes(keyIdHex))
                Log.d(TAG, "ClearKey DRM configured for '${channel.title}': kid=${keyIdHex.take(8)}...")
                // Only set the UUID to signal this item needs ClearKey DRM.
                // The DefaultDrmSessionManager (with forceSessionsForAudioAndVideoTracks=true)
                // will create a session even though the DASH manifest lacks default_KID.
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .build()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ClearKey license for '${channel.title}': ${e.message}")
        }
    }

    return builder.build()
}

private fun hexToBytes(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

/**
 * Custom HTTP DataSource that adds cookies to authenticated streams.
 */
class CookieAwareHttpDataSource(
    private val baseDataSource: DefaultHttpDataSource,
    private val channelCookiesMap: Map<String, String>
) : HttpDataSource by baseDataSource {

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val url = dataSpec.uri.toString()
        var cookies: String? = channelCookiesMap[url]
        if (cookies.isNullOrBlank()) {
            cookies = channelCookiesMap.entries.firstOrNull { (key, _) ->
                val host1 = try { java.net.URL(url).host } catch (e: Exception) { null }
                val host2 = try { java.net.URL(key).host } catch (e: Exception) { null }
                !host1.isNullOrBlank() && !host2.isNullOrBlank() && host1 == host2
            }?.value
        }
        if (!cookies.isNullOrBlank()) {
            Log.d(TAG, "Adding cookies for: ${url.take(60)}...")
            try {
                baseDataSource.setRequestProperty("Cookie", cookies)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set cookie header: ${e.message}", e)
            }
        }
        return baseDataSource.open(dataSpec)
    }
}

/**
 * Factory for creating CookieAwareHttpDataSource instances.
 */
class CookieAwareHttpDataSourceFactory(
    private val baseFactory: DefaultHttpDataSource.Factory,
    private val channelCookiesMap: Map<String, String>
) : HttpDataSource.Factory {

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
        baseFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }

    override fun createDataSource(): HttpDataSource {
        val baseDataSource = baseFactory.createDataSource()
        return CookieAwareHttpDataSource(baseDataSource, channelCookiesMap)
    }
}

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
    val liveTvRepo = remember { LiveTVRepository }

    // Lovetier token manager for auto-refreshing tokens
    val tokenManager = remember { LovetierTokenManager() }

    val headers = remember {
        mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://thesports.today/",
            "Origin" to "https://thesports.today"
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
                val list: List<Map<String, String>> = mapper.readValue(decoded, object : TypeReference<List<Map<String, String>>>() {})
                list.map {
                    val rawLogo = it["logo"] ?: it["tvg-logo"] ?: it["tvgLogo"] ?: it["logo_url"] ?: it["logoUrl"] ?: it["Logo"] ?: it["image"] ?: ""
                    val resolvedLogo = resolveLogoUrl(rawLogo)
                    val cookies = it["cookies"] ?: ""  // Get cookies if available
                    Log.d(TAG, "Channel '${it["title"]}' logo: $resolvedLogo, cookies: ${cookies.take(30)}...")
                    LiveChannelItem(
                        url = it["url"] ?: "",
                        title = it["title"] ?: "",
                        id = it["id"] ?: "",
                        logo = resolvedLogo,
                        cookies = cookies,
                        sourceId = it["sourceId"] ?: "",
                        licenseType = it["licenseType"] ?: "",
                        licenseKey = it["licenseKey"] ?: ""
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

    // State to hold channels with potentially updated tokens
    var channelsWithRealTokens by remember { mutableStateOf<List<LiveChannelItem>>(channels) }

    // Fetch real tokens for Lovetier channels with placeholder tokens
    LaunchedEffect(channels) {
        val updatedChannels = channels.mapIndexed { index, channel ->
            if (tokenManager.isLovetierStream(channel.url)) {
                val currentToken = tokenManager.extractToken(channel.url)
                val channelId = tokenManager.extractChannelId(channel.url)

                // Check if token is placeholder or needs refresh
                // Detects: "PLACEHOLDER_TOKEN", "placeholder", or base64 tokens with "no_check_ip"
                val needsRealToken = currentToken == null ||
                    currentToken == "PLACEHOLDER_TOKEN" ||
                    currentToken.contains("placeholder") ||
                    currentToken.contains("PLACEHOLDER") ||
                    currentToken.contains("no_check_ip") ||  // Old placeholder format
                    currentToken.contains(".placeholder")  // Old format with hash

                if (needsRealToken && channelId != null) {
                    Log.d(TAG, "Fetching real token for Lovetier channel: ${channel.title} (ID: $channelId)")
                    Log.d(TAG, "  Current token: ${currentToken?.take(30)}...")
                    val newToken = tokenManager.refreshToken(channelId, currentToken ?: "")
                    if (newToken != null) {
                        val newUrl = tokenManager.updateTokenInUrl(channel.url, newToken)
                        Log.d(TAG, "✓ Got real token for ${channel.title}: ${newToken.take(30)}...")
                        channel.copy(url = newUrl)
                    } else {
                        Log.w(TAG, "✗ Failed to get token for ${channel.title}, keeping placeholder")
                        channel
                    }
                } else {
                    channel
                }
            } else {
                channel
            }
        }
        channelsWithRealTokens = updatedChannels
        Log.d(TAG, "Token pre-fetch completed. Total channels: ${updatedChannels.size}")
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
    var isBuffering by remember { mutableStateOf(false) }
    var bufferingCounter by remember { mutableStateOf(0) }
    val failedChannels = remember { mutableSetOf<Int>() }
    val ioErrorRetries = remember { mutableMapOf<Int, Int>() }
    val refreshRetries = remember { mutableMapOf<Int, Int>() }
    var currentScaleMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isInPipMode by remember { mutableStateOf(false) }
    var isEnteringPip by remember { mutableStateOf(false) }

    var totalDragX by remember { mutableStateOf(0f) }
    val swipeThreshold = 150f

    var focusRow by remember { mutableIntStateOf(1) }
    var topFocusIdx by remember { mutableIntStateOf(0) }
    var bottomFocusIdx by remember { mutableIntStateOf(startIndex) }

    // Quality selection state
    data class QualityOption(val label: String, val height: Int = -1, val groupIndex: Int = -1, val trackIndex: Int = -1)
    var qualityOptions by remember { mutableStateOf(listOf<QualityOption>()) }
    var currentQualityLabel by remember { mutableStateOf("Auto") }
    var openDropdown by remember { mutableStateOf<String?>(null) }
    var dropdownFocusIdx by remember { mutableIntStateOf(0) }
    var rememberedQualityHeight by remember { mutableIntStateOf(-1) }
    var rememberedQualityOption by remember { mutableStateOf<QualityOption?>(null) }

    val topBarItems = remember(supportsPip) {
        buildList {
            add("back")
            add("quality")
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
            // Don't auto-hide if dropdown is open
            if (openDropdown != null) {
                return@launch
            }
            isControlsVisible = false
            openDropdown = null
        }
    }

    fun showControlsPermanently() {
        hideJob?.cancel()
        isControlsVisible = true
    }

    fun showControlsTemporarily() {
        openDropdown = null
        dropdownFocusIdx = 0
        focusRow = 1
        bottomFocusIdx = currentIndex
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

    /** Clear PIP auto-enter params so other screens don't accidentally trigger PIP. */
    fun clearPipAutoEnter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build()
                )
                Log.d(TAG, "PIP auto-enter disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear PIP params: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun navigateBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val act = activity
            if (act != null && act.isInPictureInPictureMode) {
                Log.d(TAG, "In PIP mode - moving activity to background")
                // Move to background instead of navigating - PIP stays active
                act.moveTaskToBack(true)
                return
            }
        }
        // Disable PIP auto-enter before leaving this screen
        clearPipAutoEnter()
        // Pause playback
        exoPlayerState?.pause()
        exoPlayerState?.playWhenReady = false
        navController.popBackStack()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun exitPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val act = activity ?: return
            if (act.isInPictureInPictureMode) {
                Log.d(TAG, "PIP mode active - moving activity to background")
                // Move activity to background to keep PIP window active
                act.moveTaskToBack(true)
                isInPipMode = false
                isEnteringPip = false
            }
        }
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

                // Set flag to prevent lifecycle from pausing playback
                isEnteringPip = true

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
                    isEnteringPip = false
                    // Restore fullscreen if PIP failed
                    act.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "PIP not supported by activity: ${e.message}")
                showInfoTemporarily("PIP not available")
                isEnteringPip = false
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter PIP mode: ${e.message}", e)
                showInfoTemporarily("PIP error")
                isEnteringPip = false
                // Try to restore fullscreen on error
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    // Silent channel change (no UI controls)
    fun changeChannelSilent(index: Int) {
        if (index in channelsWithRealTokens.indices) {
            failedChannels.clear()
            ioErrorRetries.clear()
            refreshRetries.clear()
            currentIndex = index
            bottomFocusIdx = index
            currentTitle = channelsWithRealTokens[index].title
            exoPlayerState?.seekToDefaultPosition(index)
            exoPlayerState?.prepare()
            exoPlayerState?.playWhenReady = true
            exoPlayerState?.play()
            showInfoTemporarily("Channel: ${channelsWithRealTokens[index].title}")
        }
    }

    fun selectChannelFromGuide(index: Int) {
        if (index in channelsWithRealTokens.indices) {
            changeChannelSilent(index)
            // Immediately hide controls if they were visible
            if (isControlsVisible) {
                isControlsVisible = false
                hideJob?.cancel()  // also cancel any pending auto-hide
            }
        }
    }

    fun switchToNextChannel() {
        if (channelsWithRealTokens.isNotEmpty()) {
            val nextIndex = (currentIndex + 1) % channelsWithRealTokens.size
            changeChannelSilent(nextIndex)
        }
    }

    fun switchToPreviousChannel() {
        if (channelsWithRealTokens.isNotEmpty()) {
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else channelsWithRealTokens.size - 1
            changeChannelSilent(prevIndex)
        }
    }

    fun skipToNextAvailableChannel(failedName: String) {
        var nextIndex = (currentIndex + 1) % channelsWithRealTokens.size
        var attempts = 0
        while (nextIndex in failedChannels && attempts < channelsWithRealTokens.size) {
            nextIndex = (nextIndex + 1) % channelsWithRealTokens.size
            attempts++
        }
        if (attempts < channelsWithRealTokens.size) {
            changeChannelSilent(nextIndex)
            showInfoTemporarily("Switching channel...")
        } else {
            showInfoTemporarily("All channels unavailable")
            resetAutoHideTimer()
        }
    }

    // Quality helper functions (ordered: callees first, then callers)
    fun buildQualityLabel(height: Int): String = when {
        height <= 0 -> "Auto"
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        height >= 360 -> "360p"
        height >= 240 -> "240p"
        else -> "${height}p"
    }

    fun detectAvailableQualities(player: ExoPlayer) {
        val tracks = player.currentTracks
        val options = mutableListOf<QualityOption>()
        options.add(QualityOption("Auto", -1))
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO && group.length > 0) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    val h = format.height
                    if (h > 0) {
                        val label = buildQualityLabel(h)
                        if (options.none { it.height == h }) {
                            options.add(QualityOption(label, h, groupIndex = tracks.groups.indexOf(group), trackIndex = i))
                        }
                    }
                }
            }
        }
        qualityOptions = options
    }

    fun setQuality(option: QualityOption) {
        val player = exoPlayerState ?: return
        if (option.height == -1) {
            // Auto mode
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            rememberedQualityHeight = -1
            rememberedQualityOption = null
            Log.d(TAG, "Quality set to Auto")
        } else {
            val tracks = player.currentTracks
            val group = tracks.groups.getOrNull(option.groupIndex) ?: return
            val mediaTrackGroup = group.mediaTrackGroup
            val override = TrackSelectionOverride(mediaTrackGroup, listOf(option.trackIndex))
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(override)
                .build()
            rememberedQualityHeight = option.height
            rememberedQualityOption = option
            Log.d(TAG, "Quality set to ${option.label} (h=${option.height}, g=${option.groupIndex}, t=${option.trackIndex})")
        }
        currentQualityLabel = option.label
        openDropdown = null
        focusRow = 0
    }

    fun applyRememberedQuality(player: ExoPlayer) {
        val option = rememberedQualityOption
        if (option != null && option.height > 0) {
            scope.launch {
                delay(500)
                try {
                    val tracks = player.currentTracks
                    val group = tracks.groups.getOrNull(option.groupIndex)
                    if (group != null && group.length > option.trackIndex) {
                        val mediaTrackGroup = group.mediaTrackGroup
                        val override = TrackSelectionOverride(mediaTrackGroup, listOf(option.trackIndex))
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(override)
                            .build()
                        Log.d(TAG, "Applied remembered quality: ${option.label}")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not apply remembered quality: ${e.message}")
                }
            }
        }
    }

    fun logActiveVideoTrack(player: ExoPlayer) {
        val tracks = player.currentTracks
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (group.isTrackSelected(i)) {
                        Log.d(TAG, "Active video: ${format.width}x${format.height}, codec=${format.sampleMimeType}")
                        currentQualityLabel = buildQualityLabel(format.height)
                        return
                    }
                }
            }
        }
    }

    fun activateFocusedControl() {
        when (focusRow) {
            0 -> when (topBarItems.getOrNull(topFocusIdx)) {
                "back" -> navigateBack()
                "quality" -> {
                    openDropdown = "quality"
                    dropdownFocusIdx = 0
                    focusRow = 1
                }
                "pip" -> enterPictureInPictureMode()
                "scale" -> cycleScaleMode()
            }
            1 -> {
                if (bottomFocusIdx in channelsWithRealTokens.indices) {
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
                    Log.d(TAG, "BACK-DEFENSE LAYER1-CONSUMER: navigateBack() from key event channel")
                    navigateBack()
                    return true
                }
            }
            return false
        }

        // Only reset auto-hide timer — do NOT reset focusRow or close dropdown here.
        // The original code called showControlsTemporarily() which resets focusRow to 0,
        // breaking LEFT/RIGHT navigation on the bottom channel guide bar.
        hideJob?.cancel()
        isControlsVisible = true
        resetAutoHideTimer()

        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                if (focusRow == 1 && openDropdown == "quality") {
                    if (dropdownFocusIdx > 0) {
                        dropdownFocusIdx--
                    } else {
                        openDropdown = null
                        focusRow = 0
                    }
                } else if (focusRow == 1) {
                    focusRow = 0
                    topFocusIdx = 0
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focusRow == 0) {
                    if (openDropdown == "quality") {
                        // Already in dropdown
                    } else {
                        focusRow = 1
                        bottomFocusIdx = currentIndex
                    }
                } else if (focusRow == 1 && openDropdown == "quality") {
                    if (dropdownFocusIdx < qualityOptions.size - 1) dropdownFocusIdx++
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                // Block channel navigation while quality dropdown is open
                if (openDropdown != null) return true
                when (focusRow) {
                    0 -> topFocusIdx = if (topFocusIdx > 0) topFocusIdx - 1 else topBarItems.size - 1
                    1 -> bottomFocusIdx = if (bottomFocusIdx > 0) bottomFocusIdx - 1 else channelsWithRealTokens.size - 1
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Block channel navigation while quality dropdown is open
                if (openDropdown != null) return true
                when (focusRow) {
                    0 -> topFocusIdx = (topFocusIdx + 1) % topBarItems.size
                    1 -> bottomFocusIdx = (bottomFocusIdx + 1) % channelsWithRealTokens.size
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                if (focusRow == 1 && openDropdown == "quality" && dropdownFocusIdx in qualityOptions.indices) {
                    setQuality(qualityOptions[dropdownFocusIdx])
                } else {
                    activateFocusedControl()
                }
                return true
            }
            AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                if (openDropdown != null) {
                    openDropdown = null
                    focusRow = 0
                    return true
                }
                Log.d(TAG, "BACK-DEFENSE LAYER1-CONSUMER: hiding controls (controls visible, no dropdown)")
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

    // Create a map of channel URLs to cookies for authenticated streams
    val channelCookiesMap = remember(channelsWithRealTokens) {
        Log.d(TAG, "Creating cookie map for ${channelsWithRealTokens.size} channels")
        val map = mutableMapOf<String, String>()
        channelsWithRealTokens.forEach { channel ->
            Log.d(TAG, "  Mapping: ${channel.url} -> cookies (length: ${channel.cookies.length})")
            if (channel.cookies.isNotBlank()) {
                map[channel.url] = channel.cookies
            }
            // Store sourceId keyed by channel id for refresh routing
            val sourceId = channel.sourceId
            if (sourceId.isNotBlank()) {
                map["${channel.id}_sourceId"] = sourceId
            }
        }
        Log.d(TAG, "Cookie map created with ${map.size} entries")
        map.toMap()
    }

    // Bandwidth meter — essential for ABR (Adaptive Bitrate) quality auto-switching.
    // Without this, AdaptiveTrackSelection has zero throughput data and cannot
    // decide when to switch up/down. This instance measures real network bandwidth
    // from every segment download.
    val bandwidthMeter = remember { DefaultBandwidthMeter.getSingletonInstance(context) }

    val baseHttpDataSourceFactory = remember(headers) {
        DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: "Mozilla/5.0")
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
            .setTransferListener(bandwidthMeter)  // Feed throughput data to ABR
    }

    val httpDataSourceFactory = remember(baseHttpDataSourceFactory, channelCookiesMap) {
        CookieAwareHttpDataSourceFactory(baseHttpDataSourceFactory, channelCookiesMap)
    }

    // Optimized buffer configuration for live streaming
    // Tuned to minimize rebuffering on unstable connections:
    // - Large minBuffer to keep data ahead
    // - Aggressive live edge target to stay close to head
    // - Fast initial start but reasonable rebuffer recovery
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000,   // minBufferMs (50s) — keep a large buffer ahead to absorb network dips
                120000,  // maxBufferMs (120s) — generous cap for consistent playback
                1000,    // bufferForPlaybackMs (1s) — start playing quickly
                3000     // bufferForPlaybackAfterRebufferMs (3s) — buffer more after rebuffer to prevent rapid re-stall
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setBackBuffer(30000, true) // 30s back buffer for seeking
            .build()
    }

    // Track selector with default AdaptiveTrackSelection.Factory — enables ABR
    // quality auto-switching. The bandwidth meter (attached to DataSource.Factory
    // above) feeds real throughput data so the selector can adapt quality.
    val trackSelector = remember { DefaultTrackSelector(context) }

    val mediaItems = remember(channelsWithRealTokens) {
        channelsWithRealTokens.map { channel ->
            buildMediaItemForChannel(channel)
        }
    }

    // Media source factory for HTTP streaming + DASH + ClearKey DRM
    val mediaSourceFactory = remember(httpDataSourceFactory, channelsWithRealTokens) {
        val clearKeyChannels = channelsWithRealTokens.filter {
            it.licenseType.equals("clearkey", ignoreCase = true) && it.licenseKey.isNotBlank()
        }

        // Build ClearKey license JSON + first KID for manifest injection
        var clearKeyJson: String? = null
        var firstKidB64: String? = null
        var drmManager: DrmSessionManager? = null
        if (clearKeyChannels.isNotEmpty()) {
            try {
                val keysJson = clearKeyChannels.mapNotNull { ch ->
                    val parts = ch.licenseKey.split(":")
                    if (parts.size == 2) {
                        val kidB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hexToBytes(parts[0]))
                        val keyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hexToBytes(parts[1]))
                        """{"kty":"oct","kid":"$kidB64","k":"$keyB64"}"""
                    } else null
                }.joinToString(",")
                clearKeyJson = """{"keys":[$keysJson],"type":"temporary"}"""
                Log.d(TAG, "ClearKey DRM: ${clearKeyChannels.size} keys")
                Log.d(TAG, "ClearKey JSON (first 200 chars): ${clearKeyJson!!.take(200)}")

                // First KID — injected into DASH manifest so MpdParser creates DrmInitData
                val firstKidHex = clearKeyChannels.first().licenseKey.split(":").first()
                firstKidB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hexToBytes(firstKidHex))
                Log.d(TAG, "Manifest injection KID (base64url): $firstKidB64")

                val mediaDrm = FrameworkMediaDrm.newInstance(C.CLEARKEY_UUID)
                drmManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID) { mediaDrm }
                    .build(LocalMediaDrmCallback(clearKeyJson!!.toByteArray()))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup ClearKey DRM: ${e.message}")
            }
        }

        val kid = firstKidB64
        // Wrap the DataSource factory to intercept DASH manifests and inject
        // cenc:default_KID into <ContentProtection> elements. MpdParser ignores
        // ContentProtection without this attribute, so no DrmInitData is created
        // and the DRM session is never opened → black screen, no audio.
        val dsFactory: DataSource.Factory = if (kid != null) {
            object : DataSource.Factory {
                override fun createDataSource(): DataSource {
                    val base = httpDataSourceFactory.createDataSource()
                    if (base !is HttpDataSource) return base
                    return object : HttpDataSource by base {
                        private var modifiedBytes: ByteArray? = null
                        private var modifiedPos = 0
                        private var isManifest = false

                        override fun open(dataSpec: DataSpec): Long {
                            val uri = dataSpec.uri.toString()
                            isManifest = uri.endsWith(".mpd") || uri.contains(".mpd?")
                            if (!isManifest) return base.open(dataSpec)

                            // Read entire manifest into memory
                            val contentLength = base.open(dataSpec)
                            val buf = ByteArray(contentLength.toInt().coerceAtLeast(65536))
                            var totalRead = 0
                            while (totalRead < buf.size) {
                                val n = base.read(buf, totalRead, buf.size - totalRead)
                                if (n <= 0) break
                                totalRead += n
                            }
                            // Do NOT close base here — StatsDataSource wraps us and
                            // reads response headers/URI from the underlying connection.
                            // Closing would null them out and trigger NPE in StatsDataSource.
                            // base.close()  ← causes StatsDataSource NPE

                            // Inject cenc:default_KID into ContentProtection elements
                            val xml = String(buf, 0, totalRead, Charsets.UTF_8)
                            var modified = xml
                            // Ensure cenc namespace is declared on root <MPD>
                            if (!modified.contains("xmlns:cenc=")) {
                                modified = modified.replace(
                                    "<MPD ", "<MPD xmlns:cenc=\"urn:mpeg:cenc:2013\" "
                                )
                            }
                            // Add default_KID to mp4protection ContentProtection
                            modified = modified.replace(
                                """schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc">""",
                                """schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc" cenc:default_KID="$kid">"""
                            )
                            Log.d(TAG, "Injected cenc:default_KID into DASH manifest (${modified.length} bytes)")
                            modifiedBytes = modified.toByteArray(Charsets.UTF_8)
                            modifiedPos = 0
                            return modifiedBytes!!.size.toLong()
                        }

                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            if (modifiedBytes != null) {
                                val remaining = modifiedBytes!!.size - modifiedPos
                                if (remaining <= 0) {
                                    modifiedBytes = null
                                    return -1
                                }
                                val toRead = minOf(length, remaining)
                                System.arraycopy(modifiedBytes!!, modifiedPos, buffer, offset, toRead)
                                modifiedPos += toRead
                                return toRead
                            }
                            return base.read(buffer, offset, length)
                        }

                        override fun close() {
                            modifiedBytes = null
                            modifiedPos = 0
                            isManifest = false
                            base.close()
                        }
                    }
                }
            }
        } else {
            httpDataSourceFactory
        }

        val factory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dsFactory)

        if (drmManager != null) {
            factory.setDrmSessionManagerProvider(object : DrmSessionManagerProvider {
                override fun get(mediaItem: MediaItem): DrmSessionManager {
                    val hasDrm = mediaItem.localConfiguration?.drmConfiguration != null
                    return if (hasDrm) drmManager!! else DrmSessionManager.DRM_UNSUPPORTED
                }
            })
        }

        factory
    }

    // Renderer factory with FFmpeg extension for MP2/MPEG audio support
    // EXTENSION_RENDERER_MODE_ON: Use FFmpeg software decoder when hardware doesn't support a codec (e.g., MP2)
    val renderersFactory = remember {
        val ffmpegAvailable = FfmpegLibrary.isAvailable()
        if (ffmpegAvailable) {
            Log.d(TAG, "✓ FFmpeg library loaded successfully (version: ${FfmpegLibrary.getVersion()})")
        } else {
            Log.e(TAG, "✗ FFmpeg library NOT available — MP2/AC3/DTS audio will NOT play. Check libffmpegJNI.so in jniLibs/")
        }
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                if (mediaItems.isNotEmpty()) {
                    val safeStartIndex = startIndex.coerceIn(0, mediaItems.size - 1)
                    setMediaItems(mediaItems, safeStartIndex, C.TIME_UNSET)
                }
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
                play()
            }.also {
                exoPlayerState = it
                Log.d(TAG, "Player created with FFmpeg renderer enabled + optimized buffers")
                // Listen for DRM errors so they appear in logcat instead of being silent
                it.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val msg = error.message ?: "unknown"
                        val isDrm = msg.contains("drm", ignoreCase = true) ||
                            error.cause?.message?.contains("drm", ignoreCase = true) == true
                        if (isDrm) {
                            Log.e(TAG, "❌ DRM Error: code=${error.errorCodeName}, msg=$msg", error)
                        } else {
                            Log.e(TAG, "❌ Player Error: code=${error.errorCodeName}, msg=$msg", error)
                        }
                    }
                })
            }
    }

    // Set PIP params immediately after player creation and update on orientation changes
    val configuration = LocalConfiguration.current
    LaunchedEffect(exoPlayer, supportsPip, configuration.orientation) {
        if (supportsPip) {
            val aspectRatio = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Rational(16, 9)
            } else {
                Rational(9, 16)
            }
            activity?.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .setAutoEnterEnabled(true)
                    .build()
            )
            Log.d(TAG, "PIP params set (orientation: ${configuration.orientation})")
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(250)
            isPlaying = exoPlayer.isPlaying
            // Track buffering state
            val playbackState = exoPlayer.playbackState
            isBuffering = playbackState == Player.STATE_BUFFERING
            
            // Extended buffering detection - if buffering for too long, reload stream
            if (isBuffering && playbackState == Player.STATE_BUFFERING) {
                bufferingCounter++
                if (bufferingCounter > 40) { // 10 seconds of buffering (250ms * 40)
                    Log.w(TAG, "Extended buffering detected (${bufferingCounter * 250}ms), reloading stream")
                    bufferingCounter = 0
                    try {
                        val channel = channelsWithRealTokens.getOrNull(currentIndex)
                        if (channel != null) {
                            val newMediaItem = buildMediaItemForChannel(channel)
                            exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                            Log.d(TAG, "✓ Stream reloaded successfully after extended buffering")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reloading stream after buffering: ${e.message}", e)
                    }
                }
            } else {
                bufferingCounter = 0
            }
        }
    }

    // Proactive live window monitoring - prevent falling behind before error occurs
    LaunchedEffect(exoPlayer, channelsWithRealTokens) {
        var lastPosition = 0L
        var stuckCounter = 0
        
        while (true) {
            delay(5000) // Check every 5 seconds
            try {
                if (exoPlayer.playbackState == Player.STATE_READY) {
                    val currentPosition = exoPlayer.currentPosition
                    val currentLiveOffsetMs = exoPlayer.currentLiveOffset
                    
                    // Check if player is stuck (position not moving)
                    if (lastPosition > 0 && currentPosition == lastPosition && exoPlayer.isPlaying) {
                        stuckCounter++
                        Log.w(TAG, "Player stuck detection: counter=$stuckCounter")
                        if (stuckCounter >= 3) {
                            // Player stuck for 15+ seconds, reset to live edge
                            Log.w(TAG, "Player appears stuck, resetting to live edge")
                            exoPlayer.seekToDefaultPosition()
                            stuckCounter = 0
                        }
                    } else {
                        stuckCounter = 0
                        lastPosition = currentPosition
                    }
                    
                    // Check live offset and proactively seek
                    if (currentLiveOffsetMs != C.TIME_UNSET) {
                        if (currentLiveOffsetMs > 35000) {
                            // If more than 35 seconds behind live edge, proactively seek to catch up
                            val duration = exoPlayer.duration
                            if (duration != C.TIME_UNSET) {
                                val targetPosition = duration - 20000 // Seek to 20s behind live
                                if (targetPosition > currentPosition) {
                                    Log.w(TAG, "Proactive catch-up: player is ${currentLiveOffsetMs / 1000}s behind, seeking to 20s behind live")
                                    exoPlayer.seekTo(targetPosition)
                                }
                            }
                        }
                    } else {
                        // Can't determine live offset - check if playing but not advancing
                        if (exoPlayer.isPlaying && exoPlayer.duration != C.TIME_UNSET) {
                            // Seek to a safe position (25s from end)
                            val safePosition = maxOf(0L, exoPlayer.duration - 25000)
                            if (safePosition > currentPosition + 10000) {
                                Log.w(TAG, "Live offset unknown, seeking to safe position: ${safePosition}ms")
                                exoPlayer.seekTo(safePosition)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error during proactive live window check: ${e.message}")
            }
        }
    }

    // Lifecycle-aware player control - pause when activity goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (exoPlayer.isPlaying) {
                        // Don't pause if we're entering PIP mode
                        if (isEnteringPip) {
                            Log.d(TAG, "Activity paused/stopped - entering PIP, keeping playback")
                            isEnteringPip = false // Reset the flag
                            return@LifecycleEventObserver
                        }

                        // Don't pause if already in PIP mode
                        if (isInPipMode) {
                            Log.d(TAG, "Activity paused/stopped - already in PIP, keeping playback")
                            return@LifecycleEventObserver
                        }

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
                    // Check if we're exiting PIP
                    if (isInPipMode) {
                        Log.d(TAG, "Exiting PIP mode")
                        isInPipMode = false
                        isEnteringPip = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ========== Layer 3: Direct Activity back handler ==========
    // Register navigateBack directly with MainActivity.companion.directBackHandler.
    // This is the most reliable path — it bypasses OnBackPressedDispatcher and
    // BackHandler composables entirely, going straight from Activity.onBackPressed()
    // to navigateBack(). Critical for TV firmware where back dispatch is broken.
    DisposableEffect(Unit) {
        Log.d(TAG, "BACK-DEFENSE: Registering directBackHandler with MainActivity")
        val backHandler = { navigateBack() }
        MainActivity.directBackHandler = backHandler
        onDispose {
            // Only clear if WE are still the owner (not replaced by another player)
            if (MainActivity.directBackHandler === backHandler) {
                Log.d(TAG, "BACK-DEFENSE: Clearing directBackHandler (we are still the owner)")
                MainActivity.directBackHandler = null
            } else {
                Log.d(TAG, "BACK-DEFENSE: NOT clearing directBackHandler (another player owns it)")
            }
        }
    }

    // Stop playback when navigating away from screen
    // Layer 2 safety net: BackHandler composable for OnBackPressedDispatcher path.
    BackHandler(enabled = true) {
        Log.d(TAG, "BACK-DEFENSE: BackHandler composable fired")
        navigateBack()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (channelsWithRealTokens.isNotEmpty() && newIndex != currentIndex) {
                    val newChannel = channelsWithRealTokens.getOrNull(newIndex)
                    currentIndex = newIndex
                    bottomFocusIdx = newIndex
                    currentTitle = newChannel?.title ?: ""

                    // Handle Lovetier token refresh for new channel
                    newChannel?.let { channel ->
                        if (tokenManager.isLovetierStream(channel.url)) {
                            Log.d(TAG, "New channel is Lovetier stream: ${channel.title}")
                            val channelId = tokenManager.extractChannelId(channel.url)
                            val currentToken = tokenManager.extractToken(channel.url)

                            if (channelId != null && currentToken != null) {
                                Log.d(TAG, "Starting token refresh for: $channelId")
                                tokenManager.startTokenRefresh(
                                    channelId = channelId,
                                    currentToken = currentToken,
                                    onTokenRefreshed = { newToken ->
                                        Log.d(TAG, "Token refreshed for $channelId, updating stream URL")
                                        scope.launch {
                                            try {
                                                val newUrl = tokenManager.updateTokenInUrl(channel.url, newToken)
                                                Log.d(TAG, "Replacing media item with new URL: $newUrl")

                                                val newMediaItem = buildMediaItemForChannel(channel.copy(url = newUrl))

                                                // Update the channel in our list with new URL
                                                val updatedChannels = channelsWithRealTokens.toMutableList()
                                                updatedChannels[newIndex] = channel.copy(url = newUrl)
                                                channelsWithRealTokens = updatedChannels

                                                // Update player's media item seamlessly
                                                val currentPosition = exoPlayer.currentPosition
                                                exoPlayer.replaceMediaItem(newIndex, newMediaItem)
                                                exoPlayer.seekTo(newIndex, currentPosition)

                                                Log.d(TAG, "✓ Stream URL updated seamlessly for $channelId")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error updating stream URL: ${e.message}", e)
                                            }
                                        }
                                    },
                                    scope = scope
                                )
                            } else {
                                Log.w(TAG, "Could not extract channel ID or token from URL")
                            }
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Log all track groups for audio format diagnostics (MP2 detection)
                var mp2Detected = false
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            val format = group.getTrackFormat(i)
                            val mimeType = format.sampleMimeType ?: "unknown"
                            Log.d(TAG, "[Audio Track] mime=$mimeType, sampleRate=${format.sampleRate}, channels=${format.channelCount}")
                            if (mimeType.contains("mp2") || mimeType.contains("mpeg-L2") || mimeType.contains("audio/mpeg")) {
                                mp2Detected = true
                            }
                        } else if (group.type == C.TRACK_TYPE_VIDEO) {
                            val format = group.getTrackFormat(i)
                            Log.d(TAG, "[Video Track] ${format.width}x${format.height}, bitrate=${format.bitrate}, codec=${format.sampleMimeType}")
                        }
                    }
                }
                if (mp2Detected) {
                    val ffmpegOk = FfmpegLibrary.isAvailable()
                    if (ffmpegOk) {
                        Log.d(TAG, "MP2 audio: FFmpeg decoder is available — audio SHOULD work (version ${FfmpegLibrary.getVersion()})")
                        Log.d(TAG, "MP2 audio: FfmpegLibrary.supportsFormat(audio/mpeg-L2) = ${FfmpegLibrary.supportsFormat("audio/mpeg-L2")}")
                    } else {
                        Log.e(TAG, "MP2 audio: FFmpeg library NOT loaded — audio will NOT play!")
                    }
                }
                // Detect and update quality options
                detectAvailableQualities(exoPlayer)
                // Apply remembered quality after track change
                applyRememberedQuality(exoPlayer)
                // Log active video track
                logActiveVideoTrack(exoPlayer)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    failedChannels.clear()
                    ioErrorRetries.clear()
                    refreshRetries.clear()
                    resetAutoHideTimer()
                    logActiveVideoTrack(exoPlayer)
                    val bufferedMs = if (exoPlayer.duration != C.TIME_UNSET) {
                        (exoPlayer.bufferedPercentage / 100.0 * exoPlayer.duration).toLong()
                    } else {
                        // For live streams (duration=TIME_UNSET), use buffered percentage
                        // of a 30s window as a rough indicator
                        "${exoPlayer.bufferedPercentage}% (live)"
                    }
                    Log.d(TAG, "Player ready - buffered: ${bufferedMs}ms (${exoPlayer.bufferedPercentage}%)")
                } else if (state == Player.STATE_BUFFERING) {
                    Log.d(TAG, "Player buffering - buffered: ${exoPlayer.bufferedPercentage}%")
                } else if (state == Player.STATE_ENDED) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.play()
                }
            }

            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                Log.d(TAG, "Playing state changed: $isPlayingValue, buffering: ${!isPlayingValue && exoPlayer.playbackState == Player.STATE_BUFFERING}")
            }

            @RequiresApi(Build.VERSION_CODES.N)
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val errorCode = error.errorCode
                val channelName = channelsWithRealTokens.getOrNull(currentIndex)?.title ?: "Unknown"
                val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0

                // Handle BehindLiveWindowException - player fell behind available segments
                if (error.errorCodeName == "BEHIND_LIVE_WINDOW" ||
                    error.cause?.javaClass?.simpleName == "BehindLiveWindowException") {
                    Log.w(TAG, "Player behind live window, reloading stream")
                    scope.launch {
                        delay(500)
                        try {
                            val channel = channelsWithRealTokens.getOrNull(currentIndex)
                            if (channel != null) {
                                val newMediaItem = buildMediaItemForChannel(channel)
                                exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                Log.d(TAG, "✓ Stream reloaded successfully after behind live window")
                            } else {
                                Log.e(TAG, "Channel not found for index $currentIndex")
                                skipToNextAvailableChannel(channelName)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reloading stream: ${e.message}", e)
                            failedChannels.add(currentIndex)
                            skipToNextAvailableChannel(channelName)
                        }
                    }
                    return
                }

                // Handle 404 errors - segments no longer available on server (fell behind live window)
                if (responseCode == 404) {
                    Log.w(TAG, "404 Not Found - segment no longer available, reloading stream")
                    scope.launch {
                        delay(500)
                        try {
                            val channel = channelsWithRealTokens.getOrNull(currentIndex)
                            if (channel != null) {
                                val newMediaItem = buildMediaItemForChannel(channel)
                                exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                Log.d(TAG, "✓ Stream reloaded successfully after 404")
                            } else {
                                Log.e(TAG, "Channel not found for index $currentIndex")
                                skipToNextAvailableChannel(channelName)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reloading stream after 404: ${e.message}", e)
                            failedChannels.add(currentIndex)
                            skipToNextAvailableChannel(channelName)
                        }
                    }
                    return
                }

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
                            val channel = channelsWithRealTokens.getOrNull(currentIndex)
                            if (channel != null) {
                                val newMediaItem = buildMediaItemForChannel(channel)
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

                if (errorCode == 403) {
                    val channel = channelsWithRealTokens.getOrNull(currentIndex)
                    val channelId = channel?.id ?: ""
                    val channelCookies = channel?.cookies ?: ""

                    Log.e(TAG, "403 Forbidden for channel: $channelName, has cookies: ${channelCookies.isNotEmpty()}, cookies length: ${channelCookies.length}")

                    // Check if this is an authenticated stream with cookies
                    if (channelCookies.isNotEmpty()) {
                        Log.e(TAG, "This is an authenticated stream with cookies. Checking cookie map...")

                        // Check if cookies are in the cookie map
                        val cookiesInMap = channelCookiesMap[channel?.url]
                        Log.e(TAG, "Cookies in map: ${cookiesInMap?.length ?: 0}")

                        if (cookiesInMap != null && cookiesInMap.isNotEmpty()) {
                            Log.e(TAG, "Cookies found in map. Retrying with same URL (cookies should work).")
                            // For authenticated streams, retry with same URL without refreshing
                            // The cookies should work if they're properly set in CookieAwareHttpDataSource
                            if (refreshRetries.getOrDefault(currentIndex, 0) < 1) {
                                refreshRetries[currentIndex] = refreshRetries.getOrDefault(currentIndex, 0) + 1
                                scope.launch {
                                    delay(1000)
                                    try {
                                        if (channel != null) {
                                            val newMediaItem = buildMediaItemForChannel(channel)
                                            exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                            exoPlayer.prepare()
                                            exoPlayer.playWhenReady = true
                                            exoPlayer.play()
                                        }
                                        Log.d(TAG, "Retried authenticated stream")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Retry failed: ${e.message}", e)
                                        failedChannels.add(currentIndex)
                                        skipToNextAvailableChannel(channelName)
                                    }
                                }
                            } else {
                                failedChannels.add(currentIndex)
                                skipToNextAvailableChannel(channelName)
                            }
                            return
                        } else {
                            Log.e(TAG, "Cookies NOT found in cookie map for URL: ${channel?.url}")
                        }
                    }

                    // For non-authenticated streams or streams without cookies in map, try to refresh
                    if (channelId.isNotBlank() && refreshRetries.getOrDefault(currentIndex, 0) < 2) {
                        refreshRetries[currentIndex] = refreshRetries.getOrDefault(currentIndex, 0) + 1
                        Log.d(TAG, "Attempting to refresh stream URL via LiveTVRepository...")
                        showInfoTemporarily("Refreshing stream...")
                        scope.launch {
                            val sourceId = channelCookiesMap["${channelId}_sourceId"] ?: ""
                            val freshUrl = withTimeoutOrNull(10000L) {
                                liveTvRepo.refreshStreamUrl(channelId, channelName, sourceId)
                            }
                            if (!freshUrl.isNullOrBlank()) {
                                Log.d(TAG, "Got fresh URL: $freshUrl")
                                val channel = channelsWithRealTokens.getOrNull(currentIndex)
                                val newMediaItem = if (channel != null) {
                                    buildMediaItemForChannel(channel.copy(url = freshUrl))
                                } else {
                                    buildMediaItemForChannel(LiveChannelItem(url = freshUrl, title = channelName))
                                }
                                exoPlayer.replaceMediaItem(currentIndex, newMediaItem)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            } else {
                                Log.e(TAG, "Failed to get fresh URL from LiveTVRepository")
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
            // Stop playback and release on a coroutine to avoid "Handler on dead thread" warnings
            // from MediaCodec's native event thread firing after release
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing player: ${e.message}")
            }
            // Stop all token refresh jobs
            tokenManager.stopAll()
        }
    }

    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        onDispose {
            // Clear PIP auto-enter so it doesn't leak to other screens
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    activity?.setPictureInPictureParams(
                        PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(false)
                            .build()
                    )
                } catch (_: Exception) {}
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
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
        val newCallback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.keyCode in handledKeys && event.action == AndroidKeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "BACK-DEFENSE LAYER1: WindowCallback intercepted keyCode=${event.keyCode}")
                    keyChannel.trySend(event.keyCode)
                    return true
                }
                return originalCallback.dispatchKeyEvent(event)
            }
        }
        window.callback = newCallback
        onDispose {
            // Only restore if WE are still the owner (not replaced by another player)
            if (window.callback === newCallback) {
                window.callback = originalCallback
            }
            keyChannel.close()
        }
    }

    LaunchedEffect(Unit) { showControlsTemporarily() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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

        // Buffering indicator
        AnimatedVisibility(
            visible = isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.Yellow.copy(alpha = 0.7f),
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp
                    )
                    /** Text(
                        text = "Buffering...",
                        color = Color.White,
                        fontSize = 14.sp
                    ) **/
                }
            }
        }

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
                            onClick = { navigateBack() }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(3f)
                        ) {
                            if (channelsWithRealTokens.size > 1) {
                                Text(
                                    "${currentIndex + 1}/${channelsWithRealTokens.size}",
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

                        // Quality button — always show resolution label
                        val qualityIndex = topBarItems.indexOf("quality")
                        LivePlayerButton(
                            isFocused = focusRow == 0 && topFocusIdx == qualityIndex,
                            icon = { Icon(Icons.Filled.Hd, "Quality", tint = Color.White, modifier = Modifier.size(26.dp)) },
                            label = currentQualityLabel,
                            onClick = { openDropdown = "quality"; dropdownFocusIdx = 0; focusRow = 1 }
                        )

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
                            isFocused = focusRow == 0 && topFocusIdx == topBarItems.indexOf("scale"),
                            icon = { Icon(Icons.Filled.ZoomOutMap, "Scale", tint = Color.White, modifier = Modifier.size(26.dp)) },
                            label = "Scale",
                            onClick = { cycleScaleMode() }
                        )
                    }
                }

                // Quality dropdown panel
                if (openDropdown == "quality") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                // Tap outside dropdown → close it
                                openDropdown = null
                                focusRow = 0
                                resetAutoHideTimer()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 60.dp, end = 16.dp)
                                .requiredWidth(300.dp)
                                .heightIn(max = 450.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.5.dp, FocusAccent.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    qualityOptions.forEachIndexed { idx, option ->
                                        DropdownItem(
                                            isFocused = focusRow == 1 && dropdownFocusIdx == idx,
                                            label = option.label,
                                            isSelected = currentQualityLabel == option.label && option.height != -1,
                                            onClick = { setQuality(option) }
                                        )
                                    }
                                }
                            }
                        }
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
                            if (isControlsVisible && channelsWithRealTokens.isNotEmpty() && bottomFocusIdx in channelsWithRealTokens.indices) {
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
                            itemsIndexed(channelsWithRealTokens) { idx, channel ->
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