package com.example.navirom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.navirom.data.local.CachedTrackEntity
import com.example.navirom.data.model.DownloadStatus
import com.example.navirom.data.model.NaviromTrack
import com.example.navirom.ui.AppLanguage
import com.example.navirom.ui.NaviromStrings
import com.example.navirom.ui.components.TrackListItem
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose

@Composable
fun OfflineCacheScreen(
    cachedTracks: List<CachedTrackEntity>,
    totalCacheSizeBytes: Long,
    isOfflineOnlyMode: Boolean,
    onToggleOfflineOnly: (Boolean) -> Unit,
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    favoriteIds: List<String>,
    appLanguage: AppLanguage,
    onPlayAll: (List<NaviromTrack>) -> Unit,
    onTrackClick: (NaviromTrack, List<NaviromTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteCachedTrack: (String) -> Unit,
    onClearAllCache: () -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    var showClearDialog by remember { mutableStateOf(false) }

    val trackList = remember(cachedTracks) {
        cachedTracks.map { entity ->
            NaviromTrack(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                artistId = entity.artistId,
                album = entity.album,
                albumId = entity.albumId,
                durationSeconds = entity.durationSeconds,
                coverArtUrl = entity.coverArtUrl,
                streamUrl = "",
                localFilePath = entity.localFilePath,
                year = entity.year,
                genre = entity.genre,
                bitRate = entity.bitRate,
                suffix = entity.format,
                isCached = true,
                sizeBytes = entity.fileSizeBytes
            )
        }
    }

    val totalSizeMb = "%.1f".format(totalCacheSizeBytes / (1024f * 1024f))

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("offline_cache_screen")
    ) {
        // Storage Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = str("downloads_title"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalSizeMb MB • ${cachedTracks.size} tracks cached",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (cachedTracks.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_all_cache_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = str("clear_all_cache"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Play All Offline & Offline Mode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlayAll(trackList) },
                        enabled = trackList.isNotEmpty(),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("play_all_offline_btn")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(str("play_cached"))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = str("offline_only"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isOfflineOnlyMode,
                            onCheckedChange = onToggleOfflineOnly,
                            modifier = Modifier.testTag("offline_only_switch")
                        )
                    }
                }
            }
        }

        // Cached Track List
        if (cachedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = str("empty_downloads"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = str("empty_downloads_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(trackList, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrentTrack = currentTrack?.id == track.id,
                        downloadStatus = DownloadStatus.DOWNLOADED,
                        downloadProgress = 1.0f,
                        isFavorite = favoriteIds.contains(track.id),
                        onTrackClick = { onTrackClick(track, trackList) },
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onDownloadClick = { onDeleteCachedTrack(track.id) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(str("clear_all_cache")) },
            text = { Text(if (appLanguage == AppLanguage.ALBANIAN) "A je i sigurt që dëshiron të fshish të gjitha $totalSizeMb MB të këngëve të shkarkuara nga pajisja?" else "Are you sure you want to delete all $totalSizeMb MB of downloaded tracks from device storage?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllCache()
                        showClearDialog = false
                    }
                ) {
                    Text(if (appLanguage == AppLanguage.ALBANIAN) "Fshi të gjitha" else "Clear All", color = AccentRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }
}
