package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.videolan.libvlc.util.VLCVideoLayout
import com.example.data.model.PlaylistItem
import com.example.data.util.EPGGenerator
import com.example.data.util.Program
import com.example.ui.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R

@Composable
fun VideoPlayerScreen(
    item: PlaylistItem,
    viewModel: IPTVViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize VlcPlayerManager
    val vlcPlayerManager = remember {
        VlcPlayerManager(context)
    }

    // Netflix-style Resume/Restart Popup State
    var showResumeDialog by remember { mutableStateOf(false) }
    var savedProgress by remember { mutableStateOf<com.example.data.model.WatchHistory?>(null) }

    // Context Menus & Dialogs States
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showOutrosDialog by remember { mutableStateOf(false) }

    val opcoesButtonFocusRequester = remember { FocusRequester() }
    val audioButtonFocusRequester = remember { FocusRequester() }
    val subtitleButtonFocusRequester = remember { FocusRequester() }
    val outrosButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val nextEpFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }

    var isBackFocused by remember { mutableStateOf(false) }
    var isOpcoesFocused by remember { mutableStateOf(false) }
    var isNextEpFocused by remember { mutableStateOf(false) }
    var isPlayFocused by remember { mutableStateOf(false) }
    var isAudioFocused by remember { mutableStateOf(false) }
    var isSubtitleFocused by remember { mutableStateOf(false) }
    var isOutrosFocused by remember { mutableStateOf(false) }

    val isAnyControlFocused = isBackFocused || isOpcoesFocused || isNextEpFocused || isPlayFocused || isAudioFocused || isSubtitleFocused || isOutrosFocused

    // Overlay State (Show/Hide Controls)
    var showControls by remember { mutableStateOf(true) }
    val isPlaying = vlcPlayerManager.isPlaying
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasAttemptedFallback by remember { mutableStateOf(false) }

    var seasonsCount by remember { mutableStateOf(1) }
    var episodesCount by remember { mutableStateOf(12) }
    LaunchedEffect(item) {
        if (item.type == "series") {
            try {
                val seasons = viewModel.getRealSeasons(item)
                if (seasons.isNotEmpty()) {
                    seasonsCount = seasons.size
                    episodesCount = seasons.sumOf { it.episodes.size }.coerceAtLeast(1)
                }
            } catch (e: Exception) {
                // fallback
            }
        }
    }

    // Observe error messages from VLC Player Manager
    LaunchedEffect(vlcPlayerManager.errorMessage) {
        val error = vlcPlayerManager.errorMessage
        if (error != null) {
            val isMockUrl = item.url.isBlank() || item.url == "http://streatv.elementfx.com/get.php" || item.url.contains("get.php")
            if (isMockUrl && !hasAttemptedFallback) {
                hasAttemptedFallback = true
                val samples = listOf(
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
                )
                val fallbackUrl = samples[Math.abs(item.id.hashCode()) % samples.size]
                vlcPlayerManager.playUrl(fallbackUrl)
            } else {
                errorMessage = error
            }
        } else {
            errorMessage = null
        }
    }

    // Helper to format playback times beautifully
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Save Progress and exit function
    val saveProgressAndExit = {
        val pos = vlcPlayerManager.currentPosition
        val dur = vlcPlayerManager.duration
        if ((item.type == "movie" || item.type == "series") && dur > 0 && pos > 0) {
            viewModel.saveWatchProgress(item, pos, dur)
        }
        onBack()
    }

    // Set MediaItem and check resume progress
    LaunchedEffect(item.url) {
        val initialUrl = if (item.url.isBlank() || item.url == "http://streatv.elementfx.com/get.php" || item.url.endsWith("/get.php")) {
            val samples = listOf(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
            )
            samples[Math.abs(item.id.hashCode()) % samples.size]
        } else {
            val activeServerUrl = viewModel.getSavedServerUrl()
            val creds = viewModel.getSavedCredentials()
            val user = creds.first ?: ""
            val pass = creds.second ?: ""

            if (activeServerUrl.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                rewriteUrlWithActiveCredentials(item.url, activeServerUrl, user, pass)
            } else {
                item.url
            }
        }

        android.util.Log.d("VideoPlayerScreen", "Preparing playUrl for item: ${item.title}, type: ${item.type}, url: $initialUrl")
        val progress = viewModel.getWatchProgress(item)
        if (progress != null && progress.position > 2000L) { // Only prompt if watched more than 2 seconds
            savedProgress = progress
            showResumeDialog = true
            vlcPlayerManager.playUrl(initialUrl)
            vlcPlayerManager.pause()
            vlcPlayerManager.seekTo(progress.position)
        } else {
            vlcPlayerManager.playUrl(initialUrl)
        }
    }

    // Periodic progress saving for movies/series
    if (item.type == "movie" || item.type == "series") {
        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                delay(5000)
                val pos = vlcPlayerManager.currentPosition
                val dur = vlcPlayerManager.duration
                if (dur > 0 && pos > 0) {
                    viewModel.saveWatchProgress(item, pos, dur)
                }
            }
        }
    }

    // Lifecycle Management
    DisposableEffect(lifecycleOwner, vlcPlayerManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    vlcPlayerManager.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    vlcPlayerManager.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Save on exit
            val pos = vlcPlayerManager.currentPosition
            val dur = vlcPlayerManager.duration
            if ((item.type == "movie" || item.type == "series") && dur > 0 && pos > 0) {
                viewModel.saveWatchProgress(item, pos, dur)
            }
            vlcPlayerManager.release()
        }
    }

    // Auto-hide controls only when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    // Handle Back Button
    BackHandler {
        saveProgressAndExit()
    }

    // TV Remote D-Pad Navigation and Key Handlers
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showResumeDialog) {
        if (!showResumeDialog) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus failures
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (showResumeDialog || showOptionsDialog || showAudioDialog || showSubtitleDialog || showOutrosDialog) {
                    false
                } else if (keyEvent.type == KeyEventType.KeyDown) {
                    showControls = true

                    if (isAnyControlFocused) {
                        // Focus is held by one of the control buttons. Let Compose's D-Pad focus system handle directional arrows!
                        if (keyEvent.key == Key.Back || keyEvent.key == Key.Escape) {
                            saveProgressAndExit()
                            true
                        } else {
                            false
                        }
                    } else {
                        // Focus is on background video surface:
                        when (keyEvent.key) {
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                if (vlcPlayerManager.isPlaying) {
                                    vlcPlayerManager.pause()
                                } else {
                                    vlcPlayerManager.play()
                                }
                                true
                            }
                            Key.Back, Key.Escape -> {
                                saveProgressAndExit()
                                true
                            }
                            Key.DirectionUp -> {
                                try {
                                    opcoesButtonFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                    // Ignore focus error
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                try {
                                    audioButtonFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                    // Ignore focus error
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                val newPos = (vlcPlayerManager.currentPosition - 10000).coerceAtLeast(0)
                                vlcPlayerManager.seekTo(newPos)
                                true
                            }
                            Key.DirectionRight -> {
                                val duration = vlcPlayerManager.duration
                                val newPos = if (duration > 0) {
                                    (vlcPlayerManager.currentPosition + 15000).coerceAtMost(duration)
                                } else {
                                    vlcPlayerManager.currentPosition + 15000
                                }
                                vlcPlayerManager.seekTo(newPos)
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
    ) {
        // Player Surface View using LibVLC
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    vlcPlayerManager.attachVideoLayout(this)
                }
            },
            onRelease = {
                vlcPlayerManager.detachVideoLayout()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay for TV Controls
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                // Header (Back button + Options button + Next Episode button & Title on Top Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Back Button
                        Surface(
                            onClick = saveProgressAndExit,
                            shape = CircleShape,
                            color = if (isBackFocused) Color.White else Color.Transparent,
                            border = if (isBackFocused) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .focusRequester(backButtonFocusRequester)
                                .focusProperties {
                                    right = opcoesButtonFocusRequester
                                    down = playButtonFocusRequester
                                }
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = if (isBackFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Options Button (Icon + "OPTIONS" text below)
                        Surface(
                            onClick = { showOptionsDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOpcoesFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                            border = if (isOpcoesFocused) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .focusRequester(opcoesButtonFocusRequester)
                                .focusProperties {
                                    left = backButtonFocusRequester
                                    right = nextEpFocusRequester
                                    down = playButtonFocusRequester
                                }
                                .onFocusChanged { isOpcoesFocused = it.isFocused }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Opções",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "OPTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Next Episode Button (|▷)
                        Surface(
                            onClick = { /* Next episode action if any */ },
                            shape = CircleShape,
                            color = if (isNextEpFocused) Color.White else Color.Transparent,
                            border = if (isNextEpFocused) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .focusRequester(nextEpFocusRequester)
                                .focusProperties {
                                    left = opcoesButtonFocusRequester
                                    down = playButtonFocusRequester
                                }
                                .onFocusChanged { isNextEpFocused = it.isFocused }
                                .size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "|▷",
                                    color = if (isNextEpFocused) Color.Black else Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Top-Right Title Info
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.groupName.ifEmpty { "S1: E1 \"Pilot\"" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                val isLiveChannel = item.type == "live" ||
                        item.groupName.contains("Canais", ignoreCase = true) ||
                        item.url.contains(".m3u8") ||
                        item.url.contains(".ts")

                if (isLiveChannel) {
                    // Custom Live Channel Overlay (Channel Name + EPG Info)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 48.dp, bottom = 48.dp)
                            .widthIn(max = 650.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Text(
                                        text = "AO VIVO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = item.groupName.ifEmpty { "CANAIS" },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Channel Title (e.g. ESPN FHD)
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        // Divider Line below channel title
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // Channel EPG / Program Schedule List
                        var epgPrograms by remember(item.id) { mutableStateOf<List<com.example.data.util.Program>>(emptyList()) }

                        LaunchedEffect(item.id) {
                            val activeServerUrl = viewModel.getSavedServerUrl()
                            val creds = viewModel.getSavedCredentials()
                            val user = creds.first ?: ""
                            val pass = creds.second ?: ""
                            val server = if (activeServerUrl.isNotBlank()) activeServerUrl else "http://streatv.elementfx.com"
                            val streamId = EPGGenerator.extractStreamId(item)
                            epgPrograms = EPGGenerator.generateEPG(user, pass, streamId, server)
                        }

                        val displayPrograms = if (epgPrograms.isEmpty()) EPGGenerator.fallbackList else epgPrograms

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            displayPrograms.take(7).forEach { prog ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${prog.startTime} - ${prog.endTime}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFFFC107), // Gold/yellow time matching requested design
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.widthIn(min = 190.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = prog.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .background(Color.White.copy(alpha = 0.08f))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Middle-Left Metadata & Synopsis (Netflix TV Style)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 48.dp, bottom = 48.dp)
                            .widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val iconRes = if (item.type == "series") {
                                R.drawable.ic_series
                            } else {
                                R.drawable.ic_filmes
                            }

                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = if (item.type == "series") "Série" else "Filme",
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = if (item.type == "series") "SÉRIE" else "FILME",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Logo image instead of title text if available
                        val titleLogoUrl = item.getEffectiveTitleLogoUrl()
                        if (!titleLogoUrl.isNullOrEmpty()) {
                            LogoAsyncImage(
                                logoUrl = titleLogoUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .height(80.dp)
                                    .widthIn(max = 350.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = item.title.uppercase(),
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Metadata line (Year, Genre, Rating, and Seasons/Episodes if series)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = item.getEffectiveYear(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )

                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.getEffectiveGenre(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "⭐ ${item.getEffectiveRating()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF7BD15),
                                fontWeight = FontWeight.Bold
                            )

                            if (item.type == "series") {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$seasonsCount Temporada${if (seasonsCount > 1) "s" else ""} • $episodesCount Episódios",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Synopsis in place of rating buttons
                        Text(
                            text = item.getEffectiveDescription(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Center Indicator (Play / Pause Status overlay)
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        onClick = {
                            if (vlcPlayerManager.isPlaying) vlcPlayerManager.pause() else vlcPlayerManager.play()
                        },
                        shape = CircleShape,
                        color = if (isPlayFocused) Color.White else Color.Black.copy(alpha = 0.6f),
                        border = if (isPlayFocused) BorderStroke(3.dp, Color.White) else null,
                        modifier = Modifier
                            .focusRequester(playButtonFocusRequester)
                            .focusProperties {
                                up = opcoesButtonFocusRequester
                                down = audioButtonFocusRequester
                                left = backButtonFocusRequester
                                right = nextEpFocusRequester
                            }
                            .onFocusChanged { isPlayFocused = it.isFocused }
                            .size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Tocar/Pausar",
                                tint = if (isPlayFocused) Color.Black else Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                // Footer Stream status bar (Netflix TV Footer Style)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    if (item.type == "movie" || item.type == "series") {
                        val progressFraction = if (vlcPlayerManager.duration > 0) {
                            vlcPlayerManager.currentPosition.toFloat() / vlcPlayerManager.duration.toFloat()
                        } else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (vlcPlayerManager.isPlaying) vlcPlayerManager.pause() else vlcPlayerManager.play()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = formatTime(vlcPlayerManager.currentPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            LinearProgressIndicator(
                                progress = { progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                color = Color(0xFFE50914),
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            Text(
                                text = formatTime(vlcPlayerManager.duration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (vlcPlayerManager.isPlaying) vlcPlayerManager.pause() else vlcPlayerManager.play()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color.Red, shape = MaterialTheme.shapes.small)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AO VIVO",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dynamic track labels calculation
                    val audioTracksList = remember(showAudioDialog, showControls) {
                        vlcPlayerManager.getAudioTracks() ?: emptyArray()
                    }
                    val currentAudioId = vlcPlayerManager.getSelectedAudioTrack()
                    val currentAudioTrackName = audioTracksList.find { it.id == currentAudioId }?.name
                        ?: if (audioTracksList.isNotEmpty()) audioTracksList[0].name else null

                    val audioButtonText = if (!currentAudioTrackName.isNullOrBlank()) {
                        "✓ ${cleanTrackName(currentAudioTrackName)}"
                    } else {
                        "✓ Português [Original]"
                    }

                    val spuTracksList = remember(showSubtitleDialog, showControls) {
                        vlcPlayerManager.getSpuTracks() ?: emptyArray()
                    }
                    val currentSpuId = vlcPlayerManager.getSelectedSpuTrack()
                    val currentSpuTrackName = spuTracksList.find { it.id == currentSpuId }?.name

                    val subtitleButtonText = if (currentSpuId != -1 && !currentSpuTrackName.isNullOrBlank()) {
                        "Legendas [${cleanTrackName(currentSpuTrackName)}]"
                    } else {
                        "Legendas [Desativadas]"
                    }

                    // Bottom pill buttons (Audio, Subtitles, Other)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isLiveChannel) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showAudioDialog = true },
                                    modifier = Modifier
                                        .focusRequester(audioButtonFocusRequester)
                                        .focusProperties {
                                            up = playButtonFocusRequester
                                            right = subtitleButtonFocusRequester
                                        }
                                        .onFocusChanged { isAudioFocused = it.isFocused }
                                        .height(40.dp),
                                    border = if (isAudioFocused) BorderStroke(2.dp, Color.White) else null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAudioFocused) Color.White else Color.Black.copy(alpha = 0.5f),
                                        contentColor = if (isAudioFocused) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = audioButtonText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { showSubtitleDialog = true },
                                    modifier = Modifier
                                        .focusRequester(subtitleButtonFocusRequester)
                                        .focusProperties {
                                            up = playButtonFocusRequester
                                            left = audioButtonFocusRequester
                                            right = outrosButtonFocusRequester
                                        }
                                        .onFocusChanged { isSubtitleFocused = it.isFocused }
                                        .height(40.dp),
                                    border = if (isSubtitleFocused) BorderStroke(2.dp, Color.White) else null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubtitleFocused) Color.White else Color.Black.copy(alpha = 0.5f),
                                        contentColor = if (isSubtitleFocused) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = subtitleButtonText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = { showOutrosDialog = true },
                                    modifier = Modifier
                                        .focusRequester(outrosButtonFocusRequester)
                                        .focusProperties {
                                            up = playButtonFocusRequester
                                            left = subtitleButtonFocusRequester
                                        }
                                        .onFocusChanged { isOutrosFocused = it.isFocused }
                                        .height(40.dp),
                                    border = if (isOutrosFocused) BorderStroke(2.dp, Color.White) else null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOutrosFocused) Color.White else Color.Black.copy(alpha = 0.5f),
                                        contentColor = if (isOutrosFocused) Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "Outros...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Options (Aspect Ratio) Dialog
        if (showOptionsDialog) {
            BackHandler {
                showOptionsDialog = false
                try { opcoesButtonFocusRequester.requestFocus() } catch(e: Exception) {}
            }
            OptionSelectionDialog(
                title = "Proporção da Tela (Aspect Ratio)",
                icon = Icons.Default.AspectRatio,
                options = listOf(
                    "Original" to null,
                    "Preencher (16:9)" to "16:9",
                    "Esticar" to "21:9",
                    "4:3 / Zoom" to "4:3"
                ),
                selectedVal = vlcPlayerManager.currentAspectRatio,
                onSelectOption = { ratioVal ->
                    vlcPlayerManager.setAspectRatio(ratioVal)
                },
                onDismiss = {
                    showOptionsDialog = false
                    try { opcoesButtonFocusRequester.requestFocus() } catch(e: Exception) {}
                }
            )
        }

        // Audio Tracks Dialog
        if (showAudioDialog) {
            BackHandler {
                showAudioDialog = false
                try { audioButtonFocusRequester.requestFocus() } catch(e: Exception) {}
            }
            val rawAudioTracks = vlcPlayerManager.getAudioTracks() ?: emptyArray()
            val audioTrackList = if (rawAudioTracks.isNotEmpty()) {
                rawAudioTracks.map { Pair(it.id, cleanTrackName(it.name ?: "Áudio ${it.id}")) }
            } else {
                listOf(Pair(1, "Português (Brasil)"))
            }

            val activeAudioId = vlcPlayerManager.getSelectedAudioTrack().let {
                if (it == -1 && audioTrackList.isNotEmpty()) audioTrackList[0].first else it
            }

            TrackSelectionDialog(
                title = "Áudio / Idiomas",
                tracks = audioTrackList,
                selectedTrackId = activeAudioId,
                hasDisableOption = false,
                onSelectTrack = { trackId ->
                    vlcPlayerManager.setAudioTrack(trackId)
                },
                onDismiss = {
                    showAudioDialog = false
                    try { audioButtonFocusRequester.requestFocus() } catch(e: Exception) {}
                }
            )
        }

        // Subtitles Dialog
        if (showSubtitleDialog) {
            BackHandler {
                showSubtitleDialog = false
                try { subtitleButtonFocusRequester.requestFocus() } catch(e: Exception) {}
            }
            val rawSpuTracks = vlcPlayerManager.getSpuTracks() ?: emptyArray()
            val subtitleTrackList = rawSpuTracks.map { Pair(it.id, cleanTrackName(it.name ?: "Legenda ${it.id}")) }

            TrackSelectionDialog(
                title = "Legendas",
                tracks = subtitleTrackList,
                selectedTrackId = vlcPlayerManager.getSelectedSpuTrack(),
                hasDisableOption = true,
                disableOptionLabel = "Desativar",
                onSelectTrack = { trackId ->
                    vlcPlayerManager.setSpuTrack(trackId)
                },
                onDismiss = {
                    showSubtitleDialog = false
                    try { subtitleButtonFocusRequester.requestFocus() } catch(e: Exception) {}
                }
            )
        }

        // Outros Dialog
        if (showOutrosDialog) {
            BackHandler {
                showOutrosDialog = false
                try { outrosButtonFocusRequester.requestFocus() } catch(e: Exception) {}
            }
            OptionSelectionDialog(
                title = "Outras Opções",
                icon = Icons.Default.Info,
                options = listOf(
                    "Informações da Mídia" to "info"
                ),
                selectedVal = "info",
                onSelectOption = { },
                onDismiss = {
                    showOutrosDialog = false
                    try { outrosButtonFocusRequester.requestFocus() } catch(e: Exception) {}
                }
            )
        }

        // Error message overlay card
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(450.dp)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Erro na Transmissão",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Row {
                            Button(
                                onClick = {
                                    errorMessage = null
                                    vlcPlayerManager.playUrl(item.url)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE50914)
                                )
                            ) {
                                Text("Tentar Novamente", color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Voltar")
                            }
                        }
                    }
                }
            }
        }

        // Netflix-style Resume/Restart Prompt Overlay
        if (showResumeDialog) {
            var isContinueFocused by remember { mutableStateOf(false) }
            var isRestartFocused by remember { mutableStateOf(false) }

            val continueScale by animateFloatAsState(
                targetValue = if (isContinueFocused) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "continueBtnScale"
            )
            val restartScale by animateFloatAsState(
                targetValue = if (isRestartFocused) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "restartBtnScale"
            )

            val resumeFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(150)
                try {
                    resumeFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus failures
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(460.dp)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Continuar Assistindo?",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Você já começou a assistir a este conteúdo. Deseja retomar de onde parou ou recomeçar?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        savedProgress?.let { prog ->
                            val duration = prog.duration
                            val position = prog.position
                            val progressFraction = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFFE50914),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val secs = position / 1000
                                    val mins = secs / 60
                                    val hours = mins / 60
                                    val posStr = if (hours > 0) "${hours}h ${mins % 60}m" else String.format("%02d:%02d", mins, secs % 60)

                                    val totalSecs = duration / 1000
                                    val totalMins = totalSecs / 60
                                    val totalHours = totalMins / 60
                                    val durStr = if (totalHours > 0) "${totalHours}h ${totalMins % 60}m" else String.format("%02d:%02d", totalMins, totalSecs % 60)

                                    Text(
                                        text = "Parou em: $posStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        text = durStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    vlcPlayerManager.play()
                                    showResumeDialog = false
                                },
                                modifier = Modifier
                                    .focusRequester(resumeFocusRequester)
                                    .onFocusChanged { isContinueFocused = it.isFocused }
                                    .scale(continueScale)
                                    .height(38.dp)
                                    .border(
                                        width = if (isContinueFocused) 2.dp else 1.dp,
                                        color = if (isContinueFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isContinueFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                    contentColor = if (isContinueFocused) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    "Continuar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Button(
                                onClick = {
                                    vlcPlayerManager.seekTo(0)
                                    vlcPlayerManager.play()
                                    showResumeDialog = false
                                },
                                modifier = Modifier
                                    .onFocusChanged { isRestartFocused = it.isFocused }
                                    .scale(restartScale)
                                    .height(38.dp)
                                    .border(
                                        width = if (isRestartFocused) 2.dp else 1.dp,
                                        color = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(24.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRestartFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                    contentColor = if (isRestartFocused) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    "Recomeçar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cleanTrackName(rawName: String?): String {
    if (rawName.isNullOrBlank()) return "Português (Brasil)"
    return when {
        rawName.contains("Portuguese", ignoreCase = true) || rawName.contains("Português", ignoreCase = true) || rawName.contains("por", ignoreCase = true) || rawName.contains("pt", ignoreCase = true) -> "Português (Brasil)"
        rawName.contains("English", ignoreCase = true) || rawName.contains("Inglês", ignoreCase = true) || rawName.contains("eng", ignoreCase = true) -> "Inglês"
        rawName.contains("Spanish", ignoreCase = true) || rawName.contains("Espanhol", ignoreCase = true) || rawName.contains("spa", ignoreCase = true) -> "Espanhol"
        else -> rawName
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    tracks: List<Pair<Int, String>>,
    selectedTrackId: Int,
    hasDisableOption: Boolean = false,
    disableOptionLabel: String = "Desativar",
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus error
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13151D),
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Disable option if requested
                    if (hasDisableOption) {
                        val isSelected = selectedTrackId == -1
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = {
                                onSelectTrack(-1)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .focusRequester(firstItemFocusRequester)
                                .onFocusChanged { isFocused = it.isFocused },
                            border = if (isFocused) BorderStroke(2.dp, Color.White) else if (isSelected) BorderStroke(1.5.dp, Color(0xFF3B68B2)) else null,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1C283D) else Color(0xFF1A1C24)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = disableOptionLabel,
                                    color = if (isSelected) Color.White else Color(0xFFD0D0D0),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selecionado",
                                        tint = Color(0xFF4C82E6),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, Color.Gray, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    // Track Items
                    tracks.forEachIndexed { index, (trackId, trackName) ->
                        val isSelected = trackId == selectedTrackId
                        var isFocused by remember { mutableStateOf(false) }
                        val itemRequester = if (!hasDisableOption && index == 0) firstItemFocusRequester else remember { FocusRequester() }

                        val showOriginalBadge = trackName.contains("Portuguese", ignoreCase = true) ||
                                trackName.contains("Português", ignoreCase = true) ||
                                trackName.contains("Original", ignoreCase = true) ||
                                (index == 0 && !hasDisableOption)

                        Surface(
                            onClick = {
                                onSelectTrack(trackId)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .focusRequester(itemRequester)
                                .onFocusChanged { isFocused = it.isFocused },
                            border = if (isFocused) BorderStroke(2.dp, Color.White) else if (isSelected) BorderStroke(1.5.dp, Color(0xFF3B68B2)) else null,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1C283D) else Color(0xFF1A1C24)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = trackName,
                                        color = if (isSelected) Color.White else Color(0xFFD0D0D0),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (showOriginalBadge) {
                                        Surface(
                                            color = Color(0xFF283A59),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ORIGINAL",
                                                color = Color(0xFF83A9E8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selecionado",
                                        tint = Color(0xFF4C82E6),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, Color.Gray, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Fechar Pill Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloseFocused) Color.White else Color(0xFF2B2E38),
                            contentColor = if (isCloseFocused) Color.Black else Color.White
                        ),
                        border = if (isCloseFocused) BorderStroke(2.dp, Color.White) else null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                    ) {
                        Text(
                            text = "Fechar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun OptionSelectionDialog(
    title: String,
    icon: ImageVector = Icons.Default.Settings,
    options: List<Pair<String, String?>>,
    selectedVal: String?,
    onSelectOption: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus error
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13151D),
        shape = RoundedCornerShape(20.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEachIndexed { index, (label, value) ->
                        val isSelected = (selectedVal == value) || (selectedVal == null && value == null)
                        var isFocused by remember { mutableStateOf(false) }
                        val itemRequester = if (index == 0) firstItemFocusRequester else remember { FocusRequester() }

                        Surface(
                            onClick = {
                                onSelectOption(value)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .focusRequester(itemRequester)
                                .onFocusChanged { isFocused = it.isFocused },
                            border = if (isFocused) BorderStroke(2.dp, Color.White) else if (isSelected) BorderStroke(1.5.dp, Color(0xFF3B68B2)) else null,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1C283D) else Color(0xFF1A1C24)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFFD0D0D0),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selecionado",
                                        tint = Color(0xFF4C82E6),
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, Color.Gray, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Fechar Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloseFocused) Color.White else Color(0xFF2B2E38),
                            contentColor = if (isCloseFocused) Color.Black else Color.White
                        ),
                        border = if (isCloseFocused) BorderStroke(2.dp, Color.White) else null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                    ) {
                        Text(
                            text = "Fechar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}


