package com.labix.navirom.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labix.navirom.data.model.*
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.components.AlbumCard
import com.labix.navirom.ui.components.ArtistCard
import com.labix.navirom.ui.components.TrackListItem

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: Triple<List<NaviromArtist>, List<NaviromAlbum>, List<NaviromTrack>>,
    searchHistory: List<String> = emptyList(),
    onRemoveSearchHistory: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    favoriteIds: List<String>,
    appLanguage: AppLanguage,
    onSelectAlbum: (String) -> Unit,
    onTrackClick: (NaviromTrack, List<NaviromTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (NaviromTrack) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    searchFocusTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val (artists, albums, tracks) = searchResults
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger > 0) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(str("search_hint")) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusRequester(focusRequester)
                .testTag("search_input_field")
        )

        // Search History Section below Search Bar
        if (searchHistory.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("search_history_container")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = str("search_history_title"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onClearSearchHistory,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("clear_search_history_button")
                    ) {
                        Text(
                            text = str("search_history_clear"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(searchHistory, key = { it }) { word ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp,
                            modifier = Modifier.testTag("search_history_chip_$word")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = word,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { onQueryChange(word) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = { onRemoveSearchHistory(word) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("remove_search_history_$word")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove $word from history",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val searchState = when {
            isSearching -> 0
            query.isBlank() -> 1
            artists.isEmpty() && albums.isEmpty() && tracks.isEmpty() -> 2
            else -> 3
        }

        AnimatedContent(
            targetState = searchState,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "SearchStateTransition",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                0 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = str("search_recent"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                2 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(str("search_no_results"), query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Artists Section
                        if (artists.isNotEmpty()) {
                            item {
                                Text(
                                    text = str("search_artists"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(artists, key = { it.id }) { artist ->
                                        ArtistCard(
                                            artist = artist,
                                            onClick = { onQueryChange(artist.name) },
                                            modifier = Modifier.width(130.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Albums Section
                        if (albums.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = str("search_albums"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { onSelectAlbum(album.id) },
                                            modifier = Modifier.width(150.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Tracks Section
                        if (tracks.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "${str("search_tracks")} (${tracks.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            items(tracks, key = { it.id }) { track ->
                                TrackListItem(
                                    track = track,
                                    isPlaying = isPlaying,
                                    isCurrentTrack = currentTrack?.id == track.id,
                                    downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                                    downloadProgress = downloadProgresses[track.id],
                                    isFavorite = favoriteIds.contains(track.id),
                                    onTrackClick = { onTrackClick(track, tracks) },
                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                    onDownloadClick = { onDownloadTrack(track) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
