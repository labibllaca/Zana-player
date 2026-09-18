package com.labix.navirom.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labix.navirom.data.local.FolderViewContent
import com.labix.navirom.data.local.LocalAudioRepository
import com.labix.navirom.data.local.LocalMusicFolder
import com.labix.navirom.data.local.SubFolderEntry
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.data.model.NaviromTrack
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.util.rememberNaviromHaptics
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicFoldersScreen(
    currentFolderPath: String,
    allLocalTracks: List<NaviromTrack>,
    allDiscoveredFolders: List<LocalMusicFolder>,
    isScanning: Boolean,
    playbackState: PlaybackState,
    appLanguage: AppLanguage,
    onNavigateToFolder: (String) -> Unit,
    onNavigateUp: () -> Boolean,
    onRescanStorage: () -> Unit,
    onPlayTrack: (NaviromTrack, List<NaviromTrack>) -> Unit,
    onPlayFolder: (String, Boolean) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    onToggleFavorite: (NaviromTrack) -> Unit,
    favoriteIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val haptics = rememberNaviromHaptics()
    val context = LocalContext.current
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    // Compute folder view contents reactively
    val localAudioRepo = remember(context) { LocalAudioRepository(context) }
    val folderContent = remember(currentFolderPath, allLocalTracks) {
        localAudioRepo.getFolderViewContent(currentFolderPath, allLocalTracks)
    }

    val defaultMusicPath = remember(localAudioRepo) {
        localAudioRepo.getDefaultMusicDirectoryPath()
    }
    val isAtRoot = currentFolderPath.trimEnd('/') == defaultMusicPath.trimEnd('/')

    // Intercept back button when inside a subfolder
    BackHandler(enabled = !isAtRoot) {
        onNavigateUp()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTrackForDetails by remember { mutableStateOf<NaviromTrack?>(null) }
    var showRootPicker by remember { mutableStateOf(false) }

    // Filter subfolders and direct tracks according to search
    val filteredSubfolders = remember(folderContent.subfolders, searchQuery) {
        if (searchQuery.isBlank()) folderContent.subfolders
        else folderContent.subfolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredTracks = remember(folderContent.directTracks, searchQuery) {
        if (searchQuery.isBlank()) folderContent.directTracks
        else folderContent.directTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.path.contains(searchQuery, ignoreCase = true)
        }
    }

    // Rotation animation for rescan button
    val infiniteTransition = rememberInfiniteTransition(label = "scan_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_folders_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Action Controls
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = str("folders_title"),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = folderContent.currentName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Root Directory Switcher Menu Button
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    showRootPicker = true
                                },
                                modifier = Modifier.testTag("folders_root_picker_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "Switch Directory",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Rescan Button
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    onRescanStorage()
                                },
                                enabled = !isScanning,
                                modifier = Modifier.testTag("folders_rescan_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = str("folders_rescan"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = if (isScanning) Modifier.rotate(spinAngle) else Modifier
                                )
                            }
                        }
                    }

                    // Root Picker Dropdown Menu
                    DropdownMenu(
                        expanded = showRootPicker,
                        onDismissRequest = { showRootPicker = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = str("folders_root_music"),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = defaultMusicPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                haptics.click()
                                showRootPicker = false
                                onNavigateToFolder(defaultMusicPath)
                            }
                        )

                        if (allDiscoveredFolders.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            allDiscoveredFolders.forEach { discoveredFolder ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = discoveredFolder.name,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${discoveredFolder.trackCount} ${str("folders_tracks").lowercase()} • ${discoveredFolder.displayPath}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Folder, contentDescription = null)
                                    },
                                    onClick = {
                                        haptics.click()
                                        showRootPicker = false
                                        onNavigateToFolder(discoveredFolder.path)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Breadcrumbs Navigation Row
            item(key = "breadcrumbs") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Up one level button (if not at root)
                        if (!isAtRoot) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onNavigateUp()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("folders_navigate_up_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = str("folders_up"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Scrollable breadcrumbs trail
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            folderContent.breadcrumbs.forEachIndexed { index, crumb ->
                                val isLast = index == folderContent.breadcrumbs.lastIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                                    modifier = Modifier.clickable {
                                        if (!isLast) {
                                            haptics.tick()
                                            onNavigateToFolder(crumb.second)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = crumb.first,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }

                                if (!isLast) {
                                    Text(
                                        text = "/",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter
            item(key = "search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            str("folders_search_hint"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folders_search_input")
                )
            }

            // Quick Play Actions (Play All & Shuffle)
            if (folderContent.totalTracksInTree.isNotEmpty()) {
                item(key = "play_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptics.click()
                                onPlayFolder(currentFolderPath, false)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("folders_play_all_btn")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${str("folders_play_all")} (${folderContent.totalTracksInTree.size})",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.click()
                                onPlayFolder(currentFolderPath, true)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("folders_shuffle_btn")
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = str("folders_shuffle"),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Empty Folder State
            if (folderContent.subfolders.isEmpty() && folderContent.directTracks.isEmpty()) {
                item(key = "empty_state") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = str("folders_empty_title"),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentFolderPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = str("folders_empty_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    haptics.click()
                                    onRescanStorage()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(str("folders_rescan"))
                            }

                            // If other music folders were detected on the device, show shortcuts
                            if (allDiscoveredFolders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = str("folders_other_detected"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    allDiscoveredFolders.take(4).forEach { otherFolder ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    haptics.click()
                                                    onNavigateToFolder(otherFolder.path)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Folder,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = otherFolder.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${otherFolder.trackCount} tracks • ${otherFolder.displayPath}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
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

            // Subfolders Section
            if (filteredSubfolders.isNotEmpty()) {
                item(key = "subfolders_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${str("folders_subfolders")} (${filteredSubfolders.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(filteredSubfolders, key = { "subfolder_${it.path}" }) { subfolder ->
                    SubFolderCard(
                        subfolder = subfolder,
                        str = { str(it) },
                        onOpen = {
                            haptics.tick()
                            onNavigateToFolder(subfolder.path)
                        },
                        onPlay = {
                            haptics.click()
                            onPlayFolder(subfolder.path, false)
                        }
                    )
                }
            }

            // Direct Tracks Section
            if (filteredTracks.isNotEmpty()) {
                item(key = "tracks_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${str("folders_tracks")} (${filteredTracks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(filteredTracks, key = { "track_${it.id}" }) { track ->
                    val isPlayingThis = playbackState.currentTrack?.id == track.id
                    val isFavorite = favoriteIds.contains(track.id)

                    FolderTrackRow(
                        track = track,
                        isPlaying = isPlayingThis && playbackState.isPlaying,
                        isCurrentTrack = isPlayingThis,
                        isFavorite = isFavorite,
                        onClick = {
                            haptics.click()
                            onPlayTrack(track, filteredTracks)
                        },
                        onToggleFavorite = {
                            haptics.click()
                            onToggleFavorite(track)
                        },
                        onPlayNext = {
                            haptics.tick()
                            onPlayNext(track)
                        },
                        onAddToQueue = {
                            haptics.tick()
                            onAddToQueue(track)
                        },
                        onShowDetails = {
                            selectedTrackForDetails = track
                        }
                    )
                }
            }
        }
    }

    // File Details Dialog
    selectedTrackForDetails?.let { track ->
        FileDetailsDialog(
            track = track,
            str = { str(it) },
            onDismiss = { selectedTrackForDetails = null }
        )
    }
}

@Composable
fun SubFolderCard(
    subfolder: SubFolderEntry,
    str: (String) -> String,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subfolder_card_${subfolder.name}")
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subfolder.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val detailsText = buildString {
                        if (subfolder.totalTracksCount > 0) {
                            append("${subfolder.totalTracksCount} ${str("folders_tracks").lowercase()}")
                        }
                        if (subfolder.subfoldersCount > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${subfolder.subfoldersCount} ${str("folders_subfolders").lowercase()}")
                        }
                        if (isEmpty()) {
                            append(subfolder.path.substringAfterLast('/'))
                        }
                    }
                    Text(
                        text = detailsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subfolder.totalTracksCount > 0) {
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Play folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun FolderTrackRow(
    track: NaviromTrack,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("track_item_${track.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status icon or index
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isCurrentTrack) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Paused",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = track.trackNumber?.toString() ?: "•",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Format Badge
                        if (track.suffix.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = track.suffix.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Duration
                val durationFormatted = remember(track.durationSeconds) {
                    val m = track.durationSeconds / 60
                    val s = track.durationSeconds % 60
                    String.format("%d:%02d", m, s)
                }
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Favorite
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3-dots Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            leadingIcon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Queue") },
                            leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Info") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShowDetails()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileDetailsDialog(
    track: NaviromTrack,
    str: (String) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = str("folders_file_info"),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = str("folders_path"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setPrimaryClip(ClipData.newPlainText("File Path", track.path))
                                Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.path.ifBlank { track.streamUrl },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(str("folders_format"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(track.suffix.uppercase(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    if (track.sizeBytes > 0) {
                        Column {
                            Text(str("folders_size"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            val sizeMb = track.sizeBytes.toDouble() / (1024 * 1024)
                            Text(String.format("%.2f MB", sizeMb), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Column {
                        Text(str("folders_duration"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        val m = track.durationSeconds / 60
                        val s = track.durationSeconds % 60
                        Text(String.format("%d:%02d", m, s), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
