package com.labix.navirom.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.labix.navirom.data.lyrics.LyricsData
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.data.model.RepeatMode
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.ui.theme.*

enum class PlayerViewMode {
    ARTWORK,
    LYRICS
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerModal(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    downloadStatus: DownloadStatus,
    queueIndex: Int = 0,
    queueSize: Int = 0,
    lyricsData: LyricsData = LyricsData(),
    appLanguage: AppLanguage = AppLanguage.ENGLISH,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadTrack: () -> Unit,
    onOpenQueue: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onRefetchLyrics: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val haptics = rememberNaviromHaptics()

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }
    var viewMode by remember { mutableStateOf(PlayerViewMode.ARTWORK) }
    var showMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("30") }

    // Keep screen on while singing/viewing lyrics in full player modal
    val currentView = LocalView.current
    DisposableEffect(viewMode) {
        if (viewMode == PlayerViewMode.LYRICS) {
            currentView.keepScreenOn = true
        } else {
            currentView.keepScreenOn = false
        }
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val currentPosMs = if (isDraggingSlider) dragPositionMs else playbackState.currentPositionMs
    val totalDurationMs = if (playbackState.durationMs > 0) playbackState.durationMs else (track.durationSeconds * 1000L)

    val formatTime: (Long) -> String = { ms ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        "%d:%02d".format(min, sec)
    }

    val backgroundColor = Color(0xFF1E1E1E) // Dark gray from the image
    val cardColor = Color.White
    val textOnCard = Color.Black
    val textMutedOnCard = Color.Gray

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("full_player_modal")
            .pointerInput(viewMode) {
                var totalDragX = 0f
                var totalDragY = 0f
                var gestureHandled = false

                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                        gestureHandled = false
                    },
                    onDragEnd = {
                        totalDragX = 0f
                        totalDragY = 0f
                        gestureHandled = false
                    },
                    onDragCancel = {
                        totalDragX = 0f
                        totalDragY = 0f
                        gestureHandled = false
                    },
                    onDrag = { change, dragAmount ->
                        if (!gestureHandled) {
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                            val threshold = 55f

                            if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.2f && kotlin.math.abs(totalDragX) > threshold) {
                                change.consume()
                                gestureHandled = true
                                if (totalDragX < -threshold) {
                                    // Swipe Right to Left -> NEXT SONG
                                    haptics.click()
                                    onNext()
                                } else if (totalDragX > threshold) {
                                    // Swipe Left to Right -> PREVIOUS SONG
                                    haptics.click()
                                    onPrevious()
                                }
                            } else if (kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.2f && kotlin.math.abs(totalDragY) > threshold) {
                                if (totalDragY < -threshold) {
                                    // Swipe Bottom to Top (Up) -> SHOW LYRICS VIEW
                                    if (viewMode == PlayerViewMode.ARTWORK) {
                                        change.consume()
                                        gestureHandled = true
                                        haptics.toggle()
                                        viewMode = PlayerViewMode.LYRICS
                                    }
                                } else if (totalDragY > threshold) {
                                    // Swipe Top to Bottom (Down) -> MINIMIZE PLAYER
                                    change.consume()
                                    gestureHandled = true
                                    if (viewMode == PlayerViewMode.ARTWORK) {
                                        haptics.click()
                                        onDismiss()
                                    } else if (viewMode == PlayerViewMode.LYRICS) {
                                        haptics.toggle()
                                        viewMode = PlayerViewMode.ARTWORK
                                    }
                                }
                            }
                        }
                    }
                )
            }
    ) {
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select time before playback pauses:")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(onClick = { onSetSleepTimer(15); showSleepTimerDialog = false }) { Text("15m") }
                            Button(onClick = { onSetSleepTimer(30); showSleepTimerDialog = false }) { Text("30m") }
                            Button(onClick = { onSetSleepTimer(45); showSleepTimerDialog = false }) { Text("45m") }
                            Button(onClick = { onSetSleepTimer(60); showSleepTimerDialog = false }) { Text("60m") }
                        }
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { customMinutesText = it },
                            label = { Text("Custom minutes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val mins = customMinutesText.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    onSetSleepTimer(mins)
                                    showSleepTimerDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Set Custom Timer")
                        }
                        TextButton(
                            onClick = {
                                onSetSleepTimer(null)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn off Sleep Timer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                if (targetState == PlayerViewMode.LYRICS) {
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) { it } + fadeIn(animationSpec = tween(300)))
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = tween(240)
                            ) { -it / 3 } + fadeOut(animationSpec = tween(200))
                        )
                } else {
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) { -it / 3 } + fadeIn(animationSpec = tween(300)))
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = tween(240)
                            ) { it } + fadeOut(animationSpec = tween(200))
                        )
                }
            },
            label = "PlayerViewModeTransition"
        ) { mode ->
            if (mode == PlayerViewMode.ARTWORK) {
                val validSyncedLines = remember(lyricsData.syncedLines) {
                    lyricsData.syncedLines.filter { it.text.isNotBlank() }
                }
                val longestLineLength = remember(validSyncedLines, lyricsData.plainLyrics) {
                    if (validSyncedLines.isNotEmpty()) {
                        validSyncedLines.maxOfOrNull { it.text.trim().length } ?: 20
                    } else if (lyricsData.plainLyrics.isNotBlank()) {
                        lyricsData.plainLyrics.lines().filter { it.isNotBlank() }.maxOfOrNull { it.trim().length } ?: 20
                    } else 20
                }
                val lyricOverlayFontSize = remember(longestLineLength) {
                    when {
                        longestLineLength <= 25 -> 24.sp
                        longestLineLength <= 45 -> 20.sp
                        longestLineLength <= 70 -> 17.sp
                        else -> 15.sp
                    }
                }
                val currentLyricLineText = remember(currentPosMs, validSyncedLines) {
                    if (validSyncedLines.isNotEmpty()) {
                        val idx = validSyncedLines.indexOfLast { (it.timeMs - 500L) <= currentPosMs }
                        if (idx >= 0) validSyncedLines[idx].text else validSyncedLines.firstOrNull()?.text ?: ""
                    } else ""
                }

                // Main Artwork View
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // White Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        color = cardColor,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    haptics.click()
                                    onDismiss()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textOnCard)
                                }
                                val songIndexText = remember(queueIndex, queueSize, track.id) {
                                    val currentNum = if (queueIndex in 0 until queueSize) {
                                        queueIndex + 1
                                    } else {
                                        1
                                    }
                                    val totalNum = if (queueSize > 0) queueSize else 1
                                    "$currentNum/$totalNum"
                                }
                                Text(
                                    text = songIndexText,
                                    color = textOnCard,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Box {
                                    IconButton(onClick = {
                                        haptics.click()
                                        showMenu = true
                                    }) {
                                        Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = textOnCard)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Go to Artist") },
                                            onClick = {
                                                showMenu = false
                                                onArtistClick?.invoke(track.artistId)
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Go to Album") },
                                            onClick = {
                                                showMenu = false
                                                onAlbumClick?.invoke(track.albumId)
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sleep Timer") },
                                            onClick = {
                                                showMenu = false
                                                showSleepTimerDialog = true
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Artwork with lyric overlay at bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(32.dp))
                            ) {
                                SongAlbumCover(
                                    coverArtUrl = track.coverArtUrl,
                                    contentDescription = "Album Artwork",
                                    isAlbum = false,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var totalX = 0f
                                            var totalY = 0f
                                            var handled = false
                                            detectDragGestures(
                                                onDragStart = { totalX = 0f; totalY = 0f; handled = false },
                                                onDragEnd = { totalX = 0f; totalY = 0f; handled = false },
                                                onDragCancel = { totalX = 0f; totalY = 0f; handled = false },
                                                onDrag = { change, dragAmount ->
                                                    if (!handled) {
                                                        totalX += dragAmount.x
                                                        totalY += dragAmount.y
                                                        val th = 50f
                                                        if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.2f && kotlin.math.abs(totalX) > th) {
                                                            change.consume()
                                                            handled = true
                                                            if (totalX < -th) {
                                                                // Swipe Left -> Next
                                                                haptics.click()
                                                                onNext()
                                                            } else if (totalX > th) {
                                                                // Swipe Right -> Previous
                                                                haptics.click()
                                                                onPrevious()
                                                            }
                                                        } else if (kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.2f && kotlin.math.abs(totalY) > th) {
                                                            if (totalY < -th) {
                                                                // Swipe Up -> Lyrics View
                                                                change.consume()
                                                                handled = true
                                                                haptics.toggle()
                                                                viewMode = PlayerViewMode.LYRICS
                                                            } else if (totalY > th) {
                                                                // Swipe Down -> Minimize
                                                                change.consume()
                                                                handled = true
                                                                haptics.click()
                                                                onDismiss()
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                haptics.toggle()
                                                viewMode = PlayerViewMode.LYRICS
                                            },
                                            onLongClick = { onAlbumClick?.invoke(track.albumId) }
                                        )
                                )

                                if (currentLyricLineText.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.55f),
                                                        Color.Black.copy(alpha = 0.88f)
                                                    )
                                                )
                                            )
                                            .clickable {
                                                haptics.toggle()
                                                viewMode = PlayerViewMode.LYRICS
                                            }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = currentLyricLineText,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 2 })
                                                    .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 2 })
                                            },
                                            label = "LyricOverlayText"
                                        ) { lineText ->
                                            Text(
                                                text = lineText,
                                                color = Color.White,
                                                fontSize = lyricOverlayFontSize,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                softWrap = true,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = (lyricOverlayFontSize.value * 1.25f).sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Title, Artist, and Saved Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = track.title,
                                        color = textOnCard,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = textMutedOnCard,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onArtistClick?.invoke(track.artistId) }
                                    )
                                }
                                
                                // Saved Button
                                Surface(
                                    color = if (isFavorite) Color(0xFF1DB954) else Color(0xFFE0E0E0),
                                    shape = CircleShape,
                                    modifier = Modifier.clickable { onToggleFavorite() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Saved",
                                            tint = if (isFavorite) Color.White else textMutedOnCard,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Saved",
                                            color = if (isFavorite) Color.White else textMutedOnCard,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Seekbar
                            Slider(
                                value = currentPosMs.toFloat(),
                                onValueChange = {
                                    isDraggingSlider = true
                                    dragPositionMs = it.toLong()
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                    onSeekTo(dragPositionMs)
                                },
                                valueRange = 0f..(totalDurationMs.toFloat().takeIf { it > 0f } ?: 100f),
                                colors = SliderDefaults.colors(
                                    thumbColor = textOnCard,
                                    activeTrackColor = textOnCard,
                                    inactiveTrackColor = Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = formatTime(currentPosMs), color = textMutedOnCard, fontSize = 12.sp)
                                Text(text = formatTime(totalDurationMs), color = textMutedOnCard, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onCycleRepeat) {
                                    Icon(
                                        imageVector = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (playbackState.repeatMode == RepeatMode.OFF) textMutedOnCard else textOnCard
                                    )
                                }
                                IconButton(onClick = onPrevious) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = textOnCard, modifier = Modifier.size(32.dp))
                                }
                                
                                // Play/Pause Button (Circle outline)
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(2.dp, textOnCard),
                                    modifier = Modifier.size(64.dp).clickable { onTogglePlayPause() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = textOnCard,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                IconButton(onClick = onNext) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = textOnCard, modifier = Modifier.size(32.dp))
                                }
                                IconButton(onClick = onToggleShuffle) {
                                    Icon(
                                        imageVector = Icons.Filled.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (playbackState.isShuffle) textOnCard else textMutedOnCard
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Output device indicator removed
                        }
                    }

                    // Bottom Dark Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lyrics button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF3D959), // Yellow
                            modifier = Modifier.size(48.dp).clickable { viewMode = PlayerViewMode.LYRICS }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Mic, contentDescription = "Lyrics", tint = Color.Black)
                            }
                        }

                        // Lyrics by Genius text
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lyrics by", color = Color.Gray, fontSize = 10.sp)
                            Text("GENIUS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }

                        // Queue button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier.size(48.dp).clickable { onOpenQueue() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Lyrics View
                val listState = rememberLazyListState()
                val showTopCard by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 15
                    }
                }
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Mini White Card (Show/hide when scrolling up/down)
                    AnimatedVisibility(
                        visible = showTopCard,
                        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { -it },
                        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it }
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    var totalX = 0f
                                    var totalY = 0f
                                    var handled = false
                                    detectDragGestures(
                                        onDragStart = { totalX = 0f; totalY = 0f; handled = false },
                                        onDragEnd = { totalX = 0f; totalY = 0f; handled = false },
                                        onDragCancel = { totalX = 0f; totalY = 0f; handled = false },
                                        onDrag = { change, dragAmount ->
                                            if (!handled) {
                                                totalX += dragAmount.x
                                                totalY += dragAmount.y
                                                val th = 50f
                                                if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.2f && kotlin.math.abs(totalX) > th) {
                                                    change.consume()
                                                    handled = true
                                                    if (totalX < -th) {
                                                        haptics.click()
                                                        onNext()
                                                    } else if (totalX > th) {
                                                        haptics.click()
                                                        onPrevious()
                                                    }
                                                } else if (totalY > th) {
                                                    change.consume()
                                                    handled = true
                                                    haptics.toggle()
                                                    viewMode = PlayerViewMode.ARTWORK
                                                }
                                            }
                                        }
                                    )
                                },
                            color = cardColor,
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.click()
                                            viewMode = PlayerViewMode.ARTWORK
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Player",
                                            tint = textOnCard
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                    ) {
                                        SongAlbumCover(
                                            coverArtUrl = track.coverArtUrl,
                                            contentDescription = "Cover",
                                            isAlbum = false,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = track.title,
                                                color = textOnCard,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                color = textMutedOnCard,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            IconButton(onClick = {
                                                haptics.click()
                                                showMenu = true
                                            }) {
                                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = textMutedOnCard)
                                            }
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Go to Artist") },
                                                    onClick = {
                                                        showMenu = false
                                                        onArtistClick?.invoke(track.artistId)
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Go to Album") },
                                                    onClick = {
                                                        showMenu = false
                                                        onAlbumClick?.invoke(track.albumId)
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Sleep Timer") },
                                                    onClick = {
                                                        showMenu = false
                                                        showSleepTimerDialog = true
                                                    },
                                                    leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null) }
                                                )
                                            }
                                        }
                                        IconButton(onClick = onToggleFavorite) {
                                            Icon(
                                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                contentDescription = "Favorite",
                                                tint = if (isFavorite) Color(0xFF1DB954) else textMutedOnCard
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                // Drag handle
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray)
                                        .clickable {
                                            haptics.toggle()
                                            viewMode = PlayerViewMode.ARTWORK
                                        }
                                )
                            }
                        }
                    }
                    // Lyrics Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (lyricsData.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White
                            )
                        } else if (lyricsData.error != null || (lyricsData.plainLyrics.isEmpty() && lyricsData.syncedLines.isEmpty())) {
                            Text(
                                text = "Lyrics not available",
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            // Dots indicator
                            Row(
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.Gray))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                            }
                            val validSyncedLines = remember(lyricsData.syncedLines) {
                                lyricsData.syncedLines.filter { it.text.isNotBlank() }
                            }

                            if (validSyncedLines.isNotEmpty()) {
                                // Find active line with 0.5s anticipation
                                val activeLineIndex = validSyncedLines.indexOfLast { (it.timeMs - 500L) <= currentPosMs }.coerceAtLeast(0)
                                
                                LaunchedEffect(activeLineIndex) {
                                    if (activeLineIndex >= 0 && validSyncedLines.isNotEmpty()) {
                                        listState.animateScrollToItem(index = activeLineIndex, scrollOffset = 0)
                                    }
                                }

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    val verticalPadding = (maxHeight / 2 - 36.dp).coerceAtLeast(60.dp)

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(validSyncedLines) { index, line ->
                                            val isActive = index == activeLineIndex
                                            val isPast = index < activeLineIndex

                                            val textColor by animateColorAsState(
                                                targetValue = when {
                                                    isActive -> Color.White
                                                    isPast -> Color.White.copy(alpha = 0.55f)
                                                    else -> Color.White.copy(alpha = 0.35f)
                                                },
                                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                                label = "modal_lyric_color"
                                            )

                                            val bgColor by animateColorAsState(
                                                targetValue = if (isActive) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                                label = "modal_lyric_bg"
                                            )

                                            val scale by animateFloatAsState(
                                                targetValue = if (isActive) 1.03f else 1.0f,
                                                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                                label = "modal_lyric_scale"
                                            )

                                            Surface(
                                                color = bgColor,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
                                                    .clickable {
                                                        haptics.click()
                                                        onSeekTo(line.timeMs)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AnimatedVisibility(
                                                        visible = isActive,
                                                        enter = fadeIn(tween(300)),
                                                        exit = fadeOut(tween(200))
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Filled.PlayArrow,
                                                                contentDescription = "Current",
                                                                tint = Color(0xFFF3D959), // Yellow play icon
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                        }
                                                    }
                                                    if (!isActive) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = line.text,
                                                        color = textColor,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = if (isActive) 21.sp else 16.sp,
                                                        lineHeight = if (isActive) 28.sp else 22.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Plain lyrics
                                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp).verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = lyricsData.plainLyrics,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        lineHeight = 28.sp
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(48.dp).clickable { onTogglePlayPause() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black
                                )
                            }
                        }
                        
                        Slider(
                            value = currentPosMs.toFloat(),
                            onValueChange = {
                                isDraggingSlider = true
                                dragPositionMs = it.toLong()
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                onSeekTo(dragPositionMs)
                            },
                            valueRange = 0f..(totalDurationMs.toFloat().takeIf { it > 0f } ?: 100f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.Gray
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier.size(48.dp).clickable { onOpenQueue() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.QueueMusic, contentDescription = "Queue", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
