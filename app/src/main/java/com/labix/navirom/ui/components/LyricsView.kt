package com.labix.navirom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.labix.navirom.data.lyrics.LyricsData
import com.labix.navirom.data.lyrics.LyricsSource
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyricsData: LyricsData,
    currentPositionMs: Long,
    appLanguage: AppLanguage,
    onSeekTo: (Long) -> Unit,
    onRefetch: () -> Unit,
    onFetchTeksteShqip: ((String?) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberNaviromHaptics()
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    var showTeksteShqipDialog by remember { mutableStateOf(false) }
    var teksteShqipUrlInput by remember { mutableStateOf("") }

    // Keep screen on while lyrics view is active for seamless karaoke/sing-along
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val plainScrollState = rememberScrollState()

    // Filter out blank/empty lines
    val validSyncedLines = remember(lyricsData.syncedLines) {
        lyricsData.syncedLines.filter { it.text.isNotBlank() }
    }

    val hasAnyLyrics = validSyncedLines.isNotEmpty() || lyricsData.plainLyrics.isNotBlank()
    val isLyricsAvailable = !lyricsData.isLoading && lyricsData.error == null && hasAnyLyrics

    var accumulatedDownwardDrag by remember { mutableFloatStateOf(0f) }

    val lyricsNestedScrollConnection = remember(onClose) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && accumulatedDownwardDrag > 0f) {
                    val consumed = (-available.y).coerceAtMost(accumulatedDownwardDrag)
                    accumulatedDownwardDrag -= consumed
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (onClose != null && available.y > 0f) {
                    accumulatedDownwardDrag += available.y
                    if (accumulatedDownwardDrag > 45f) {
                        accumulatedDownwardDrag = 0f
                        haptics.toggle()
                        onClose.invoke()
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                accumulatedDownwardDrag = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                accumulatedDownwardDrag = 0f
                if (onClose != null && available.y > 150f) {
                    haptics.toggle()
                    onClose.invoke()
                }
                return Velocity.Zero
            }
        }
    }

    val noLyricsDragModifier = if (onClose != null && !isLyricsAvailable) {
        Modifier.pointerInput(Unit) {
            var totalY = 0f
            var handled = false
            detectDragGestures(
                onDragStart = { totalY = 0f; handled = false },
                onDragEnd = { totalY = 0f; handled = false },
                onDragCancel = { totalY = 0f; handled = false },
                onDrag = { change, dragAmount ->
                    if (!handled) {
                        totalY += dragAmount.y
                        if (totalY > 35f) {
                            change.consume()
                            handled = true
                            haptics.toggle()
                            onClose.invoke()
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    // Find active lyric line based on current playback timestamp with a 0.5s anticipation offset
    val activeIndex = remember(currentPositionMs, validSyncedLines) {
        if (validSyncedLines.isEmpty()) -1
        else {
            val idx = validSyncedLines.indexOfLast { (it.timeMs - 500L) <= currentPositionMs }
            if (idx >= 0) idx else 0
        }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Auto-scroll to active line (centered in vertical middle)
    LaunchedEffect(activeIndex, autoScrollEnabled) {
        if (autoScrollEnabled && activeIndex >= 0 && validSyncedLines.isNotEmpty()) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
        }
    }

    val sourceLabel = when (lyricsData.source) {
        LyricsSource.EMBEDDED_FILE -> str("lyrics_source_file")
        LyricsSource.NAVIDROME_SERVER -> str("lyrics_source_server")
        LyricsSource.ONLINE_LRCLIB -> str("lyrics_source_online")
        LyricsSource.ONLINE_TEKSTESHQIP -> str("lyrics_source_teksteshqip")
        LyricsSource.NOT_FOUND -> str("lyrics_not_found")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .nestedScroll(lyricsNestedScrollConnection)
            .then(noLyricsDragModifier)
            .testTag("lyrics_view"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Source & Controls Header Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (lyricsData.isSynced) Icons.Filled.SyncAlt else Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (onFetchTeksteShqip != null) {
                        SuggestionChip(
                            onClick = {
                                if (teksteShqipUrlInput.isBlank()) {
                                    if (lyricsData.artist.contains("leonora", ignoreCase = true) ||
                                        lyricsData.title.contains("vritet", ignoreCase = true)
                                    ) {
                                        teksteShqipUrlInput = "https://teksteshqip.com/leonora-jakupi/teksti/1848928"
                                    }
                                }
                                showTeksteShqipDialog = true
                            },
                            label = { Text("TeksteShqip", style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(12.dp))
                            },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lyricsData.isSynced) {
                        IconButton(
                            onClick = { autoScrollEnabled = !autoScrollEnabled },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (autoScrollEnabled) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                                contentDescription = "Auto scroll lock",
                                tint = if (autoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefetch,
                        modifier = Modifier.size(32.dp).testTag("lyrics_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = str("lyrics_reload"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (lyricsData.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = str("lyrics_loading"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (lyricsData.source == LyricsSource.NOT_FOUND && lyricsData.plainLyrics.isBlank()) {
            // Not found view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.MusicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = str("lyrics_not_found"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = str("lyrics_not_found_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRefetch,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(str("lyrics_reload"))
                        }
                        if (onFetchTeksteShqip != null) {
                            FilledTonalButton(
                                onClick = {
                                    if (teksteShqipUrlInput.isBlank() &&
                                        (lyricsData.artist.contains("leonora", ignoreCase = true) ||
                                                lyricsData.title.contains("vritet", ignoreCase = true))
                                    ) {
                                        teksteShqipUrlInput = "https://teksteshqip.com/leonora-jakupi/teksti/1848928"
                                    }
                                    showTeksteShqipDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TeksteShqip")
                            }
                        }
                    }
                }
            }
        } else if (lyricsData.isSynced && validSyncedLines.isNotEmpty()) {
            // Synchronized Karaoke Lyrics with Vertical-Middle Centering
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                val verticalPadding = (maxHeight / 2 - 32.dp).coerceAtLeast(60.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(validSyncedLines) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = index < activeIndex

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isActive -> MaterialTheme.colorScheme.onSurface
                                isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                            label = "lyric_color"
                        )

                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                            label = "lyric_bg"
                        )

                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.03f else 1.0f,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                            label = "lyric_scale"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptics.click()
                                    onSeekTo(line.timeMs)
                                }
                                .testTag("lyric_line_$index"),
                            shape = RoundedCornerShape(16.dp),
                            color = bgColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = line.text,
                                    style = if (isActive)
                                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp)
                                    else
                                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )

                                AnimatedVisibility(
                                    visible = isActive,
                                    enter = androidx.compose.animation.fadeIn(tween(300)),
                                    exit = androidx.compose.animation.fadeOut(tween(200))
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Plain Text Lyrics
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(plainScrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = lyricsData.plainLyrics,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showTeksteShqipDialog && onFetchTeksteShqip != null) {
            AlertDialog(
                onDismissRequest = { showTeksteShqipDialog = false },
                title = { Text(str("lyrics_teksteshqip_dialog_title")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = str("lyrics_teksteshqip_desc"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = teksteShqipUrlInput,
                            onValueChange = { teksteShqipUrlInput = it },
                            label = { Text("URL") },
                            placeholder = { Text("https://teksteshqip.com/leonora-jakupi/teksti/1848928") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val url = teksteShqipUrlInput.trim().ifBlank { null }
                                onFetchTeksteShqip(url)
                                showTeksteShqipDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (teksteShqipUrlInput.isNotBlank()) str("lyrics_teksteshqip_fetch") else str("lyrics_teksteshqip_search_auto"))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTeksteShqipDialog = false }) {
                        Text(str("cancel"))
                    }
                }
            )
        }
    }
}
