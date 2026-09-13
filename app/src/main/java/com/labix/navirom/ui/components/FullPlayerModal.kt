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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import android.os.SystemClock
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.isActive
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.labix.navirom.data.lyrics.LyricsData
import com.labix.navirom.data.lyrics.LyricsSource
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.data.model.RepeatMode
import com.labix.navirom.data.model.SleepTimerOptions
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
    isVinylEffectEnabled: Boolean = false,
    sleepTimerOptions: SleepTimerOptions = SleepTimerOptions(),
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
    onSetSleepTimer: (Int?, SleepTimerOptions) -> Unit,
    onUpdateSleepTimerOptions: ((SleepTimerOptions) -> Unit)? = null,
    onRefetchLyrics: () -> Unit = {},
    onFetchTeksteShqipLyrics: ((String?) -> Unit)? = null,
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
    var showArtistsDialog by remember { mutableStateOf(false) }
    var showTeksteShqipDialog by remember { mutableStateOf(false) }
    var teksteShqipUrlInput by remember { mutableStateOf("") }
    var customMinutesText by remember { mutableStateOf("30") }

    val isTimerActive = (playbackState.sleepTimerSecondsLeft != null && playbackState.sleepTimerSecondsLeft > 0) ||
            (playbackState.sleepTimerMinutesLeft != null && playbackState.sleepTimerMinutesLeft > 0)

    val activeOptions = playbackState.sleepTimerOptions
    var disableBt by remember(activeOptions.disableBluetooth, sleepTimerOptions.disableBluetooth) {
        mutableStateOf(activeOptions.disableBluetooth || sleepTimerOptions.disableBluetooth)
    }
    var disableWifi by remember(activeOptions.disableWifi, sleepTimerOptions.disableWifi) {
        mutableStateOf(activeOptions.disableWifi || sleepTimerOptions.disableWifi)
    }
    var disableData by remember(activeOptions.disableMobileData, sleepTimerOptions.disableMobileData) {
        mutableStateOf(activeOptions.disableMobileData || sleepTimerOptions.disableMobileData)
    }

    val context = LocalContext.current
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                NaviromStrings.get("permission_bluetooth_needed", appLanguage),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val checkAndRequestBtPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var vinylRotationAngle by remember { mutableFloatStateOf(0f) }
    var isVinylScratching by remember { mutableStateOf(false) }
    var vinylScratchVelocity by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playbackState.isPlaying, isVinylEffectEnabled, viewMode, isLifecycleResumed, isVinylScratching) {
        if (isVinylEffectEnabled && viewMode == PlayerViewMode.ARTWORK && isLifecycleResumed) {
            val normalSpeed = 360f / 16f // 22.5 deg/sec at standard vinyl rotation
            val targetSpeed = if (playbackState.isPlaying) normalSpeed else 0f
            if (!isVinylScratching && vinylScratchVelocity == 0f && playbackState.isPlaying) {
                vinylScratchVelocity = targetSpeed
            }
            var lastTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    val dtSeconds = ((frameTimeNanos - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    lastTime = frameTimeNanos
                    if (!isVinylScratching) {
                        if (kotlin.math.abs(vinylScratchVelocity - targetSpeed) > 0.5f) {
                            // Realistic turntable slipmat friction decay towards target rotational speed
                            val friction = 3.5f
                            val decay = kotlin.math.exp(-friction * dtSeconds)
                            vinylScratchVelocity = vinylScratchVelocity * decay + targetSpeed * (1f - decay)
                        } else {
                            vinylScratchVelocity = targetSpeed
                        }

                        if (vinylScratchVelocity != 0f) {
                            vinylRotationAngle = (vinylRotationAngle + vinylScratchVelocity * dtSeconds) % 360f
                            if (vinylRotationAngle < 0f) vinylRotationAngle += 360f
                        }
                    }
                }
            }
        }
    }

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

    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFF1E1E1E)
    val cardColor = if (isDark) Color(0xFF000000) else Color.White
    val textOnCard = if (isDark) Color.White else Color.Black
    val textMutedOnCard = if (isDark) Color.LightGray else Color.Gray

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
            val currentOptions = SleepTimerOptions(
                disableBluetooth = disableBt,
                disableWifi = disableWifi,
                disableMobileData = disableData
            )

            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                icon = {
                    Icon(
                        imageVector = if (isTimerActive) Icons.Filled.HourglassTop else Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (isTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = NaviromStrings.get("sleep_timer", appLanguage),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        if (isTimerActive && (playbackState.sleepTimerSecondsLeft ?: 0) > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    val totalSecs = playbackState.sleepTimerSecondsLeft ?: 0
                                    val mins = totalSecs / 60
                                    val secs = totalSecs % 60
                                    Text(
                                        text = "${NaviromStrings.get("sleep_timer_active_badge", appLanguage)}: %02d:%02d".format(mins, secs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = NaviromStrings.get("sleep_timer_desc", appLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Presets Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                val isSelected = isTimerActive && (playbackState.sleepTimerMinutesLeft == mins)
                                FilledTonalButton(
                                    onClick = {
                                        haptics.click()
                                        if (disableBt) {
                                            checkAndRequestBtPermission()
                                        }
                                        val opt = currentOptions.copy(
                                            disableBluetooth = disableBt,
                                            disableWifi = disableWifi,
                                            disableMobileData = disableData
                                        )
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                        onSetSleepTimer(mins, opt)
                                        showSleepTimerDialog = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = if (isSelected) {
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        // Custom Minutes Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customMinutesText,
                                onValueChange = { customMinutesText = it.filter { ch -> ch.isDigit() }.take(4) },
                                label = { Text(NaviromStrings.get("sleep_timer_custom_min", appLanguage)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val mins = customMinutesText.toIntOrNull()
                                    if (mins != null && mins > 0) {
                                        haptics.click()
                                        if (disableBt) {
                                            checkAndRequestBtPermission()
                                        }
                                        val opt = currentOptions.copy(
                                            disableBluetooth = disableBt,
                                            disableWifi = disableWifi,
                                            disableMobileData = disableData
                                        )
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                        onSetSleepTimer(mins, opt)
                                        showSleepTimerDialog = false
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text(NaviromStrings.get("sleep_timer_set_custom", appLanguage), fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Action Toggles Section Header
                        Text(
                            text = NaviromStrings.get("sleep_timer_extra_actions", appLanguage),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Bluetooth Toggle Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.toggle()
                                        val newBt = !disableBt
                                        if (newBt) {
                                            checkAndRequestBtPermission()
                                        }
                                        disableBt = newBt
                                        val opt = currentOptions.copy(disableBluetooth = newBt)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (disableBt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Bluetooth,
                                            contentDescription = null,
                                            tint = if (disableBt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_bluetooth", appLanguage),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_bluetooth_desc", appLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = disableBt,
                                    onCheckedChange = {
                                        haptics.toggle()
                                        if (it) {
                                            checkAndRequestBtPermission()
                                        }
                                        disableBt = it
                                        val opt = currentOptions.copy(disableBluetooth = it)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                )
                            }
                        }

                        // Wi-Fi Toggle Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.toggle()
                                        val newWifi = !disableWifi
                                        disableWifi = newWifi
                                        val opt = currentOptions.copy(disableWifi = newWifi)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (disableWifi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Wifi,
                                            contentDescription = null,
                                            tint = if (disableWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_wifi", appLanguage),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_wifi_desc", appLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = disableWifi,
                                    onCheckedChange = {
                                        haptics.toggle()
                                        disableWifi = it
                                        val opt = currentOptions.copy(disableWifi = it)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                )
                            }
                        }

                        // Mobile Data Toggle Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.toggle()
                                        val newData = !disableData
                                        disableData = newData
                                        val opt = currentOptions.copy(disableMobileData = newData)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (disableData) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.SignalCellularAlt,
                                            contentDescription = null,
                                            tint = if (disableData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_mobile_data", appLanguage),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = NaviromStrings.get("sleep_timer_disable_mobile_data_desc", appLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = disableData,
                                    onCheckedChange = {
                                        haptics.toggle()
                                        disableData = it
                                        val opt = currentOptions.copy(disableMobileData = it)
                                        onUpdateSleepTimerOptions?.invoke(opt)
                                    }
                                )
                            }
                        }

                        // Turn off button if active
                        if (isTimerActive) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.click()
                                    onSetSleepTimer(null, currentOptions)
                                    showSleepTimerDialog = false
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TimerOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = NaviromStrings.get("sleep_timer_turn_off", appLanguage),
                                    fontWeight = FontWeight.Bold
                                )
                            }
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

        // Multi-artist selection popup
        val artistList = remember(track.artist) {
            track.artist.split(Regex("[,&/]|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        if (showArtistsDialog) {
            AlertDialog(
                onDismissRequest = { showArtistsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Track Artists",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        artistList.forEach { artistName ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showArtistsDialog = false
                                        onArtistClick?.invoke(track.artistId)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showArtistsDialog = false }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (showTeksteShqipDialog) {
            AlertDialog(
                onDismissRequest = { showTeksteShqipDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("TeksteShqip")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Fetch lyrics from TeksteShqip.com or enter a song URL (e.g. for Leonora Jakupi).",
                            fontSize = 13.sp,
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
                                onFetchTeksteShqipLyrics?.invoke(url)
                                showTeksteShqipDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (teksteShqipUrlInput.isNotBlank()) "Fetch Lyrics" else "Auto-Search TeksteShqip")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTeksteShqipDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp)
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
                val currentLyricLineText = remember(currentPosMs, validSyncedLines, lyricsData.plainLyrics) {
                    if (validSyncedLines.isNotEmpty()) {
                        val idx = validSyncedLines.indexOfLast { (it.timeMs - 500L) <= currentPosMs }
                        if (idx >= 0) validSyncedLines[idx].text else validSyncedLines.firstOrNull()?.text ?: ""
                    } else if (lyricsData.plainLyrics.isNotBlank()) {
                        lyricsData.plainLyrics.lines().firstOrNull { it.isNotBlank() } ?: ""
                    } else ""
                }

                // Main Artwork View
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Card
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
                            // Top Bar: Back | Song Index | Saved (Heart) Button
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

                                // Saved / Favorite Icon at Top Right
                                val savedFavScale by animateFloatAsState(
                                    targetValue = if (isFavorite) 1.25f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "topSavedFavScale"
                                )
                                IconButton(
                                    onClick = {
                                        haptics.click()
                                        onToggleFavorite()
                                    },
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = savedFavScale
                                        scaleY = savedFavScale
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Saved",
                                        tint = if (isFavorite) Color(0xFFE91E63) else textOnCard,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Artwork breathing scale based on playback state
                            val artworkScale by animateFloatAsState(
                                targetValue = if (playbackState.isPlaying) 1.0f else 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "artworkScale"
                            )

                            // Artwork with vinyl effect - vinyl rotates cleanly without overlaid text
                            val artworkShape = if (isVinylEffectEnabled) CircleShape else RoundedCornerShape(32.dp)

                            // Platter depression spring animation while vinyl is being scratched
                            val vinylPlatterScale by animateFloatAsState(
                                targetValue = if (isVinylScratching) 0.982f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "vinylPlatterScale"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .then(
                                        if (isVinylEffectEnabled) {
                                            Modifier.pointerInput(Unit) {
                                                val center = Offset(size.width / 2f, size.height / 2f)
                                                val maxRadius = size.width / 2f
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val downTimeMs = SystemClock.uptimeMillis()
                                                    val initialDist = (down.position - center).getDistance()
                                                    if (initialDist <= maxRadius) {
                                                        var prevPos = down.position
                                                        var prevAngleRad = kotlin.math.atan2(prevPos.y - center.y, prevPos.x - center.x)
                                                        var totalAngularTravel = 0f
                                                        var totalLinearTravel = 0f
                                                        var isScratchConfirmed = false
                                                        var lastTimeMs = downTimeMs
                                                        var currentAngularVelocity = 0f
                                                        var accumulatedHapticAngle = 0f

                                                        do {
                                                            val event = awaitPointerEvent()
                                                            val currentChange = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (currentChange.pressed) {
                                                                val curPos = currentChange.position
                                                                val now = SystemClock.uptimeMillis()
                                                                val dt = (now - lastTimeMs).coerceAtLeast(1) / 1000f
                                                                lastTimeMs = now

                                                                val distFromCenter = (curPos - center).getDistance()
                                                                if (distFromCenter > 20f) {
                                                                    val curAngleRad = kotlin.math.atan2(curPos.y - center.y, curPos.x - center.x)
                                                                    var deltaRad = curAngleRad - prevAngleRad
                                                                    while (deltaRad > Math.PI) deltaRad -= (2 * Math.PI).toFloat()
                                                                    while (deltaRad < -Math.PI) deltaRad += (2 * Math.PI).toFloat()
                                                                    val deltaDegrees = Math.toDegrees(deltaRad.toDouble()).toFloat()

                                                                    totalAngularTravel += kotlin.math.abs(deltaDegrees)
                                                                    totalLinearTravel += (curPos - prevPos).getDistance()

                                                                    if (!isScratchConfirmed && (totalAngularTravel > 2.0f || totalLinearTravel > 8f)) {
                                                                        isScratchConfirmed = true
                                                                        isVinylScratching = true
                                                                    }

                                                                    if (isScratchConfirmed) {
                                                                        currentChange.consume()
                                                                        vinylRotationAngle = (vinylRotationAngle + deltaDegrees) % 360f
                                                                        if (vinylRotationAngle < 0f) vinylRotationAngle += 360f

                                                                        val instVelocity = deltaDegrees / dt
                                                                        currentAngularVelocity = currentAngularVelocity * 0.35f + instVelocity * 0.65f

                                                                        accumulatedHapticAngle += kotlin.math.abs(deltaDegrees)
                                                                        if (accumulatedHapticAngle >= 14f) {
                                                                            haptics.tick()
                                                                            accumulatedHapticAngle = 0f
                                                                        }
                                                                    }
                                                                    prevAngleRad = curAngleRad
                                                                }
                                                                prevPos = curPos
                                                            }
                                                        } while (event.changes.any { it.pressed })

                                                        if (isScratchConfirmed) {
                                                            isVinylScratching = false
                                                            vinylScratchVelocity = currentAngularVelocity.coerceIn(-1800f, 1800f)
                                                        } else {
                                                            val durationMs = SystemClock.uptimeMillis() - downTimeMs
                                                            if (durationMs > 500L && onAlbumClick != null) {
                                                                haptics.click()
                                                                onAlbumClick.invoke(track.albumId)
                                                            } else {
                                                                haptics.toggle()
                                                                viewMode = PlayerViewMode.LYRICS
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .graphicsLayer {
                                        scaleX = artworkScale * (if (isVinylEffectEnabled) vinylPlatterScale else 1.0f)
                                        scaleY = artworkScale * (if (isVinylEffectEnabled) vinylPlatterScale else 1.0f)
                                        if (isVinylEffectEnabled) {
                                            rotationZ = vinylRotationAngle
                                        }
                                    }
                                    .clip(artworkShape)
                            ) {
                                SongAlbumCover(
                                    coverArtUrl = track.coverArtUrl,
                                    contentDescription = "Album Artwork",
                                    isAlbum = false,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (!isVinylEffectEnabled) {
                                                Modifier
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
                                            } else {
                                                Modifier
                                            }
                                        )
                                )

                                if (isVinylEffectEnabled) {
                                    // Concentric micro-groove ring 1
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.80f)
                                            .align(Alignment.Center)
                                            .border(
                                                BorderStroke(0.75.dp, Color.White.copy(alpha = 0.12f)),
                                                CircleShape
                                            )
                                    )
                                    // Concentric micro-groove ring 2
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.60f)
                                            .align(Alignment.Center)
                                            .border(
                                                BorderStroke(0.75.dp, Color.White.copy(alpha = 0.09f)),
                                                CircleShape
                                            )
                                    )
                                    // Subtle rim edge ring
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(
                                                BorderStroke(3.dp, Color.Black.copy(alpha = 0.35f)),
                                                CircleShape
                                            )
                                    )
                                    // Center spindle hole & label ring
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.75f)), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(if (isDark) Color.Black else Color(0xFF1E1E1E), CircleShape)
                                                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)), CircleShape)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Title & Artist (Full Width)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                AnimatedContent(
                                    targetState = track.id,
                                    transitionSpec = {
                                        (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 })
                                            .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
                                    },
                                    label = "fullPlayerTrackTitleAnim",
                                    modifier = Modifier.fillMaxWidth()
                                ) { _ ->
                                    Column {
                                        Text(
                                            text = track.title,
                                            color = textOnCard,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = track.artist,
                                            color = textMutedOnCard,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                if (artistList.size > 1) {
                                                    showArtistsDialog = true
                                                } else {
                                                    onArtistClick?.invoke(track.artistId)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Playback Controls
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
                                        AnimatedContent(
                                            targetState = playbackState.isPlaying,
                                            transitionSpec = {
                                                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(160)))
                                                    .togetherWith(scaleOut() + fadeOut(tween(100)))
                                            },
                                            label = "fullPlayPauseAnim"
                                        ) { isPlaying ->
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = textOnCard,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
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

                            // 1-Line Lyric Preview below control buttons
                            if (currentLyricLineText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDark) Color(0xFF161616) else Color(0xFFF3F4F6),
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptics.toggle()
                                            viewMode = PlayerViewMode.LYRICS
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = currentLyricLineText,
                                            transitionSpec = {
                                                (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 })
                                                    .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 })
                                            },
                                            label = "LyricPreviewBelowControls",
                                            modifier = Modifier.fillMaxWidth()
                                        ) { lineText ->
                                            AutoResizingSingleLineLyric(
                                                text = lineText,
                                                color = textOnCard
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Dark Section: Lyrics Mic Button | [Artist, Album, Sleep Timer Countdown] | Queue Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lyrics button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF3D959), // Yellow
                            modifier = Modifier.size(48.dp).clickable {
                                haptics.toggle()
                                viewMode = PlayerViewMode.LYRICS
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Mic, contentDescription = "Lyrics", tint = Color.Black)
                            }
                        }

                        // Center Actions: Artist (single or multi popup), Album, Sleep Timer (with countdown)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Artist Action Button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2C2C2C),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        haptics.click()
                                        if (artistList.size > 1) {
                                            showArtistsDialog = true
                                        } else {
                                            onArtistClick?.invoke(track.artistId)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (artistList.size > 1) Icons.Filled.People else Icons.Filled.Person,
                                        contentDescription = "Artist",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 2. Album Action Button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2C2C2C),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable {
                                        haptics.click()
                                        onAlbumClick?.invoke(track.albumId)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Album,
                                        contentDescription = "Album",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // 3. Sleep Timer Action Button with Live Countdown
                            val isTimerActive = (playbackState.sleepTimerSecondsLeft != null && playbackState.sleepTimerSecondsLeft > 0) ||
                                    (playbackState.sleepTimerMinutesLeft != null && playbackState.sleepTimerMinutesLeft > 0)

                            val timerCountdownText = remember(playbackState.sleepTimerSecondsLeft, playbackState.sleepTimerMinutesLeft) {
                                val totalSecs = playbackState.sleepTimerSecondsLeft ?: (playbackState.sleepTimerMinutesLeft?.times(60) ?: 0)
                                if (totalSecs <= 0) ""
                                else {
                                    val mins = totalSecs / 60
                                    val secs = totalSecs % 60
                                    "%02d:%02d".format(mins, secs)
                                }
                            }

                            Surface(
                                shape = if (isTimerActive) RoundedCornerShape(22.dp) else CircleShape,
                                color = if (isTimerActive) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2C),
                                modifier = Modifier
                                    .height(44.dp)
                                    .then(if (isTimerActive) Modifier.padding(horizontal = 2.dp) else Modifier.width(44.dp))
                                    .clickable {
                                        haptics.click()
                                        showSleepTimerDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = if (isTimerActive) 12.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isTimerActive) Icons.Filled.HourglassTop else Icons.Filled.Timer,
                                        contentDescription = "Sleep Timer",
                                        tint = if (isTimerActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    if (isTimerActive && timerCountdownText.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = timerCountdownText,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Queue button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2C2C2C),
                            modifier = Modifier.size(48.dp).clickable {
                                haptics.click()
                                onOpenQueue()
                            }
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
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Persistent Mini Player Card (always visible on top of lyrics)
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
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.click()
                                        viewMode = PlayerViewMode.ARTWORK
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Player",
                                        tint = textOnCard,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                SongAlbumCover(
                                    coverArtUrl = track.coverArtUrl,
                                    contentDescription = "Cover",
                                    isAlbum = false,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clickable {
                                            haptics.click()
                                            viewMode = PlayerViewMode.ARTWORK
                                        }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            haptics.click()
                                            viewMode = PlayerViewMode.ARTWORK
                                        }
                                ) {
                                    Text(
                                        text = track.title,
                                        color = textOnCard,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = textMutedOnCard,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Playback controls in the top player header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptics.click()
                                            onPrevious()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipPrevious,
                                            contentDescription = "Previous",
                                            tint = textOnCard,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = textOnCard,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptics.click()
                                                onTogglePlayPause()
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                                tint = cardColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            haptics.click()
                                            onNext()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = "Next",
                                            tint = textOnCard,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onToggleFavorite,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Favorite",
                                            tint = if (isFavorite) Color(0xFF1DB954) else textMutedOnCard,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = {
                                                haptics.click()
                                                showMenu = true
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = "More",
                                                tint = textMutedOnCard,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Reload Lyrics") },
                                                onClick = {
                                                    showMenu = false
                                                    onRefetchLyrics()
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                                            )
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
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subtle progress indicator
                            val progressFraction = if (totalDurationMs > 0L) {
                                (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFFF3D959),
                                trackColor = textMutedOnCard.copy(alpha = 0.2f)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Drag handle to slide back down
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(textMutedOnCard.copy(alpha = 0.35f))
                                    .clickable {
                                        haptics.toggle()
                                        viewMode = PlayerViewMode.ARTWORK
                                    }
                            )
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
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MusicOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Lyrics not available",
                                    color = Color.LightGray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = onRefetchLyrics,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry", fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (teksteShqipUrlInput.isBlank()) {
                                                if (track.artist.contains("leonora", ignoreCase = true) ||
                                                    track.title.contains("vritet", ignoreCase = true)
                                                ) {
                                                    teksteShqipUrlInput = "https://teksteshqip.com/leonora-jakupi/teksti/1848928"
                                                }
                                            }
                                            showTeksteShqipDialog = true
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TeksteShqip", fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // Top indicator bar with source and TeksteShqip button
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    val srcText = when (lyricsData.source) {
                                        LyricsSource.EMBEDDED_FILE -> "Embedded in File"
                                        LyricsSource.NAVIDROME_SERVER -> "Navidrome Server"
                                        LyricsSource.ONLINE_LRCLIB -> "LrcLib Synced"
                                        LyricsSource.ONLINE_TEKSTESHQIP -> "TeksteShqip"
                                        LyricsSource.NOT_FOUND -> ""
                                    }
                                    Text(
                                        text = srcText,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier.clickable {
                                        if (teksteShqipUrlInput.isBlank()) {
                                            if (track.artist.contains("leonora", ignoreCase = true) ||
                                                track.title.contains("vritet", ignoreCase = true)
                                            ) {
                                                teksteShqipUrlInput = "https://teksteshqip.com/leonora-jakupi/teksti/1848928"
                                            }
                                        }
                                        showTeksteShqipDialog = true
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Language,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "TeksteShqip",
                                            color = Color(0xFFFFD54F),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
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

@Composable
private fun AutoResizingSingleLineLyric(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp
) {
    val estimatedSize = remember(text) {
        val len = text.length
        when {
            len <= 16 -> maxFontSize
            len <= 26 -> 16.sp
            len <= 38 -> 14.5.sp
            len <= 52 -> 13.sp
            len <= 70 -> 11.sp
            else -> minFontSize
        }
    }
    var fontSize by remember(text) { mutableStateOf(estimatedSize) }

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        onTextLayout = { layoutResult ->
            if (layoutResult.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value - 0.8f).coerceAtLeast(minFontSize.value).sp
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
