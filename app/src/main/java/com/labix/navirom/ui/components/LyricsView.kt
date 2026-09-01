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
    modifier: Modifier = Modifier
) {
    val haptics = rememberNaviromHaptics()
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

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

    // Filter out blank/empty lines
    val validSyncedLines = remember(lyricsData.syncedLines) {
        lyricsData.syncedLines.filter { it.text.isNotBlank() }
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
        LyricsSource.NOT_FOUND -> str("lyrics_not_found")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (lyricsData.isSynced) Icons.Filled.SyncAlt else Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    Button(
                        onClick = onRefetch,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(str("lyrics_reload"))
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
                    .verticalScroll(rememberScrollState())
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
    }
}
