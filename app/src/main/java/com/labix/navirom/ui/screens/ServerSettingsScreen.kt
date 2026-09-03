package com.labix.navirom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labix.R
import com.labix.navirom.data.stats.ListeningStatsSummary
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.AppThemeMode
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.ServerConnectionUiState
import com.labix.ui.theme.AccentEmerald

import com.labix.navirom.diagnostics.AppDiagnostics
import com.labix.navirom.ui.components.DebugLogsDialog
import com.labix.navirom.ui.components.AppUpdateDialog
import com.labix.navirom.update.AppUpdateInfo
import com.labix.navirom.update.UpdateState
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    serverState: ServerConnectionUiState,
    allServers: List<com.labix.navirom.data.local.ServerConfigEntity>,
    onSelectServer: (com.labix.navirom.data.local.ServerConfigEntity) -> Unit,
    appLanguage: AppLanguage,
    appThemeMode: AppThemeMode,
    isCrossfadeEnabled: Boolean = false,
    crossfadeDurationSeconds: Int = 5,
    onSetCrossfadeEnabled: (Boolean) -> Unit = {},
    onSetCrossfadeDurationSeconds: (Int) -> Unit = {},
    statsSummary: ListeningStatsSummary = ListeningStatsSummary(),
    onViewStats: () -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onUpdateConfig: (protocol: String, host: String, port: String, user: String, pass: String, tokenAuth: Boolean, alternativeHost: String) -> Unit,
    onConnect: () -> Unit,
    onSyncLibrary: () -> Unit,
    onScanNetwork: () -> Unit,
    onSelectMusicFolder: (String?) -> Unit = {},
    onToggleMusicFolder: (String) -> Unit = {},
    onSelectAllMusicFolders: () -> Unit = {},
    focusUsernameTrigger: Long = 0L,
    updateState: UpdateState = UpdateState.Idle,
    autoCheckUpdates: Boolean = true,
    githubRepo: String = "labibllaca/Zana-player",
    lastUpdateCheckedTime: Long = 0L,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: (AppUpdateInfo) -> Unit = {},
    onSetAutoCheckUpdates: (Boolean) -> Unit = {},
    onSetGithubRepo: (String) -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = rememberNaviromHaptics()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val usernameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusUsernameTrigger) {
        if (focusUsernameTrigger > 0L) {
            try {
                usernameFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    var protocol by remember(serverState.protocol) { mutableStateOf(serverState.protocol) }
    var host by remember(serverState.host) { mutableStateOf(serverState.host) }
    var port by remember(serverState.port) { mutableStateOf(serverState.port) }
    var username by remember(serverState.username) { mutableStateOf(serverState.username) }
    var password by remember(serverState.password) { mutableStateOf(serverState.password) }
    var useTokenAuth by remember(serverState.useTokenAuth) { mutableStateOf(serverState.useTokenAuth) }
    var alternativeHost by remember(serverState.alternativeHost) { mutableStateOf(serverState.alternativeHost) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var protocolExpanded by remember { mutableStateOf(false) }
    var folderExpanded by remember { mutableStateOf(false) }
    var showDebugLogsDialog by remember { mutableStateOf(false) }

    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("server_settings_screen")
    ) {
        Text(
            text = str("settings_title"),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = str("server_config_title"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Text(text = if (serverState.isConnected) "Connected" else "Disconnected", modifier = Modifier.padding(16.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Multi-Server List
        if (allServers.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = str("saved_servers"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    allServers.forEach { server ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectServer(server) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (server.isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = server.name, style = MaterialTheme.typography.titleSmall)
                                    Text(text = server.serverUrl, style = MaterialTheme.typography.bodySmall)
                                }
                                if (server.isConnected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Connection Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (serverState.isConnected)
                    AccentEmerald.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (serverState.isConnected) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = if (serverState.isConnected) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (serverState.isConnected) str("server_connected") else str("server_disconnected"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (serverState.isConnected) AccentEmerald else MaterialTheme.colorScheme.onSurface
                    )
                    if (serverState.connectionStatusMessage != null) {
                        Text(
                            text = serverState.connectionStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (serverState.isConnected) {
                    FilledTonalIconButton(
                        onClick = onSyncLibrary,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = str("server_sync_now"))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // Listening Statistics & Habits Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onViewStats() }
                .testTag("settings_stats_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Insights,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = str("stats_title"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = str("stats_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats preview chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalSec = statsSummary.totalListeningSeconds
                    val hours = totalSec / 3600
                    val minutes = (totalSec % 3600) / 60
                    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = str("stats_total_listening"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = str("stats_peak_day"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (appLanguage == AppLanguage.ALBANIAN) statsSummary.peakDaySq else statsSummary.peakDayEn,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = str("stats_peak_hours"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = statsSummary.peakHourRange,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onViewStats,
                    modifier = Modifier.fillMaxWidth().testTag("view_stats_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(str("stats_btn_view"))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Appearance & Language Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = str("appearance_title"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Theme Mode Selector
                Text(
                    text = str("theme_mode"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = appThemeMode == mode
                        val label = when (mode) {
                            AppThemeMode.DARK -> str("theme_dark")
                            AppThemeMode.LIGHT -> str("theme_light")
                            AppThemeMode.SYSTEM -> str("theme_system")
                        }
                        val icon = when (mode) {
                            AppThemeMode.DARK -> Icons.Filled.DarkMode
                            AppThemeMode.LIGHT -> Icons.Filled.LightMode
                            AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            onClick = {
                                haptics.tick()
                                onSetThemeMode(mode)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Language Selector
                Text(
                    text = str("language"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = appLanguage == lang
                        val flagRes = when (lang) {
                            AppLanguage.ALBANIAN -> R.drawable.ic_flag_albania
                            AppLanguage.ENGLISH -> R.drawable.ic_flag_uk
                            AppLanguage.GERMAN -> R.drawable.ic_flag_germany
                        }
                        val flagDesc = when (lang) {
                            AppLanguage.ALBANIAN -> "Shqip (Albanian)"
                            AppLanguage.ENGLISH -> "English"
                            AppLanguage.GERMAN -> "Deutsch (German)"
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("lang_flag_${lang.code}"),
                            onClick = {
                                haptics.tick()
                                onSetLanguage(lang)
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(flagRes),
                                    contentDescription = flagDesc,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(20.dp))

        // Playback Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = str("settings_crossfade"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCrossfadeEnabled) "${crossfadeDurationSeconds}s ${str("settings_crossfade_seconds")}" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCrossfadeEnabled,
                        onCheckedChange = { onSetCrossfadeEnabled(it) }
                    )
                }

                if (isCrossfadeEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str("settings_crossfade_duration"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "1 - 20 ${str("settings_crossfade_seconds")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (crossfadeDurationSeconds > 1) {
                                        onSetCrossfadeDurationSeconds(crossfadeDurationSeconds - 1)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                            }

                            var textInput by remember(crossfadeDurationSeconds) { mutableStateOf(crossfadeDurationSeconds.toString()) }
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    textInput = filtered
                                    filtered.toIntOrNull()?.let { sec ->
                                        if (sec in 1..20) {
                                            onSetCrossfadeDurationSeconds(sec)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.width(64.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    if (crossfadeDurationSeconds < 20) {
                                        onSetCrossfadeDurationSeconds(crossfadeDurationSeconds + 1)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Multi-Select Library Filter (if server has music folders)
        if (serverState.musicFolders.isNotEmpty()) {
            val isAllSelected = serverState.selectedMusicFolderIds.isEmpty() || (serverState.selectedMusicFolderIds.size >= serverState.musicFolders.size)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str("library_filter"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isAllSelected) {
                    TextButton(onClick = onSelectAllMusicFolders) {
                        Text(str("select_all_libraries"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Master All Libraries option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        onClick = onSelectAllMusicFolders,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.AllInclusive, contentDescription = null, tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(str("all_libraries_combined"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Checkbox(checked = isAllSelected, onCheckedChange = { onSelectAllMusicFolders() })
                        }
                    }
                    // Individual Folders
                    serverState.musicFolders.forEach { folder ->
                        val isChecked = !isAllSelected && serverState.selectedMusicFolderIds.contains(folder.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            onClick = { onToggleMusicFolder(folder.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column {
                                        Text(folder.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("Folder ID: ${folder.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Checkbox(checked = isChecked, onCheckedChange = { onToggleMusicFolder(folder.id) })
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }



        // Network Auto-Scan / Stop Auto-Scan Button
        Button(
            onClick = {
                haptics.click()
                onScanNetwork()
                try {
                    usernameFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
            },
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serverState.isConnecting) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (serverState.isConnecting) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(
                imageVector = if (serverState.isConnecting) Icons.Filled.Stop else Icons.Filled.WifiFind,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (serverState.isConnecting) str("btn_stop_auto_scan") else str("btn_auto_scan"))
        }

        // Visible Scan / Network Offline Banner
        if (serverState.connectionStatusMessage != null) {
            val isOfflineWarning = serverState.connectionStatusMessage.contains("⚠️") ||
                    serverState.connectionStatusMessage.contains("No network connection", ignoreCase = true) ||
                    serverState.connectionStatusMessage.contains("Nuk ka lidhje", ignoreCase = true) ||
                    serverState.connectionStatusMessage.contains("offline", ignoreCase = true)

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isOfflineWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    serverState.isConnecting -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOfflineWarning) {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (serverState.isConnecting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        if (isOfflineWarning) {
                            Text(
                                text = str("scan_no_network_title"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = serverState.connectionStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOfflineWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server Credentials Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = str("server_config_title"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Protocol and Host
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = protocolExpanded,
                        onExpandedChange = { protocolExpanded = !protocolExpanded },
                        modifier = Modifier.weight(0.35f)
                    ) {
                        OutlinedTextField(
                            value = protocol,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(str("protocol")) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = protocolExpanded,
                            onDismissRequest = { protocolExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("http") },
                                onClick = {
                                    protocol = "http"
                                    protocolExpanded = false
                                    onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("https") },
                                onClick = {
                                    protocol = "https"
                                    protocolExpanded = false
                                    onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it
                            onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                        },
                        label = { Text(str("host")) },
                        placeholder = { Text(str("host_placeholder")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.weight(0.65f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                    },
                    label = { Text(str("port")) },
                    placeholder = { Text(str("port_placeholder")) },
                    leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = alternativeHost,
                    onValueChange = {
                        alternativeHost = it
                        onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                    },
                    label = { Text(if (appLanguage == AppLanguage.ALBANIAN) "Host/IP Alternativ (p.sh. jashtë shtëpisë)" else "Alternative Host/IP (e.g. cellular)") },
                    placeholder = { Text("e.g. 10.x.x.x or remote.domain.com") },
                    leadingIcon = { Icon(Icons.Filled.CellTower, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                    },
                    label = { Text(str("username")) },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .testTag("server_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                    },
                    label = { Text(str("password")) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onConnect()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Token Auth Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("use_token_auth"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = str("use_token_auth_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = useTokenAuth,
                        onCheckedChange = {
                            useTokenAuth = it
                            onUpdateConfig(protocol, host, port, username, password, useTokenAuth, alternativeHost)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Connect Button
        Button(
            onClick = {
                haptics.click()
                onConnect()
            },
            enabled = !serverState.isConnecting && host.isNotBlank() && username.isNotBlank(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("test_server_connect_btn")
        ) {
            if (serverState.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(str("btn_connecting"))
            } else {
                Icon(Icons.Filled.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(str("btn_connect"))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Diagnostics & Debug Logs Card
        val logsList by AppDiagnostics.logsFlow.collectAsState()
        val totalLogCount = logsList.size
        val errorLogCount = remember(logsList) { logsList.count { it.level == com.labix.navirom.diagnostics.LogLevel.ERROR } }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnostics & Debug Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (errorLogCount > 0) "$errorLogCount error(s) logged ($totalLogCount total)" else "$totalLogCount log entry/entries recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (errorLogCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showDebugLogsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Logs", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Updates Card
        var isEditingRepo by remember { mutableStateOf(false) }
        var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("app_updates_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = str("updates_section_title"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${str("updates_current_version")}: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(14.dp))

                // Auto-check switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("updates_auto_check"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = str("updates_auto_check_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCheckUpdates,
                        onCheckedChange = { onSetAutoCheckUpdates(it) },
                        modifier = Modifier.testTag("auto_update_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // GitHub Repository Slug Setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("updates_github_repo"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isEditingRepo) {
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onSetGithubRepo(repoInput)
                                        isEditingRepo = false
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = "Save")
                                    }
                                }
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clickable {
                                        try {
                                            val releasesUrl = "https://github.com/$githubRepo/releases"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "https://github.com/$githubRepo/releases",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Outlined.OpenInBrowser,
                                    contentDescription = "Open Releases",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (!isEditingRepo) {
                        TextButton(onClick = { isEditingRepo = true }) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (lastUpdateCheckedTime > 0L) {
                    val lastDateStr = remember(lastUpdateCheckedTime) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        sdf.format(Date(lastUpdateCheckedTime))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${str("updates_last_checked")}: $lastDateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Update Status or Check Button
                when (updateState) {
                    is UpdateState.Checking -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(str("updates_checking"))
                        }
                    }
                    is UpdateState.Available -> {
                        val info = updateState.updateInfo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NewReleases,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${str("updates_new_available_title")} (${info.tagName})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (info.body.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = info.body.take(150) + if (info.body.length > 150) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        maxLines = 3
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onDownloadAndInstallUpdate(info) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(str("updates_install_btn"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is UpdateState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${str("updates_downloading")} (${(updateState.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                haptics.click()
                                onCheckForUpdates()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_check_app_updates")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(str("updates_check_btn"))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Zana v${com.labix.BuildConfig.VERSION_NAME} (Build ${com.labix.BuildConfig.VERSION_CODE}) • Android Auto Ready",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (appLanguage == AppLanguage.ALBANIAN)
                        "Klient muzikor modern për serverët Navidrome & Subsonic me mbështetje për Android Auto, dëgjim të drejtpërdrejtë, ruajtje pa internet, lista vetjake dhe kontrolle të lëvizjes."
                    else
                        "Modern music streaming client for Navidrome & Subsonic servers with Android Auto support, direct high-res streaming, offline caching, smart mixing, and gesture controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showDebugLogsDialog) {
            DebugLogsDialog(onDismissRequest = { showDebugLogsDialog = false })
        }
    }
}
