package com.labix.navirom.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.data.model.NaviromPlaylist
import com.labix.navirom.data.model.NaviromTrack
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.components.PlaylistCard
import com.labix.navirom.ui.components.TrackListItem
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.ui.theme.AccentRose

@Composable
fun PlaylistsScreen(
    playlists: List<NaviromPlaylist>,
    selectedPlaylistId: String?,
    selectedPlaylistTracks: List<NaviromTrack>,
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    favoriteIds: List<String>,
    appLanguage: AppLanguage,
    onSelectPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onTrackClick: (NaviromTrack, List<NaviromTrack>) -> Unit,
    onPlayAll: (List<NaviromTrack>) -> Unit,
    onShuffleAll: (List<NaviromTrack>) -> Unit,
    onDownloadPlaylist: (List<NaviromTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (NaviromTrack) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = selectedPlaylistId,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 })
                    .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 4 })
            }
        },
        label = "PlaylistScreenDetailTransition",
        modifier = modifier.fillMaxSize()
    ) { targetPlaylistId ->
        if (targetPlaylistId != null) {
            val selectedPlaylist = playlists.find { it.id == targetPlaylistId }
            PlaylistDetailView(
                playlist = selectedPlaylist,
                tracks = selectedPlaylistTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                downloadStatuses = downloadStatuses,
                downloadProgresses = downloadProgresses,
                favoriteIds = favoriteIds,
                appLanguage = appLanguage,
                onBack = { onSelectPlaylist(null) },
                onDelete = { onDeletePlaylist(targetPlaylistId) },
                onTrackClick = { track -> onTrackClick(track, selectedPlaylistTracks) },
                onPlayAll = { onPlayAll(selectedPlaylistTracks) },
                onShuffleAll = { onShuffleAll(selectedPlaylistTracks) },
                onDownloadPlaylist = { onDownloadPlaylist(selectedPlaylistTracks) },
                onToggleFavorite = onToggleFavorite,
                onDownloadTrack = onDownloadTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("playlists_screen")
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${str("playlists_title")} (${playlists.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("create_playlist_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(str("btn_create_playlist"))
                    }
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(
                                imageVector = Icons.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = str("empty_playlists"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = str("empty_playlists_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(onClick = { showCreateDialog = true }) {
                                Text(str("btn_create_playlist"))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onSelectPlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(str("dialog_create_playlist")) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(str("dialog_playlist_name")) },
                    placeholder = { Text("e.g. Favorite Beats") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_new_playlist_btn")
                ) {
                    Text(str("btn_create"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }
}

@Composable
fun PlaylistDetailView(
    playlist: NaviromPlaylist?,
    tracks: List<NaviromTrack>,
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    favoriteIds: List<String>,
    appLanguage: AppLanguage,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTrackClick: (NaviromTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (NaviromTrack) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val haptics = rememberNaviromHaptics()
    BackHandler(enabled = true) { onBack() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = { dragX = 0f; dragY = 0f },
                    onDrag = { change, dragAmount ->
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragX > 75f && kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.2f) {
                            haptics.toggle()
                            onBack()
                        }
                    }
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("playlist_detail_view")
        ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("playlist_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = playlist?.name ?: str("playlists_title"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (playlist?.id != "favorites_dynamic_playlist_id") {
                    IconButton(
                        onClick = { showDeleteConfirm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = str("delete_playlist"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (playlist?.id == "favorites_dynamic_playlist_id") {
                                AccentRose.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist?.id == "favorites_dynamic_playlist_id") {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = AccentRose,
                            modifier = Modifier.size(48.dp)
                        )
                    } else if (playlist?.coverArt?.isNotBlank() == true) {
                        AsyncImage(
                            model = playlist.coverArt,
                            contentDescription = playlist.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (playlist?.id == "favorites_dynamic_playlist_id") {
                        Text(
                            text = "Songs favorited via the heart icon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    } else if (playlist?.comment?.isNotBlank() == true) {
                        Text(
                            text = playlist.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f).testTag("playlist_play_all"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(str("btn_play_all"))
                }

                FilledTonalButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f).testTag("playlist_shuffle_all"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(str("btn_shuffle"))
                }

                OutlinedButton(
                    onClick = onDownloadPlaylist,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("playlist_download_btn")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                }
            }
        }

        items(tracks, key = { it.id }) { track ->
            TrackListItem(
                track = track,
                isPlaying = isPlaying,
                isCurrentTrack = currentTrack?.id == track.id,
                downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = downloadProgresses[track.id],
                isFavorite = favoriteIds.contains(track.id),
                showCoverArt = true,
                onTrackClick = { onTrackClick(track) },
                onToggleFavorite = { onToggleFavorite(track.id) },
                onDownloadClick = { onDownloadTrack(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(str("delete_playlist")) },
            text = { Text(if (appLanguage == AppLanguage.ALBANIAN) "A je i sigurt që dëshiron të fshish '${playlist?.name}'?" else "Are you sure you want to delete '${playlist?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(if (appLanguage == AppLanguage.ALBANIAN) "Fshi" else "Delete", color = AccentRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }
}
