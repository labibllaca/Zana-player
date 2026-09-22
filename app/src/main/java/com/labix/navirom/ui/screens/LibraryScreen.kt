package com.labix.navirom.ui.screens

import androidx.activity.compose.BackHandler
import java.util.Calendar
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.labix.navirom.data.model.*
import com.labix.navirom.data.local.LocalMusicFolder
import com.labix.navirom.ui.AppLanguage
import com.labix.navirom.ui.LibrarySubTab
import com.labix.navirom.ui.NaviromStrings
import com.labix.navirom.ui.SongSortOrder
import com.labix.navirom.ui.components.ActionButtonGroup
import com.labix.navirom.ui.components.AlbumCard
import com.labix.navirom.ui.components.DualAudioPlayButton
import com.labix.navirom.ui.components.ArtistCard
import com.labix.navirom.ui.components.SleekFeatureCard
import com.labix.navirom.ui.components.SongAlbumCover
import com.labix.navirom.ui.components.TrackListItem
import com.labix.navirom.ui.util.rememberNaviromHaptics
import com.labix.ui.theme.*

import com.labix.navirom.ui.components.ArtistDetailView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    subTab: LibrarySubTab,
    onSubTabSelected: (LibrarySubTab) -> Unit,
    albums: List<NaviromAlbum>,
    artists: List<NaviromArtist>,
    quickMixTracks: List<NaviromTrack>,
    recentlyPlayedTracks: List<NaviromTrack> = emptyList(),
    librarySongs: List<NaviromTrack> = emptyList(),
    songSortOrder: SongSortOrder = SongSortOrder.NAME,
    onSetSongSortOrder: (SongSortOrder) -> Unit = {},
    newestAlbums: List<NaviromAlbum> = emptyList(),
    mostPlayedAlbums: List<NaviromAlbum> = emptyList(),
    randomAlbums: List<NaviromAlbum> = emptyList(),
    onRefreshRandomAlbums: () -> Unit = {},
    profileName: String = "",
    newestTracks: List<NaviromTrack> = emptyList(),
    selectedAlbumId: String?,
    selectedAlbumTracks: List<NaviromTrack>,
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    favoriteIds: List<String>,
    favoriteTracks: List<NaviromTrack> = emptyList(),
    appLanguage: AppLanguage,
    musicFolders: List<com.labix.navirom.data.api.dto.MusicFolderDto> = emptyList(),
    selectedMusicFolderId: String? = null,
    selectedMusicFolderIds: Set<String> = emptySet(),
    onSelectMusicFolder: (String?) -> Unit = {},
    onToggleMusicFolder: (String) -> Unit = {},
    onSelectAllMusicFolders: () -> Unit = {},
    onSyncLibrary: () -> Unit = {},
    onSelectAlbum: (String?) -> Unit,
    onTrackClick: (NaviromTrack, List<NaviromTrack>) -> Unit,
    onPlayAll: (List<NaviromTrack>) -> Unit,
    onShuffleAll: (List<NaviromTrack>) -> Unit,
    onDownloadTracks: (List<NaviromTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (NaviromTrack) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    onGoToSettings: () -> Unit = {},
    onScanNetwork: () -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    selectedArtistId: String? = null,
    currentArtist: NaviromArtist? = null,
    currentArtistAlbums: List<NaviromAlbum> = emptyList(),
    currentArtistSongs: List<NaviromTrack> = emptyList(),
    isLoadingArtistDetails: Boolean = false,
    onSelectArtist: (String?) -> Unit = {},
    localTracks: List<NaviromTrack> = emptyList(),
    localFolders: List<LocalMusicFolder> = emptyList(),
    disabledLocalFolderIds: Set<String> = emptySet(),
    onToggleLocalFolder: (String) -> Unit = {},
    onSetLocalFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onSelectAllLocalFolders: () -> Unit = {},
    onDeselectAllLocalFolders: () -> Unit = {},
    isScanningLocalAudio: Boolean = false,
    onScanLocalAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val haptics = rememberNaviromHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onScanLocalAudio()
        }
    }

    val requestLocalPermission = {
        val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            perm
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            onScanLocalAudio()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    var showFolderSwitcherDialog by remember { mutableStateOf(false) }

    // If an album is selected, show the Album Detail view (placed before artist detail view to allow navigating from artist -> album)
    if (selectedAlbumId != null) {
        val selectedAlbum = currentArtistAlbums.find { it.id == selectedAlbumId }
            ?: albums.find { it.id == selectedAlbumId }
            ?: newestAlbums.find { it.id == selectedAlbumId }
            ?: if (selectedAlbumTracks.isNotEmpty()) {
                NaviromAlbum(
                    id = selectedAlbumId,
                    name = selectedAlbumTracks.first().album,
                    artist = selectedAlbumTracks.first().artist,
                    artistId = selectedAlbumTracks.first().artistId,
                    coverArt = selectedAlbumTracks.first().coverArtId,
                    coverArtUrl = selectedAlbumTracks.first().coverArtUrl,
                    songCount = selectedAlbumTracks.size,
                    durationSeconds = selectedAlbumTracks.sumOf { it.durationSeconds }
                )
            } else null

        AlbumDetailView(
            album = selectedAlbum,
            tracks = selectedAlbumTracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            downloadStatuses = downloadStatuses,
            downloadProgresses = downloadProgresses,
            favoriteIds = favoriteIds,
            appLanguage = appLanguage,
            onBack = { onSelectAlbum(null) },
            onTrackClick = { track -> onTrackClick(track, selectedAlbumTracks) },
            onPlayAll = { onPlayAll(selectedAlbumTracks) },
            onShuffleAll = { onShuffleAll(selectedAlbumTracks) },
            onDownloadAlbum = { onDownloadTracks(selectedAlbumTracks) },
            onToggleFavorite = onToggleFavorite,
            onDownloadTrack = onDownloadTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            modifier = modifier
        )
        return
    }

    // If an artist is selected, show the Artist Detail view
    if (selectedArtistId != null) {
        ArtistDetailView(
            artist = currentArtist,
            albums = currentArtistAlbums,
            songs = currentArtistSongs,
            isLoading = isLoadingArtistDetails,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            downloadStatuses = downloadStatuses,
            downloadProgresses = downloadProgresses,
            favoriteIds = favoriteIds,
            appLanguage = appLanguage,
            onBack = { onSelectArtist(null) },
            onSelectAlbum = onSelectAlbum,
            onTrackClick = { track -> onTrackClick(track, currentArtistSongs) },
            onPlayAllSongs = { onPlayAll(currentArtistSongs) },
            onShuffleAllSongs = { onShuffleAll(currentArtistSongs) },
            onToggleFavorite = onToggleFavorite,
            onDownloadTrack = onDownloadTrack,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            modifier = modifier
        )
        return
    }

    val hasLocalMusic = localTracks.isNotEmpty() || librarySongs.isNotEmpty() || localFolders.isNotEmpty()
    val hasServerMusic = albums.isNotEmpty() || artists.isNotEmpty() || quickMixTracks.isNotEmpty() || musicFolders.isNotEmpty()

    if (!hasLocalMusic && !hasServerMusic) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = str("empty_library_title"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = str("empty_library_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onScanLocalAudio,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(str("scan_local_audio"))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGoToSettings,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(str("btn_connect_server"))
                    }
                }
            }
        }
        return
    }

    val isAllLibrariesSelected = selectedMusicFolderIds.isEmpty() || (musicFolders.isNotEmpty() && selectedMusicFolderIds.size >= musicFolders.size)
    val activeFolderName = when {
        isAllLibrariesSelected -> str("all_libraries_title")
        selectedMusicFolderIds.size == 1 -> musicFolders.find { it.id == selectedMusicFolderIds.first() }?.name ?: str("all_libraries_title")
        else -> {
            val matching = musicFolders.filter { it.id in selectedMusicFolderIds }.map { it.name }
            if (matching.isNotEmpty()) matching.joinToString(", ") else String.format(str("multi_libraries_selected"), selectedMusicFolderIds.size)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_screen")
        ) {
            // 1. Time-of-day greeting header with profile name
            GreetingHeader(
                profileName = profileName,
                appLanguage = appLanguage,
                onOpenSidebar = onOpenSidebar
            )

            // 2. Editorial Library Active Folder Bar (Clean, serene layout)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { onOpenSidebar() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("library_folder_switcher")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("switch_library_prompt").uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeFolderName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { onOpenSidebar() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (musicFolders.isNotEmpty()) "${musicFolders.size} ${str("subtab_libraries").lowercase()} +" else "${str("subtab_libraries")} +",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Editorial Minimalist SubTabs (Inspired by ELOME navigation)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialSubTabItem(
                    title = str("subtab_overview"),
                    isSelected = subTab == LibrarySubTab.OVERVIEW,
                    onClick = { onSubTabSelected(LibrarySubTab.OVERVIEW) },
                    testTag = "tab_overview"
                )
                EditorialSubTabItem(
                    title = if (librarySongs.isNotEmpty()) "${str("subtab_songs")} (${librarySongs.size})" else str("subtab_songs"),
                    isSelected = subTab == LibrarySubTab.SONGS,
                    onClick = { onSubTabSelected(LibrarySubTab.SONGS) },
                    testTag = "tab_songs"
                )
                EditorialSubTabItem(
                    title = "${str("subtab_albums")} (${albums.size})",
                    isSelected = subTab == LibrarySubTab.ALBUMS,
                    onClick = { onSubTabSelected(LibrarySubTab.ALBUMS) },
                    testTag = "tab_albums"
                )
                EditorialSubTabItem(
                    title = "${str("subtab_artists")} (${artists.size})",
                    isSelected = subTab == LibrarySubTab.ARTISTS,
                    onClick = { onSubTabSelected(LibrarySubTab.ARTISTS) },
                    testTag = "tab_artists"
                )
                val totalFolderCount = musicFolders.size + localFolders.size
                val countSuffix = if (totalFolderCount > 0) " ($totalFolderCount)" else ""
                EditorialSubTabItem(
                    title = "${str("subtab_local_folders")}$countSuffix",
                    isSelected = subTab == LibrarySubTab.LIBRARIES,
                    onClick = { onSubTabSelected(LibrarySubTab.LIBRARIES) },
                    testTag = "tab_local_files"
                )
                EditorialSubTabItem(
                    title = str("newly_added_title"),
                    isSelected = subTab == LibrarySubTab.NEWEST,
                    onClick = { onSubTabSelected(LibrarySubTab.NEWEST) },
                    testTag = "tab_newest"
                )
                EditorialSubTabItem(
                    title = str("subtab_quick_mix"),
                    isSelected = subTab == LibrarySubTab.QUICK_MIX,
                    onClick = { onSubTabSelected(LibrarySubTab.QUICK_MIX) },
                    testTag = "tab_quick_mix"
                )
                EditorialSubTabItem(
                    title = if (recentlyPlayedTracks.isNotEmpty()) "${str("subtab_recent")} (${recentlyPlayedTracks.size})" else str("subtab_recent"),
                    isSelected = subTab == LibrarySubTab.RECENT,
                    onClick = { onSubTabSelected(LibrarySubTab.RECENT) },
                    testTag = "tab_recent"
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            AnimatedContent(
                targetState = subTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(animationSpec = tween(200)) +
                        slideInHorizontally(animationSpec = tween(200)) { width -> if (forward) width / 6 else -width / 6 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                            slideOutHorizontally(animationSpec = tween(150)) { width -> if (forward) -width / 6 else width / 6 }
                        )
                },
                label = "LibrarySubTabAnimatedTransition",
                modifier = Modifier.fillMaxSize()
            ) { activeSubTab ->
                when (activeSubTab) {
                    LibrarySubTab.OVERVIEW -> {
                    // Overview Dashboard with the 4 requested groups:
                    // 1. Recent Played Songs
                    // 2. Recent Added (Albums & Tracks)
                    // 3. Most Played (Albums)
                    // 4. Random from Library (Albums)
                    val effectiveNewAlbums = if (newestAlbums.isNotEmpty()) newestAlbums else albums.take(12)
                    val effectiveMostPlayed = if (mostPlayedAlbums.isNotEmpty()) mostPlayedAlbums else albums.take(12)
                    val effectiveRandom = if (randomAlbums.isNotEmpty()) randomAlbums else albums.shuffled().take(12)

                    LazyColumn(
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_overview_list")
                    ) {
                        // Group 1: 'Recent Played' Songs
                        item {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = str("recently_played_title"),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = str("recently_played_subtitle"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (recentlyPlayedTracks.isNotEmpty()) {
                                        TextButton(
                                            onClick = { onSubTabSelected(LibrarySubTab.RECENT) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(str("see_all"), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (recentlyPlayedTracks.isNotEmpty()) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(recentlyPlayedTracks.take(15), key = { "overview_recent_${it.id}" }) { track ->
                                            val isThisPlaying = currentTrack?.id == track.id
                                            Surface(
                                                onClick = { onTrackClick(track, recentlyPlayedTracks) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isThisPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.width(135.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Box {
                                                        SongAlbumCover(
                                                            coverArtUrl = track.coverArtUrl,
                                                            contentDescription = track.title,
                                                            isAlbum = false,
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(115.dp)
                                                        )
                                                        if (isThisPlaying && isPlaying) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .matchParentSize()
                                                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(28.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = track.title,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.artist,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Clean empty state card for Recent
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = str("empty_recent_title"),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = str("empty_recent_desc"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Group 2: 'Recent Added' (Albums & Music)
                        if (effectiveNewAlbums.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.FiberNew,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = str("recently_added_title"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = str("recently_added_subtitle"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        TextButton(
                                            onClick = { onSubTabSelected(LibrarySubTab.NEWEST) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(str("see_all"), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(effectiveNewAlbums.take(12), key = { "overview_new_${it.id}" }) { album ->
                                            Box(modifier = Modifier.width(145.dp)) {
                                                AlbumCard(
                                                    album = album,
                                                    onClick = { onSelectAlbum(album.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Group 3: 'Most Played' (Top Albums)
                        if (effectiveMostPlayed.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.TrendingUp,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = str("most_played_title"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = str("most_played_subtitle"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(effectiveMostPlayed.take(12), key = { "overview_most_played_${it.id}" }) { album ->
                                            Box(modifier = Modifier.width(145.dp)) {
                                                AlbumCard(
                                                    album = album,
                                                    onClick = { onSelectAlbum(album.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Group 4: 'Random from Library' (Alben)
                        if (effectiveRandom.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Casino,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = str("random_albums_title"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = str("random_albums_subtitle"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = onRefreshRandomAlbums,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = str("btn_refresh_random"),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(effectiveRandom.take(12), key = { "overview_random_${it.id}" }) { album ->
                                            Box(modifier = Modifier.width(145.dp)) {
                                                AlbumCard(
                                                    album = album,
                                                    onClick = { onSelectAlbum(album.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Local Songs in Overview
                        if (localTracks.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.FolderOpen,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = str("local_music_tab"),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${localTracks.size} ${str("songs_count_suffix")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalButton(
                                                onClick = { onPlayAll(localTracks) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(str("btn_play_all"), style = MaterialTheme.typography.labelSmall)
                                            }
                                            IconButton(
                                                onClick = { onShuffleAll(localTracks) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Filled.Shuffle, contentDescription = str("btn_shuffle_all"), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(localTracks.take(15), key = { "overview_local_${it.id}" }) { track ->
                                            val isThisPlaying = currentTrack?.id == track.id
                                            Surface(
                                                onClick = { onTrackClick(track, localTracks) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isThisPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.width(135.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Box {
                                                        SongAlbumCover(
                                                            coverArtUrl = track.coverArtUrl,
                                                            contentDescription = track.title,
                                                            isAlbum = false,
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(115.dp)
                                                        )
                                                        if (isThisPlaying && isPlaying) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .matchParentSize()
                                                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.PlayArrow,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(28.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = track.title,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.artist,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isThisPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
                LibrarySubTab.SONGS -> {
                    // List view for songs in library with sorting controls
                    val effectiveSongs = if (librarySongs.isNotEmpty()) librarySongs else quickMixTracks
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_songs_list_view")
                    ) {
                        // Song Sorting Controls Header
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${effectiveSongs.size} ${str("subtab_songs").lowercase()}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onPlayAll(effectiveSongs) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("songs_play_all_btn")
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(str("btn_play_all"), style = MaterialTheme.typography.labelMedium)
                                        }

                                        FilledTonalButton(
                                            onClick = { onShuffleAll(effectiveSongs) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("songs_shuffle_all_btn")
                                        ) {
                                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(str("btn_shuffle"), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Sort selection chips row: Name, Time/Duration, Last input / Recently Added, Artist
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${str("sort_by")}:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )

                                    FilterChip(
                                        selected = songSortOrder == SongSortOrder.NAME,
                                        onClick = { onSetSongSortOrder(SongSortOrder.NAME) },
                                        label = { Text(str("sort_name"), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("sort_name_chip")
                                    )

                                    FilterChip(
                                        selected = songSortOrder == SongSortOrder.DURATION,
                                        onClick = { onSetSongSortOrder(SongSortOrder.DURATION) },
                                        label = { Text(str("sort_duration"), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("sort_duration_chip")
                                    )

                                    FilterChip(
                                        selected = songSortOrder == SongSortOrder.RECENTLY_ADDED,
                                        onClick = { onSetSongSortOrder(SongSortOrder.RECENTLY_ADDED) },
                                        label = { Text(str("sort_recent"), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("sort_recent_chip")
                                    )

                                    FilterChip(
                                        selected = songSortOrder == SongSortOrder.ARTIST,
                                        onClick = { onSetSongSortOrder(SongSortOrder.ARTIST) },
                                        label = { Text(str("sort_artist"), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("sort_artist_chip")
                                    )
                                }
                            }
                        }

                        // Songs list items
                        items(effectiveSongs, key = { it.id }) { track ->
                            TrackListItem(
                                track = track,
                                isPlaying = isPlaying,
                                isCurrentTrack = currentTrack?.id == track.id,
                                downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                                downloadProgress = downloadProgresses[track.id],
                                isFavorite = favoriteIds.contains(track.id),
                                showCoverArt = true,
                                trackList = effectiveSongs,
                                onTrackClick = { onTrackClick(track, effectiveSongs) },
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onDownloadClick = { onDownloadTrack(track) },
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                LibrarySubTab.LIBRARIES -> {
                    // Local Music Folders & Server Libraries tab
                    val isAllServerSelected = selectedMusicFolderIds.isEmpty() || (selectedMusicFolderIds.size >= musicFolders.size)
                    val activeLocalFolderCount = localFolders.count { !disabledLocalFolderIds.contains(it.id) }
                    val allLocalSelected = disabledLocalFolderIds.isEmpty() && localFolders.isNotEmpty()

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().testTag("local_files_tab_list")
                    ) {
                        // Section 1: Local On-Device Audio Folders Banner
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                modifier = Modifier.fillMaxWidth().testTag("local_device_music_card")
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.SdStorage,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = str("local_music_folders_title"),
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                text = if (localFolders.isNotEmpty()) {
                                                    String.format(str("local_folders_active"), activeLocalFolderCount, localFolders.size) + " • ${localFolders.sumOf { it.trackCount }} ${str("tracks_count")}"
                                                } else {
                                                    str("local_music_folders_subtitle")
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (localFolders.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    haptics.click()
                                                    if (allLocalSelected) {
                                                        onDeselectAllLocalFolders()
                                                    } else {
                                                        onSelectAllLocalFolders()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (allLocalSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (allLocalSelected) str("deselect_all_local_folders") else str("select_all_local_folders"),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                haptics.click()
                                                requestLocalPermission()
                                            },
                                            modifier = if (localFolders.isNotEmpty()) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !isScanningLocalAudio,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.35f))
                                        ) {
                                            if (isScanningLocalAudio) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Scanning...", style = MaterialTheme.typography.labelSmall)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (localFolders.isEmpty()) str("scan_device_folders") else str("rescan_device_folders"),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Local Folders List
                        if (localFolders.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${str("local_folders_header")} (${localFolders.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${activeLocalFolderCount} ${str("deck_discover").lowercase()} / ${localFolders.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            items(localFolders, key = { "local_folder_${it.id}" }) { folder ->
                                val isFolderEnabled = !disabledLocalFolderIds.contains(folder.id)
                                var isExpanded by remember(folder.id) { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("local_folder_card_${folder.id.hashCode()}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFolderEnabled) {
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    border = if (isFolderEnabled) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        // Top Row: Folder Icon, Names, Status Badge & Checkbox
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isFolderEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.size(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isFolderEnabled) Icons.Filled.Folder else Icons.Outlined.FolderOff,
                                                            contentDescription = null,
                                                            tint = if (isFolderEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = folder.name,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = if (isFolderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )

                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isFolderEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                        ) {
                                                            Text(
                                                                text = if (isFolderEnabled) "Aktiv" else "Deaktiviert",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = if (isFolderEnabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))

                                                    Text(
                                                        text = "${folder.trackCount} ${str("tracks_count")} • " +
                                                                "${if (folder.totalDurationSeconds >= 3600) "${folder.totalDurationSeconds / 3600}h ${(folder.totalDurationSeconds % 3600) / 60}m" else "${folder.totalDurationSeconds / 60} min"}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Text(
                                                        text = folder.displayPath,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Checkbox toggle for activating/deactivating this folder
                                            Checkbox(
                                                checked = isFolderEnabled,
                                                onCheckedChange = {
                                                    haptics.toggle()
                                                    onToggleLocalFolder(folder.id)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.tertiary
                                                ),
                                                modifier = Modifier.testTag("toggle_local_folder_${folder.id.hashCode()}")
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Actions Row: Play All Folder, Shuffle Folder, Expand/Collapse
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    haptics.click()
                                                    if (!isFolderEnabled) {
                                                        onToggleLocalFolder(folder.id)
                                                    }
                                                    onPlayAll(folder.tracks)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(str("local_folder_play_all"), style = MaterialTheme.typography.labelMedium)
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    haptics.click()
                                                    if (!isFolderEnabled) {
                                                        onToggleLocalFolder(folder.id)
                                                    }
                                                    onShuffleAll(folder.tracks)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(str("local_folder_shuffle"), style = MaterialTheme.typography.labelMedium)
                                            }

                                            IconButton(
                                                onClick = {
                                                    haptics.click()
                                                    isExpanded = !isExpanded
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Expandable Track List inside Folder
                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                Spacer(modifier = Modifier.height(4.dp))

                                                folder.tracks.forEachIndexed { idx, track ->
                                                    val isTrackPlaying = currentTrack?.id == track.id && isPlaying
                                                    TrackListItem(
                                                        track = track,
                                                        isPlaying = isTrackPlaying,
                                                        isCurrentTrack = currentTrack?.id == track.id,
                                                        downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                                                        downloadProgress = downloadProgresses[track.id] ?: 0f,
                                                        isFavorite = favoriteIds.contains(track.id),
                                                        trackList = folder.tracks,
                                                        onTrackClick = { onTrackClick(track, folder.tracks) },
                                                        onToggleFavorite = { onToggleFavorite(track.id) },
                                                        onDownloadClick = { onDownloadTrack(track) },
                                                        onPlayNext = { onPlayNext(track) },
                                                        onAddToQueue = { onAddToQueue(track) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = str("no_local_folders_found"),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2: Navidrome Server Libraries Header
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.FolderSpecial,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = str("all_libraries_title"),
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = if (isAllServerSelected) {
                                                    "${str("all_libraries_combined")} (${musicFolders.size} folders)"
                                                } else {
                                                    "${selectedMusicFolderIds.size} / ${musicFolders.size} folders active"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                haptics.click()
                                                onSelectAllMusicFolders()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isAllServerSelected) Icons.Filled.DoneAll else Icons.Filled.SelectAll,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isAllServerSelected) "Reset Filter" else "Select All",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                haptics.click()
                                                onGoToSettings()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Settings",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Navidrome Server Music Folders List
                        if (musicFolders.isNotEmpty()) {
                            item {
                                Text(
                                    text = "${str("server_libraries_title")} (${musicFolders.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }

                            items(musicFolders, key = { it.id }) { folder ->
                                val isChecked = !isAllServerSelected && selectedMusicFolderIds.contains(folder.id)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptics.toggle()
                                            onSelectMusicFolder(folder.id)
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isChecked) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = if (isChecked) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
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
                                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Folder,
                                                        contentDescription = null,
                                                        tint = if (isChecked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = folder.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Folder ID: ${folder.id}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                haptics.toggle()
                                                onToggleMusicFolder(folder.id)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "No Music Folders Detected",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Configure server music folders in settings or rescan your server.",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                haptics.click()
                                                onGoToSettings()
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Configure in Settings")
                                        }
                                    }
                                }
                            }
                        }

                        // Collection Header and Quick Actions for Active Selection
                        if (librarySongs.isNotEmpty() || albums.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Folder Tracks & Albums",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${librarySongs.size} tracks • ${albums.size} albums",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { onPlayAll(librarySongs) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(str("play_all"), fontSize = 12.sp)
                                        }

                                        FilledTonalButton(
                                            onClick = { onShuffleAll(librarySongs) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(str("shuffle_all"), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            items(librarySongs.take(20), key = { it.id }) { track ->
                                TrackListItem(
                                    track = track,
                                    isPlaying = isPlaying,
                                    isCurrentTrack = currentTrack?.id == track.id,
                                    downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                                    downloadProgress = downloadProgresses[track.id],
                                    isFavorite = favoriteIds.contains(track.id),
                                    showCoverArt = true,
                                    trackList = librarySongs,
                                    onTrackClick = { onTrackClick(track, librarySongs) },
                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                    onDownloadClick = { onDownloadTrack(track) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                LibrarySubTab.RECENT -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().testTag("recent_tracks_list")
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = str("recently_played_title"),
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = str("recently_played_subtitle"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    if (recentlyPlayedTracks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { onPlayAll(recentlyPlayedTracks) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(str("play_all"))
                                            }
                                            FilledTonalButton(
                                                onClick = { onShuffleAll(recentlyPlayedTracks) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(str("shuffle_all"))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (recentlyPlayedTracks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = str("empty_recent_title"),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = str("empty_recent_desc"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(recentlyPlayedTracks, key = { it.id }) { track ->
                                TrackListItem(
                                    track = track,
                                    isPlaying = isPlaying,
                                    isCurrentTrack = currentTrack?.id == track.id,
                                    downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                                    downloadProgress = downloadProgresses[track.id],
                                    isFavorite = favoriteIds.contains(track.id),
                                    trackList = recentlyPlayedTracks,
                                    onTrackClick = { onTrackClick(track, recentlyPlayedTracks) },
                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                    onDownloadClick = { onDownloadTrack(track) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) }
                                )
                            }
                        }
                    }
                }
            LibrarySubTab.NEWEST -> {
                val effectiveNewAlbums = if (newestAlbums.isNotEmpty()) newestAlbums else albums.take(12)
                val effectiveNewTracks = if (newestTracks.isNotEmpty()) newestTracks else quickMixTracks.take(20)

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().testTag("newly_added_list")
                ) {
                    // Hero Banner for Newly Added
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.FiberNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = str("newly_added_title"),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = str("newly_added_subtitle"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (effectiveNewTracks.isNotEmpty()) onPlayAll(effectiveNewTracks)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(str("play_all"))
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            if (effectiveNewTracks.isNotEmpty()) onShuffleAll(effectiveNewTracks)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(str("shuffle_all"))
                                    }
                                }
                            }
                        }
                    }

                    // Newly Added Albums Section
                    if (effectiveNewAlbums.isNotEmpty()) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = str("newly_added_albums"),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${effectiveNewAlbums.size} ${str("subtab_albums").lowercase()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    effectiveNewAlbums.forEach { album ->
                                        Box(modifier = Modifier.width(150.dp)) {
                                            AlbumCard(
                                                album = album,
                                                onClick = { onSelectAlbum(album.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Removed Recently Played block from here since it's above the tabs now

                    // Newly Added Tracks Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = str("newly_added_tracks"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${effectiveNewTracks.size} ${str("tracks_count")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(effectiveNewTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            isPlaying = isPlaying,
                            isCurrentTrack = currentTrack?.id == track.id,
                            downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                            downloadProgress = downloadProgresses[track.id],
                            isFavorite = favoriteIds.contains(track.id),
                            trackList = effectiveNewTracks,
                            onTrackClick = { onTrackClick(track, effectiveNewTracks) },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onDownloadClick = { onDownloadTrack(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) }
                        )
                    }
                }
            }

            LibrarySubTab.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { onSelectAlbum(album.id) }
                        )
                    }
                }
            }

            LibrarySubTab.ARTISTS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(artists, key = { it.id }) { artist ->
                        ArtistCard(
                            artist = artist,
                            onClick = {
                                onSelectArtist(artist.id)
                            }
                        )
                    }
                }
            }

            LibrarySubTab.GENRES,
            LibrarySubTab.QUICK_MIX -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Sleek 2x2 Quick Actions / Discover Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SleekFeatureCard(
                                        title = str("deck_liked"),
                                        subtitle = "${favoriteIds.size} ${str("deck_liked_sub")}",
                                        icon = Icons.Filled.Favorite,
                                        iconBgColor = SleekPillLikedBg,
                                        iconColor = SleekPillLikedIcon,
                                        onClick = {
                                            if (favoriteTracks.isNotEmpty()) {
                                                onPlayAll(favoriteTracks)
                                            } else {
                                                val favTracks = quickMixTracks.filter { favoriteIds.contains(it.id) }
                                                if (favTracks.isNotEmpty()) onPlayAll(favTracks) else onPlayAll(quickMixTracks)
                                            }
                                        }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    SleekFeatureCard(
                                        title = str("deck_mix"),
                                        subtitle = "${quickMixTracks.size} ${str("deck_mix_sub")}",
                                        icon = Icons.Filled.AutoAwesome,
                                        iconBgColor = SleekPillMixBg,
                                        iconColor = SleekPillMixIcon,
                                        onClick = { onPlayAll(quickMixTracks) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SleekFeatureCard(
                                        title = str("deck_shuffle"),
                                        subtitle = str("deck_shuffle_sub"),
                                        icon = Icons.Filled.Shuffle,
                                        iconBgColor = SleekPillFlashbackBg,
                                        iconColor = SleekPillFlashbackIcon,
                                        onClick = { onShuffleAll(quickMixTracks) }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    SleekFeatureCard(
                                        title = str("subtab_albums"),
                                        subtitle = "${albums.size} collections",
                                        icon = Icons.Filled.Album,
                                        iconBgColor = SleekPillDiscoverBg,
                                        iconColor = SleekPillDiscoverIcon,
                                        onClick = { onSubTabSelected(LibrarySubTab.ALBUMS) }
                                    )
                                }
                            }
                        }
                    }

                    // Quick Mix Track List Section Header
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = str("subtab_quick_mix"),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FilledTonalButton(
                                onClick = { onPlayAll(quickMixTracks) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(str("btn_play_all"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Track items
                    items(quickMixTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            isPlaying = isPlaying,
                            isCurrentTrack = currentTrack?.id == track.id,
                            downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                            downloadProgress = downloadProgresses[track.id],
                            isFavorite = favoriteIds.contains(track.id),
                            trackList = quickMixTracks,
                            onTrackClick = { onTrackClick(track, quickMixTracks) },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onDownloadClick = { onDownloadTrack(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) }
                        )
                    }
                }
            }
        }
    }
    }

    // Folder Switcher Dialog
        if (showFolderSwitcherDialog) {
            AlertDialog(
                onDismissRequest = { showFolderSwitcherDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = str("switch_library_prompt"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = str("server_libraries_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // All Libraries option
                        val isAllSelectedInDialog = selectedMusicFolderIds.isEmpty() || (musicFolders.isNotEmpty() && selectedMusicFolderIds.size >= musicFolders.size)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelectedInDialog) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                onSelectAllMusicFolders()
                            },
                            modifier = Modifier.fillMaxWidth()
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
                                        imageVector = Icons.Filled.AllInclusive,
                                        contentDescription = null,
                                        tint = if (isAllSelectedInDialog) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = str("all_libraries_title"),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isAllSelectedInDialog) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Checkbox(
                                    checked = isAllSelectedInDialog,
                                    onCheckedChange = { onSelectAllMusicFolders() }
                                )
                            }
                        }

                        // Specific Music Folders
                        musicFolders.forEach { folder ->
                            val isChecked = !isAllSelectedInDialog && selectedMusicFolderIds.contains(folder.id)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    onSelectMusicFolder(folder.id)
                                },
                                modifier = Modifier.fillMaxWidth()
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
                                            tint = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "ID: ${folder.id}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleMusicFolder(folder.id) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFolderSwitcherDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Animated Sidebar Drawer for Libraries on HomeScreen has been moved to NaviromApp
    }
}

@Composable
fun AlbumDetailView(
    album: NaviromAlbum?,
    tracks: List<NaviromTrack>,
    currentTrack: NaviromTrack?,
    isPlaying: Boolean,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    favoriteIds: List<String>,
    appLanguage: AppLanguage,
    onBack: () -> Unit,
    onTrackClick: (NaviromTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onDownloadAlbum: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (NaviromTrack) -> Unit,
    onPlayNext: (NaviromTrack) -> Unit,
    onAddToQueue: (NaviromTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)
    val haptics = rememberNaviromHaptics()
    BackHandler(enabled = true) { onBack() }

    val totalSeconds = remember(tracks) { tracks.sumOf { it.durationSeconds } }
    val totalDurationFormatted = remember(totalSeconds) {
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        "${min}m ${sec}s"
    }

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
                .testTag("album_detail_view")
        ) {
        // Back Button & Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("album_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = album?.name ?: str("subtab_albums"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Album Header Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongAlbumCover(
                    coverArtUrl = album?.coverArtUrl,
                    contentDescription = album?.name,
                    isAlbum = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(110.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album?.name ?: "Album",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album?.artist ?: "Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${tracks.size} Songs • $totalDurationFormatted" + (if (album?.year != null) " • ${album.year}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons Row (Play, Shuffle, Download)
        item {
            ActionButtonGroup {
                DualAudioPlayButton(
                    text = str("btn_play_all"),
                    icon = Icons.Filled.PlayArrow,
                    onClickPlayer1 = onPlayAll,
                    tracks = tracks,
                    modifier = Modifier.defaultMinSize(minWidth = 110.dp),
                    testTag = "album_play_all"
                )

                DualAudioPlayButton(
                    text = str("btn_shuffle"),
                    icon = Icons.Filled.Shuffle,
                    onClickPlayer1 = onShuffleAll,
                    tracks = remember(tracks) { tracks.shuffled() },
                    modifier = Modifier.defaultMinSize(minWidth = 110.dp),
                    isTonal = true,
                    testTag = "album_shuffle_all"
                )

                OutlinedButton(
                    onClick = onDownloadAlbum,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("album_download_btn")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(str("btn_download"))
                }
            }
        }

        // Tracklist
        items(tracks, key = { it.id }) { track ->
            val index = tracks.indexOf(track) + 1
            TrackListItem(
                track = track,
                isPlaying = isPlaying,
                isCurrentTrack = currentTrack?.id == track.id,
                downloadStatus = downloadStatuses[track.id] ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = downloadProgresses[track.id],
                isFavorite = favoriteIds.contains(track.id),
                showCoverArt = false,
                trackIndex = index,
                trackList = tracks,
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
}

@Composable
private fun GreetingHeader(
    profileName: String,
    appLanguage: AppLanguage,
    onOpenSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingKey = when (currentHour) {
        in 5..11 -> "greeting_morning"
        in 12..17 -> "greeting_afternoon"
        in 18..22 -> "greeting_evening"
        else -> "greeting_night"
    }

    val greetingPrefix = str(greetingKey)
    val displayName = if (profileName.isNotBlank()) profileName.trim() else str("profile_default_name")
    val greetingText = "$greetingPrefix, $displayName"

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("library_greeting_header")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = str("greeting_subtitle"),
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.4.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // User Profile avatar badge (Inspired by MenuMobile.jpg profile circular avatar)
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = onOpenSidebar,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("greeting_profile_avatar")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialSubTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = if (isSelected) {
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            } else {
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.2.sp
                )
            },
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(1.5.dp)
                .width(28.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
        )
    }
}


