package com.labix.navirom.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.ui.util.rememberNaviromHaptics

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val haptics = rememberNaviromHaptics()
    val animatedProgress by animateFloatAsState(
        targetValue = playbackState.progressFraction,
        animationSpec = tween(200, easing = LinearEasing),
        label = "miniProgress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            // Linear progress indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main content area supporting taps and swipes
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onExpandPlayer() }
                        .pointerInput(Unit) {
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
                                                haptics.click()
                                                onNext()
                                            } else if (totalDragX > threshold) {
                                                haptics.click()
                                                onPrevious()
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        SongAlbumCover(
                            coverArtUrl = track.coverArtUrl,
                            contentDescription = track.title,
                            isAlbum = false,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist with smooth transition
                    AnimatedContent(
                        targetState = track.id,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
                        },
                        label = "miniTrackAnim",
                        modifier = Modifier.weight(1f)
                    ) { _ ->
                        Column {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Controls
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(2.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("mini_player_play_pause")
                    ) {
                        AnimatedContent(
                            targetState = playbackState.isPlaying,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(150)))
                                    .togetherWith(scaleOut() + fadeOut(tween(100)))
                            },
                            label = "miniPlayPauseAnim"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

