package com.labix.navirom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.data.model.NaviromTrack
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.data.model.SleepTimerOptions
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.util.rememberNaviromHaptics
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverQuickActionsSheet(
    track: NaviromTrack,
    playbackState: PlaybackState,
    downloadStatus: DownloadStatus,
    downloadProgress: Float? = null,
    sleepTimerOptions: SleepTimerOptions = SleepTimerOptions(),
    appLanguage: AppLanguage = AppLanguage.GERMAN,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetSleepTimer: (Int?, SleepTimerOptions) -> Unit,
    onUpdateSleepTimerOptions: ((SleepTimerOptions) -> Unit)? = null,
    onOpenSleepTimerDialog: () -> Unit,
    onDownloadTrack: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberNaviromHaptics()
    val scrollState = rememberScrollState()

    val totalDurationMs = if (playbackState.durationMs > 0) playbackState.durationMs else (track.durationSeconds * 1000L)
    val currentPosMs = playbackState.currentPositionMs.coerceIn(0L, totalDurationMs.coerceAtLeast(1L))

    val formatTime: (Long) -> String = { ms ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        "%d:%02d".format(min, sec)
    }

    // Timer active calculation
    val isTimerActive = (playbackState.sleepTimerSecondsLeft != null && playbackState.sleepTimerSecondsLeft > 0) ||
            (playbackState.sleepTimerMinutesLeft != null && playbackState.sleepTimerMinutesLeft > 0)

    val totalTimerSecs = playbackState.sleepTimerSecondsLeft ?: (playbackState.sleepTimerMinutesLeft?.times(60) ?: 0)
    val timerCountdownText = remember(playbackState.sleepTimerSecondsLeft, playbackState.sleepTimerMinutesLeft) {
        if (totalTimerSecs <= 0) ""
        else {
            val mins = totalTimerSecs / 60
            val secs = totalTimerSecs % 60
            "%02d:%02d".format(mins, secs)
        }
    }

    // Jump Input State
    var jumpInputText by remember { mutableStateOf("") }
    var jumpErrorText by remember { mutableStateOf<String?>(null) }
    var jumpSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Custom Timer Minutes State
    var customTimerText by remember { mutableStateOf("30") }

    val parseTimeInput: (String) -> Long? = { input ->
        val trimmed = input.trim().removeSuffix("s").removeSuffix("S")
        if (trimmed.isEmpty()) null
        else if (trimmed.contains(":") || trimmed.contains(".")) {
            val sep = if (trimmed.contains(":")) ":" else "."
            val parts = trimmed.split(sep)
            when (parts.size) {
                2 -> {
                    val m = parts[0].toLongOrNull()
                    val s = parts[1].toLongOrNull()
                    if (m != null && s != null && s in 0..59) (m * 60 + s) * 1000L else null
                }
                3 -> {
                    val h = parts[0].toLongOrNull()
                    val m = parts[1].toLongOrNull()
                    val s = parts[2].toLongOrNull()
                    if (h != null && m != null && s != null && m in 0..59 && s in 0..59) (h * 3600 + m * 60 + s) * 1000L else null
                }
                else -> null
            }
        } else {
            val s = trimmed.toLongOrNull()
            if (s != null) s * 1000L else null
        }
    }

    val executeJump: (Long) -> Unit = { targetMs ->
        val clamped = targetMs.coerceIn(0L, totalDurationMs)
        haptics.click()
        onSeekTo(clamped)
        jumpSuccessMessage = "${formatTime(clamped)}"
        jumpErrorText = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag("cover_quick_actions_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Track Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongAlbumCover(
                    coverArtUrl = track.coverArtUrl,
                    contentDescription = track.title,
                    isAlbum = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                    Text(
                        text = "${formatTime(currentPosMs)} / ${formatTime(totalDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("cover_sheet_close_btn")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ==========================================
            // 1. COUNTDOWN TIMER SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isTimerActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cover_sheet_timer_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isTimerActive) Icons.Filled.HourglassTop else Icons.Filled.Timer,
                                contentDescription = null,
                                tint = if (isTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = NaviromStrings.get("countdown_timer_title", appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isTimerActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = timerCountdownText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (isTimerActive) {
                        // Running timer controls
                        Text(
                            text = "${NaviromStrings.get("countdown_timer_active", appLanguage)}: $timerCountdownText ${NaviromStrings.get("updates_view_changelog", appLanguage).let { "" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    haptics.click()
                                    onSetSleepTimer(null, sleepTimerOptions)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("cover_sheet_stop_timer_btn")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = NaviromStrings.get("countdown_timer_stop", appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptics.click()
                                    val newMins = (totalTimerSecs / 60) + 5
                                    onSetSleepTimer(newMins.coerceAtLeast(1), sleepTimerOptions)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text("+5m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptics.click()
                                    val newMins = (totalTimerSecs / 60) + 15
                                    onSetSleepTimer(newMins.coerceAtLeast(1), sleepTimerOptions)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text("+15m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Presets Row
                        Text(
                            text = NaviromStrings.get("sleep_timer_desc", appLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                FilledTonalButton(
                                    onClick = {
                                        haptics.click()
                                        onSetSleepTimer(mins, sleepTimerOptions)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            // End of song preset
                            val remainingSongMins = ceil((totalDurationMs - currentPosMs).coerceAtLeast(1000L) / 60000.0).toInt().coerceAtLeast(1)
                            FilledTonalButton(
                                onClick = {
                                    haptics.click()
                                    onSetSleepTimer(remainingSongMins, sleepTimerOptions)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = NaviromStrings.get("countdown_end_of_track", appLanguage),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Custom Minutes Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customTimerText,
                                onValueChange = { customTimerText = it.filter { ch -> ch.isDigit() }.take(3) },
                                label = { Text(NaviromStrings.get("sleep_timer_custom_min", appLanguage), fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val m = customTimerText.toIntOrNull()
                                    if (m != null && m > 0) {
                                        haptics.click()
                                        onSetSleepTimer(m, sleepTimerOptions)
                                    }
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            )

                            Button(
                                onClick = {
                                    val m = customTimerText.toIntOrNull()
                                    if (m != null && m > 0) {
                                        haptics.click()
                                        onSetSleepTimer(m, sleepTimerOptions)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("cover_sheet_start_timer_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = NaviromStrings.get("countdown_timer_start", appLanguage),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Link to advanced sleep timer options (BT/WiFi/Data)
                    TextButton(
                        onClick = {
                            haptics.click()
                            onDismiss()
                            onOpenSleepTimerDialog()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = NaviromStrings.get("countdown_more_options", appLanguage),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ==========================================
            // 2. DIRECT TIME JUMP SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cover_sheet_jump_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FastForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = NaviromStrings.get("jump_to_time_title", appLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Current progress bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Aktuell: ${formatTime(currentPosMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Gesamt: ${formatTime(totalDurationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val progressFraction = if (totalDurationMs > 0) {
                            (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }

                    // Quick Jump Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { executeJump((currentPosMs - 30_000L).coerceAtLeast(0L)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(NaviromStrings.get("jump_quick_rewind", appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { executeJump((currentPosMs + 30_000L).coerceAtMost(totalDurationMs)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(NaviromStrings.get("jump_quick_forward", appLanguage), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { executeJump(0L) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(NaviromStrings.get("jump_quick_start", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = { executeJump(totalDurationMs / 2L) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Text(NaviromStrings.get("jump_quick_middle", appLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    // Time Input and Jump Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = jumpInputText,
                            onValueChange = {
                                jumpInputText = it
                                jumpErrorText = null
                                jumpSuccessMessage = null
                            },
                            placeholder = { Text(NaviromStrings.get("jump_to_time_hint", appLanguage), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (jumpInputText.isNotEmpty()) {
                                    IconButton(onClick = { jumpInputText = ""; jumpErrorText = null }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            isError = jumpErrorText != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val parsed = parseTimeInput(jumpInputText)
                                    if (parsed != null && parsed in 0L..totalDurationMs) {
                                        executeJump(parsed)
                                    } else {
                                        jumpErrorText = NaviromStrings.get("jump_to_time_invalid", appLanguage)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("jump_to_time_input")
                        )

                        Button(
                            onClick = {
                                val parsed = parseTimeInput(jumpInputText)
                                if (parsed != null && parsed in 0L..totalDurationMs) {
                                    executeJump(parsed)
                                } else {
                                    jumpErrorText = NaviromStrings.get("jump_to_time_invalid", appLanguage)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("jump_to_time_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = NaviromStrings.get("jump_to_time_btn", appLanguage),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Feedback messages
                    if (jumpErrorText != null) {
                        Text(
                            text = jumpErrorText ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (jumpSuccessMessage != null) {
                        Text(
                            text = "✓ Gesprungen zu $jumpSuccessMessage",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ==========================================
            // 3. DOWNLOAD SONG SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (downloadStatus) {
                        DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cover_sheet_download_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (downloadStatus) {
                                    DownloadStatus.DOWNLOADED -> Icons.Filled.CheckCircle
                                    DownloadStatus.DOWNLOADING -> Icons.Filled.Downloading
                                    DownloadStatus.FAILED -> Icons.Filled.ErrorOutline
                                    DownloadStatus.NOT_DOWNLOADED -> Icons.Filled.Download
                                },
                                contentDescription = null,
                                tint = when (downloadStatus) {
                                    DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
                                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = NaviromStrings.get("download_this_song_btn", appLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (downloadStatus) {
                                DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
                                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.tertiary
                                DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                DownloadStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when (downloadStatus) {
                                    DownloadStatus.DOWNLOADED -> "Offline ✓"
                                    DownloadStatus.DOWNLOADING -> "${((downloadProgress ?: 0f) * 100).toInt()}%"
                                    DownloadStatus.FAILED -> "Fehler"
                                    DownloadStatus.NOT_DOWNLOADED -> "Online"
                                },
                                color = when (downloadStatus) {
                                    DownloadStatus.NOT_DOWNLOADED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    when (downloadStatus) {
                        DownloadStatus.NOT_DOWNLOADED -> {
                            Text(
                                text = NaviromStrings.get("download_this_song_desc", appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    haptics.click()
                                    onDownloadTrack()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("download_this_song_btn")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = NaviromStrings.get("download_this_song_btn", appLanguage),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DownloadStatus.DOWNLOADING -> {
                            Text(
                                text = NaviromStrings.get("download_status_downloading", appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val progressVal = downloadProgress ?: 0f
                            LinearProgressIndicator(
                                progress = { progressVal.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        DownloadStatus.DOWNLOADED -> {
                            Text(
                                text = NaviromStrings.get("download_status_downloaded", appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.click()
                                        onDownloadTrack()
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = NaviromStrings.get("download_status_redownload", appLanguage),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        DownloadStatus.FAILED -> {
                            Text(
                                text = NaviromStrings.get("download_status_failed", appLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )

                            Button(
                                onClick = {
                                    haptics.click()
                                    onDownloadTrack()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Erneut versuchen", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. LYRICS SHORTCUT
            // ==========================================
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                    onOpenLyrics()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("cover_sheet_lyrics_btn")
            ) {
                Icon(Icons.Filled.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = NaviromStrings.get("cover_open_lyrics", appLanguage),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
