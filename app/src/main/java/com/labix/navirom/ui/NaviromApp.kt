package com.labix.navirom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labix.R
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.player.wlan.*
import com.labix.navirom.ui.components.*
import com.labix.navirom.update.AppUpdateInfo
import com.labix.navirom.update.UpdateState
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.labix.navirom.ui.util.rememberNaviromHaptics
import androidx.activity.compose.BackHandler
import com.labix.navirom.ui.screens.*
import com.labix.navirom.ui.util.RememberShakeDetector
import com.labix.navirom.ui.SongSortOrder
import com.labix.ui.theme.AccentEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaviromApp(
    viewModel: NaviromViewModel,
    modifier: Modifier = Modifier,
    onCloseApp: () -> Unit = {}
) {
    val haptics = rememberNaviromHaptics()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkConnectionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val isCrossfadeEnabled by viewModel.isCrossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationSeconds by viewModel.crossfadeDurationSeconds.collectAsStateWithLifecycle()
    val isVinylEffectEnabled by viewModel.isVinylEffectEnabled.collectAsStateWithLifecycle()
    val focusUsernameTrigger by viewModel.focusUsernameTrigger.collectAsStateWithLifecycle()

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val librarySubTab by viewModel.librarySubTab.collectAsStateWithLifecycle()
    val selectedAlbumId by viewModel.selectedAlbumId.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedArtistId by viewModel.selectedArtistId.collectAsStateWithLifecycle()
    val currentArtist by viewModel.currentArtist.collectAsStateWithLifecycle()
    val currentArtistAlbums by viewModel.currentArtistAlbums.collectAsStateWithLifecycle()
    val currentArtistSongs by viewModel.currentArtistSongs.collectAsStateWithLifecycle()
    val isLoadingArtistDetails by viewModel.isLoadingArtistDetails.collectAsStateWithLifecycle()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val newestAlbums by viewModel.newestAlbums.collectAsStateWithLifecycle()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val randomAlbums by viewModel.randomAlbums.collectAsStateWithLifecycle()
    val newestTracks by viewModel.newestTracks.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val quickMixTracks by viewModel.quickMixTracks.collectAsStateWithLifecycle()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val songSortOrder by viewModel.songSortOrder.collectAsStateWithLifecycle()
    val currentAlbumTracks by viewModel.currentAlbumTracks.collectAsStateWithLifecycle()
    val currentPlaylistTracks by viewModel.currentPlaylistTracks.collectAsStateWithLifecycle()

    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val listeningStats by viewModel.listeningStats.collectAsStateWithLifecycle()
    val isStatsScreenVisible by viewModel.isStatsScreenVisible.collectAsStateWithLifecycle()
    val shakeNotificationMessage by viewModel.shakeNotificationMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Global Shake detector: 2 seconds of shaking starts a random song from active library
    RememberShakeDetector(
        enabled = true,
        onShake2Seconds = {
            haptics.toggle()
            viewModel.playRandomTrackFromCurrentLibrary()
        }
    )

    LaunchedEffect(shakeNotificationMessage) {
        shakeNotificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissShakeNotification()
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val searchFocusTrigger by viewModel.searchFocusTrigger.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val allServers by viewModel.allServers.collectAsStateWithLifecycle()
    val cachedTracks by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val totalCacheSizeBytes by viewModel.totalCacheSizeBytes.collectAsStateWithLifecycle()
    val isOfflineOnlyMode by viewModel.isOfflineOnlyMode.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val localFolders by viewModel.localFolders.collectAsStateWithLifecycle()
    val allDiscoveredLocalFolders by viewModel.allDiscoveredLocalFolders.collectAsStateWithLifecycle()
    val settingsEnabledLocalFolderIds by viewModel.settingsEnabledLocalFolderIds.collectAsStateWithLifecycle()
    val disabledLocalFolderIds by viewModel.disabledLocalFolderIds.collectAsStateWithLifecycle()
    val isScanningLocalAudio by viewModel.isScanningLocalAudio.collectAsStateWithLifecycle()
    val currentMusicFolderPath by viewModel.currentMusicFolderPath.collectAsStateWithLifecycle()

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queue by viewModel.currentQueue.collectAsStateWithLifecycle()
    val queueIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val downloadProgresses by viewModel.downloadProgresses.collectAsStateWithLifecycle()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()
    val isQueueSheetVisible by viewModel.isQueueSheetVisible.collectAsStateWithLifecycle()
    val sleepTimerOptions by viewModel.sleepTimerOptions.collectAsStateWithLifecycle()
    val isDualAudioEnabled by viewModel.isDualAudioEnabled.collectAsStateWithLifecycle()
    val secondaryPlaybackState by viewModel.secondaryPlaybackState.collectAsStateWithLifecycle()
    val isDeckSyncEnabled by viewModel.isDeckSyncEnabled.collectAsStateWithLifecycle()
    val availableOutputDevices by viewModel.availableOutputDevices.collectAsStateWithLifecycle()
    val player1DeviceId by viewModel.player1DeviceId.collectAsStateWithLifecycle()
    val player2DeviceId by viewModel.player2DeviceId.collectAsStateWithLifecycle()
    val wlanSpeakerState by viewModel.wlanSpeakerState.collectAsStateWithLifecycle()
    val isWlanCastSheetVisible by viewModel.isWlanCastSheetVisible.collectAsStateWithLifecycle()

    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateGithubRepo by viewModel.updateGithubRepo.collectAsStateWithLifecycle()
    val lastUpdateCheckedTime by viewModel.lastUpdateCheckedTime.collectAsStateWithLifecycle()

    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000L)
            backPressedOnce = false
        }
    }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isFullPlayerVisible) {
            viewModel.setFullPlayerVisible(false)
        } else if (isQueueSheetVisible) {
            viewModel.setQueueSheetVisible(false)
        } else if (isStatsScreenVisible) {
            viewModel.setStatsScreenVisible(false)
        } else if (selectedAlbumId != null || selectedPlaylistId != null || selectedArtistId != null) {
            viewModel.selectAlbum(null)
            viewModel.selectPlaylist(null)
            viewModel.selectArtist(null)
        } else if (searchQuery.isNotBlank()) {
            viewModel.onSearchQueryChange("")
        } else if (currentTab != NaviromTab.LIBRARY) {
            viewModel.setTab(NaviromTab.LIBRARY)
        } else {
            if (backPressedOnce) {
                showExitDialog = true
            } else {
                backPressedOnce = true
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = str("press_back_again_to_exit"),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDualAudioContext provides DualAudioContext(
            isDualAudioEnabled = isDualAudioEnabled,
            onPlayPlayer1 = { viewModel.playTrack(it) },
            onPlayPlayer2 = { track, trackList -> viewModel.playSecondaryTrack(track, trackList ?: queue) },
            onPlayAllPlayer2 = { tracks ->
                tracks.firstOrNull()?.let { first ->
                    viewModel.playSecondaryTrack(first, tracks)
                }
            }
        )
    ) {
        ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullPlayerVisible && !isQueueSheetVisible && !isStatsScreenVisible,
        drawerContent = {
            LibrariesSidebarContent(
                musicFolders = serverState.musicFolders,
                selectedMusicFolderIds = serverState.selectedMusicFolderIds,
                onSelectMusicFolder = { viewModel.selectMusicFolder(it) },
                onToggleMusicFolder = { viewModel.toggleMusicFolder(it) },
                onSelectAllMusicFolders = { viewModel.selectAllMusicFolders() },
                onDeselectAllMusicFolders = { viewModel.deselectAllMusicFolders() },
                localFolders = localFolders,
                disabledLocalFolderIds = disabledLocalFolderIds,
                onToggleLocalFolder = { viewModel.toggleLocalFolder(it) },
                onSelectAllLocalFolders = { viewModel.selectAllLocalFolders() },
                onDeselectAllLocalFolders = { viewModel.deselectAllLocalFolders() },
                onGoToSettings = { viewModel.setTab(NaviromTab.SETTINGS) },
                onSyncLibrary = { viewModel.syncLibrary() },
                onCloseSidebar = { scope.launch { drawerState.close() } },
                wlanSpeakerState = wlanSpeakerState,
                onOpenWlanCast = { viewModel.showWlanCastSheet() },
                profileName = serverState.username,
                serverConnected = serverState.isConnected,
                onNavigateToTab = { tab -> viewModel.setTab(tab) },
                currentTab = currentTab,
                str = ::str
            )
        }
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp || (maxWidth >= 480.dp && maxHeight < 520.dp)

        if (isWideScreen) {
            // Big Screen / Tablet Layout: Side Navigation Rail + Content Area
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("tablet_nav_rail"),
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = "Navirom",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = currentTab == NaviromTab.LIBRARY,
                        onClick = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.LIBRARY)
                        },
                        icon = { Icon(if (currentTab == NaviromTab.LIBRARY) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = str("tab_home")) },
                        label = { Text(str("tab_home")) },
                        modifier = Modifier.testTag("rail_item_library"),
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == NaviromTab.PLAYLISTS,
                        onClick = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.PLAYLISTS)
                        },
                        icon = { Icon(if (currentTab == NaviromTab.PLAYLISTS) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic, contentDescription = str("tab_playlists")) },
                        label = { Text(str("tab_playlists")) },
                        modifier = Modifier.testTag("rail_item_playlists"),
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == NaviromTab.SEARCH,
                        onClick = {
                            haptics.tick()
                            if (currentTab == NaviromTab.SEARCH) {
                                viewModel.triggerSearchFocus()
                            } else {
                                viewModel.setTab(NaviromTab.SEARCH)
                            }
                        },
                        icon = { Icon(Icons.Filled.Search, contentDescription = str("tab_search")) },
                        label = { Text(str("tab_search")) },
                        modifier = Modifier.testTag("rail_item_search"),
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == NaviromTab.FOLDERS,
                        onClick = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.FOLDERS)
                        },
                        icon = { Icon(if (currentTab == NaviromTab.FOLDERS) Icons.Filled.Folder else Icons.Outlined.Folder, contentDescription = str("tab_music")) },
                        label = { Text(str("tab_music")) },
                        modifier = Modifier.testTag("rail_item_folders"),
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    NavigationRailItem(
                        selected = currentTab == NaviromTab.SETTINGS,
                        onClick = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.SETTINGS)
                        },
                        icon = { Icon(if (currentTab == NaviromTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = str("tab_settings")) },
                        label = { Text(str("tab_settings")) },
                        modifier = Modifier.testTag("rail_item_settings"),
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            haptics.click()
                            onCloseApp()
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .size(48.dp)
                            .testTag("tablet_close_app_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close App",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Top App Header
                    AppTopBar(
                        serverConnected = serverState.isConnected,
                        onOpenSearch = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.SEARCH)
                        },
                        onOpenSettings = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.SETTINGS)
                        },
                        onCloseApp = {
                            haptics.click()
                            onCloseApp()
                        },
                        onOpenSidebar = {
                            haptics.tick()
                            scope.launch { drawerState.open() }
                        }
                    )

                    // Tab Content
                    Box(modifier = Modifier.weight(1f)) {
                        TabContent(
                            currentTab = currentTab,
                            librarySubTab = librarySubTab,
                            onSubTabSelected = { viewModel.setLibrarySubTab(it) },
                            albums = albums,
                            newestAlbums = newestAlbums,
                            mostPlayedAlbums = mostPlayedAlbums,
                            randomAlbums = randomAlbums,
                            onRefreshRandomAlbums = { viewModel.refreshRandomAlbums() },
                            profileName = serverState.username,
                            newestTracks = newestTracks,
                            artists = artists,
                            playlists = playlists,
                            quickMixTracks = quickMixTracks,
                            recentlyPlayedTracks = recentlyPlayedTracks,
                            librarySongs = librarySongs,
                            songSortOrder = songSortOrder,
                            onSetSongSortOrder = { viewModel.setSongSortOrder(it) },
                            selectedAlbumId = selectedAlbumId,
                            selectedAlbumTracks = currentAlbumTracks,
                            selectedPlaylistId = selectedPlaylistId,
                            selectedPlaylistTracks = currentPlaylistTracks,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                            isSearching = isSearching,
                            searchResults = searchResults,
                            searchHistory = searchHistory,
                            onRemoveSearchHistory = { viewModel.removeSearchHistoryItem(it) },
                            onClearSearchHistory = { viewModel.clearSearchHistory() },
                            cachedTracks = cachedTracks,
                            totalCacheSizeBytes = totalCacheSizeBytes,
                            isOfflineOnlyMode = isOfflineOnlyMode,
                            serverState = serverState,
                            allServers = allServers,
                            favoriteIds = favoriteIds,
                            downloadStatuses = downloadStatuses,
                            downloadProgresses = downloadProgresses,
                            playbackState = playbackState,
                            appLanguage = appLanguage,
                            appThemeMode = appThemeMode,
                            statsSummary = listeningStats,
                            isCrossfadeEnabled = isCrossfadeEnabled,
                            crossfadeDurationSeconds = crossfadeDurationSeconds,
                            isVinylEffectEnabled = isVinylEffectEnabled,
                            onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                            onSetCrossfadeDurationSeconds = { viewModel.setCrossfadeDurationSeconds(it) },
                            onSetVinylEffectEnabled = { viewModel.setVinylEffectEnabled(it) },
                            onViewStats = { viewModel.setStatsScreenVisible(true) },
                            onSetLanguage = { viewModel.setLanguage(it) },
                            onSetThemeMode = { viewModel.setThemeMode(it) },
                            onSelectAlbum = { albumId ->
                                viewModel.selectAlbum(albumId)
                                viewModel.setTab(NaviromTab.LIBRARY) // Open and switch to library tab to view the album details
                            },
                            searchFocusTrigger = searchFocusTrigger,
                            onSelectPlaylist = { viewModel.selectPlaylist(it) },
                            onCreatePlaylist = { viewModel.createPlaylist(it) },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                            onPlayAll = { viewModel.playAll(it) },
                            onShuffleAll = { viewModel.shuffleAll(it) },
                            onDownloadTracks = { viewModel.downloadTracks(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDownloadTrack = { viewModel.downloadTrack(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onToggleOfflineOnly = { viewModel.setOfflineOnlyMode(it) },
                            onDeleteCachedTrack = { viewModel.deleteCachedTrack(it) },
                            onClearAllCache = { viewModel.clearAllCache() },
                            onUpdateServerConfig = { protocol, host, port, user, p, t, altHost -> viewModel.updateServerConfig(protocol, host, port, user, p, t, altHost) },
                            onConnectServer = { viewModel.connectServer() },
                            onSyncLibrary = { viewModel.syncLibrary() },
                            onScanNetwork = { viewModel.startAutoScanAndFocusUsername() },
                            onSelectMusicFolder = { viewModel.selectMusicFolder(it) },
                            onToggleMusicFolder = { viewModel.toggleMusicFolder(it) },
                            onSelectAllMusicFolders = { viewModel.selectAllMusicFolders() },
                            focusUsernameTrigger = focusUsernameTrigger,
                            onSelectServer = { viewModel.setServerConfig(it) },
                            onGoToSettings = { viewModel.setTab(NaviromTab.SETTINGS) },
                            onOpenSidebar = { scope.launch { drawerState.open() } },
                            updateState = updateState,
                            autoCheckUpdates = autoCheckUpdates,
                            githubRepo = updateGithubRepo,
                            lastUpdateCheckedTime = lastUpdateCheckedTime,
                            onCheckForUpdates = { viewModel.checkForAppUpdates(isManual = true) },
                            onDownloadAndInstallUpdate = { viewModel.downloadAndInstallUpdate(it) },
                            onSetAutoCheckUpdates = { viewModel.setAutoCheckUpdates(it) },
                            onSetGithubRepo = { viewModel.setUpdateGithubRepo(it) },
                            onDismissUpdate = { viewModel.dismissAppUpdate() },
                            localTracks = localTracks,
                            localFolders = localFolders,
                            disabledLocalFolderIds = disabledLocalFolderIds,
                            onToggleLocalFolder = { viewModel.toggleLocalFolder(it) },
                            onSetLocalFolderEnabled = { id, enabled -> viewModel.setLocalFolderEnabled(id, enabled) },
                            onSelectAllLocalFolders = { viewModel.selectAllLocalFolders() },
                            onDeselectAllLocalFolders = { viewModel.deselectAllLocalFolders() },
                            allDiscoveredLocalFolders = allDiscoveredLocalFolders,
                            settingsEnabledLocalFolderIds = settingsEnabledLocalFolderIds,
                            onToggleSettingsLocalFolder = { viewModel.toggleSettingsLocalFolder(it) },
                            onSetSettingsLocalFolderEnabled = { id, enabled -> viewModel.setSettingsLocalFolderEnabled(id, enabled) },
                            onSelectAllSettingsLocalFolders = { viewModel.selectAllSettingsLocalFolders() },
                            onDeselectAllSettingsLocalFolders = { viewModel.deselectAllSettingsLocalFolders() },
                            isScanningLocalAudio = isScanningLocalAudio,
                            onScanLocalAudio = { viewModel.scanLocalAudio() }
                        )
                    }

                    // Bottom Persistent Mini Player on Tablet
                    if (playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onExpandPlayer = { viewModel.setFullPlayerVisible(true) },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() },
                            onPrevious = { viewModel.previous() },
                            onSeekRelative = { viewModel.seekRelative(it) },
                            onClose = { viewModel.clearQueue() }
                        )
                    }
                }
            }
        } else {
            // Small Screen / Phone Layout: Top Bar + Content + Floating Mini Player + Bottom Navigation Bar
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AppTopBar(
                        serverConnected = serverState.isConnected,
                        onOpenSearch = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.SEARCH)
                        },
                        onOpenSettings = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.SETTINGS)
                        },
                        onCloseApp = {
                            haptics.click()
                            onCloseApp()
                        },
                        onOpenSidebar = {
                            haptics.tick()
                            scope.launch { drawerState.open() }
                        }
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Floating Mini Player
                        AnimatedVisibility(
                            visible = playbackState.currentTrack != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onExpandPlayer = {
                                    haptics.click()
                                    viewModel.setFullPlayerVisible(true)
                                },
                                onTogglePlayPause = {
                                    haptics.toggle()
                                    viewModel.togglePlayPause()
                                },
                                onNext = {
                                    haptics.click()
                                    viewModel.next()
                                },
                                onPrevious = {
                                    haptics.click()
                                    viewModel.previous()
                                },
                                onSeekRelative = { viewModel.seekRelative(it) },
                                onClose = {
                                    viewModel.clearQueue()
                                }
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                            NavigationBarItem(
                                selected = currentTab == NaviromTab.LIBRARY,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setTab(NaviromTab.LIBRARY)
                                },
                                icon = { Icon(if (currentTab == NaviromTab.LIBRARY) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = str("tab_library")) },
                                label = { Text(str("tab_library"), fontWeight = if (currentTab == NaviromTab.LIBRARY) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_library"),
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == NaviromTab.PLAYLISTS,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setTab(NaviromTab.PLAYLISTS)
                                },
                                icon = { Icon(if (currentTab == NaviromTab.PLAYLISTS) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic, contentDescription = str("tab_playlists")) },
                                label = { Text(str("tab_playlists"), fontWeight = if (currentTab == NaviromTab.PLAYLISTS) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_playlists"),
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == NaviromTab.FOLDERS,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setTab(NaviromTab.FOLDERS)
                                },
                                icon = { Icon(if (currentTab == NaviromTab.FOLDERS) Icons.Filled.Folder else Icons.Outlined.Folder, contentDescription = str("tab_music")) },
                                label = { Text(str("tab_music"), fontWeight = if (currentTab == NaviromTab.FOLDERS) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_folders"),
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == NaviromTab.SEARCH,
                                onClick = {
                                    haptics.tick()
                                    if (currentTab == NaviromTab.SEARCH) {
                                        viewModel.triggerSearchFocus()
                                    } else {
                                        viewModel.setTab(NaviromTab.SEARCH)
                                    }
                                },
                                icon = { Icon(Icons.Filled.Search, contentDescription = str("tab_search")) },
                                label = { Text(str("tab_search"), fontWeight = if (currentTab == NaviromTab.SEARCH) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_search"),
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == NaviromTab.SETTINGS,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setTab(NaviromTab.SETTINGS)
                                },
                                icon = { Icon(if (currentTab == NaviromTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = str("tab_settings")) },
                                label = { Text(str("tab_settings"), fontWeight = if (currentTab == NaviromTab.SETTINGS) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_settings"),
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    TabContent(
                        currentTab = currentTab,
                        librarySubTab = librarySubTab,
                        onSubTabSelected = { viewModel.setLibrarySubTab(it) },
                        albums = albums,
                        newestAlbums = newestAlbums,
                        mostPlayedAlbums = mostPlayedAlbums,
                        randomAlbums = randomAlbums,
                        onRefreshRandomAlbums = { viewModel.refreshRandomAlbums() },
                        profileName = serverState.username,
                        newestTracks = newestTracks,
                        artists = artists,
                        playlists = playlists,
                        quickMixTracks = quickMixTracks,
                        recentlyPlayedTracks = recentlyPlayedTracks,
                        librarySongs = librarySongs,
                        songSortOrder = songSortOrder,
                        onSetSongSortOrder = { viewModel.setSongSortOrder(it) },
                        selectedAlbumId = selectedAlbumId,
                        selectedAlbumTracks = currentAlbumTracks,
                        selectedPlaylistId = selectedPlaylistId,
                        selectedPlaylistTracks = currentPlaylistTracks,
                        selectedArtistId = selectedArtistId,
                        currentArtist = currentArtist,
                        currentArtistAlbums = currentArtistAlbums,
                        currentArtistSongs = currentArtistSongs,
                        isLoadingArtistDetails = isLoadingArtistDetails,
                        onSelectArtist = { viewModel.selectArtist(it) },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                        isSearching = isSearching,
                        searchResults = searchResults,
                        searchHistory = searchHistory,
                        onRemoveSearchHistory = { viewModel.removeSearchHistoryItem(it) },
                        onClearSearchHistory = { viewModel.clearSearchHistory() },
                        cachedTracks = cachedTracks,
                        totalCacheSizeBytes = totalCacheSizeBytes,
                        isOfflineOnlyMode = isOfflineOnlyMode,
                        serverState = serverState,
                        allServers = allServers,
                        favoriteIds = favoriteIds,
                        favoriteTracks = favoriteTracks,
                        downloadStatuses = downloadStatuses,
                        downloadProgresses = downloadProgresses,
                        playbackState = playbackState,
                        appLanguage = appLanguage,
                        appThemeMode = appThemeMode,
                        statsSummary = listeningStats,
                        isCrossfadeEnabled = isCrossfadeEnabled,
                        crossfadeDurationSeconds = crossfadeDurationSeconds,
                        isVinylEffectEnabled = isVinylEffectEnabled,
                        isDualAudioEnabled = isDualAudioEnabled,
                        onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                        onSetCrossfadeDurationSeconds = { viewModel.setCrossfadeDurationSeconds(it) },
                        onSetVinylEffectEnabled = { viewModel.setVinylEffectEnabled(it) },
                        onSetDualAudioEnabled = { viewModel.setDualAudioEnabled(it) },
                        wlanSpeakerState = wlanSpeakerState,
                        onOpenWlanCast = { viewModel.showWlanCastSheet() },
                        onViewStats = { viewModel.setStatsScreenVisible(true) },
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onSetThemeMode = { viewModel.setThemeMode(it) },
                        onSelectAlbum = { albumId ->
                            viewModel.selectAlbum(albumId)
                            viewModel.setTab(NaviromTab.LIBRARY) // Open and switch to library tab to view the album details
                        },
                        searchFocusTrigger = searchFocusTrigger,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onCreatePlaylist = { viewModel.createPlaylist(it) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onTrackClick = { track, list -> viewModel.playTrack(track, list) },
                        onPlayAll = { viewModel.playAll(it) },
                        onShuffleAll = { viewModel.shuffleAll(it) },
                        onDownloadTracks = { viewModel.downloadTracks(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDownloadTrack = { viewModel.downloadTrack(it) },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onToggleOfflineOnly = { viewModel.setOfflineOnlyMode(it) },
                        onDeleteCachedTrack = { viewModel.deleteCachedTrack(it) },
                        onClearAllCache = { viewModel.clearAllCache() },
                        onUpdateServerConfig = { protocol, host, port, user, p, t, altHost -> viewModel.updateServerConfig(protocol, host, port, user, p, t, altHost) },
                        onConnectServer = { viewModel.connectServer() },
                        onSyncLibrary = { viewModel.syncLibrary() },
                        onScanNetwork = { viewModel.startAutoScanAndFocusUsername() },
                        onSelectMusicFolder = { viewModel.selectMusicFolder(it) },
                        onToggleMusicFolder = { viewModel.toggleMusicFolder(it) },
                        onSelectAllMusicFolders = { viewModel.selectAllMusicFolders() },
                        focusUsernameTrigger = focusUsernameTrigger,
                        onSelectServer = { viewModel.setServerConfig(it) },
                        onGoToSettings = { viewModel.setTab(NaviromTab.SETTINGS) },
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                        updateState = updateState,
                        autoCheckUpdates = autoCheckUpdates,
                        githubRepo = updateGithubRepo,
                        lastUpdateCheckedTime = lastUpdateCheckedTime,
                        onCheckForUpdates = { viewModel.checkForAppUpdates(isManual = true) },
                        onDownloadAndInstallUpdate = { viewModel.downloadAndInstallUpdate(it) },
                        onSetAutoCheckUpdates = { viewModel.setAutoCheckUpdates(it) },
                        onSetGithubRepo = { viewModel.setUpdateGithubRepo(it) },
                        onDismissUpdate = { viewModel.dismissAppUpdate() },
                        localTracks = localTracks,
                        localFolders = localFolders,
                        disabledLocalFolderIds = disabledLocalFolderIds,
                        onToggleLocalFolder = { viewModel.toggleLocalFolder(it) },
                        onSetLocalFolderEnabled = { id, enabled -> viewModel.setLocalFolderEnabled(id, enabled) },
                        onSelectAllLocalFolders = { viewModel.selectAllLocalFolders() },
                        onDeselectAllLocalFolders = { viewModel.deselectAllLocalFolders() },
                        allDiscoveredLocalFolders = allDiscoveredLocalFolders,
                        settingsEnabledLocalFolderIds = settingsEnabledLocalFolderIds,
                        onToggleSettingsLocalFolder = { viewModel.toggleSettingsLocalFolder(it) },
                        onSetSettingsLocalFolderEnabled = { id, enabled -> viewModel.setSettingsLocalFolderEnabled(id, enabled) },
                        onSelectAllSettingsLocalFolders = { viewModel.selectAllSettingsLocalFolders() },
                        onDeselectAllSettingsLocalFolders = { viewModel.deselectAllSettingsLocalFolders() },
                        isScanningLocalAudio = isScanningLocalAudio,
                        onScanLocalAudio = { viewModel.scanLocalAudio() },
                        currentMusicFolderPath = currentMusicFolderPath,
                        onNavigateToMusicFolder = { viewModel.navigateToMusicFolder(it) },
                        onNavigateUpMusicFolder = { viewModel.navigateUpMusicFolder() },
                        onPlayFolder = { path, shuffle -> viewModel.playFolder(path, shuffle) }
                    )
                }
            }
        }

        // Full Screen Player Modal
        if (isFullPlayerVisible && playbackState.currentTrack != null) {
            FullPlayerModal(
                playbackState = playbackState,
                isFavorite = favoriteIds.contains(playbackState.currentTrack?.id),
                downloadStatus = downloadStatuses[playbackState.currentTrack?.id] ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgress = downloadProgresses[playbackState.currentTrack?.id],
                queueIndex = queueIndex,
                queueSize = queue.size,
                lyricsData = currentLyrics,
                appLanguage = appLanguage,
                isVinylEffectEnabled = isVinylEffectEnabled,
                sleepTimerOptions = sleepTimerOptions,
                isDualAudioEnabled = isDualAudioEnabled,
                secondaryPlaybackState = secondaryPlaybackState,
                isDeckSyncEnabled = isDeckSyncEnabled,
                onToggleDeckSync = { viewModel.toggleDeckSync() },
                availableOutputDevices = availableOutputDevices,
                player1DeviceId = player1DeviceId,
                player2DeviceId = player2DeviceId,
                onSetPlayer1PreferredDevice = { viewModel.setPlayer1PreferredDevice(it) },
                onSetPlayer2PreferredDevice = { viewModel.setPlayer2PreferredDevice(it) },
                onRefreshOutputDevices = { viewModel.refreshOutputDevices() },
                wlanSpeakerState = wlanSpeakerState,
                onOpenWlanCast = { viewModel.showWlanCastSheet() },
                onToggleSecondaryPlayPause = { viewModel.toggleSecondaryPlayPause() },
                onPlaySecondaryNext = { viewModel.playSecondaryNext() },
                onPlaySecondaryPrevious = { viewModel.playSecondaryPrevious() },
                onSeekSecondaryTo = { viewModel.seekSecondaryTo(it) },
                onSeekSecondaryRelative = { viewModel.seekSecondaryRelative(it) },
                onSetSecondaryVolume = { viewModel.setSecondaryVolume(it) },
                onStopSecondaryTrack = { viewModel.stopSecondaryTrack() },
                onDismiss = { viewModel.setFullPlayerVisible(false) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeekTo = { viewModel.seekTo(it) },
                onSeekRelative = { viewModel.seekRelative(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleFavorite = { playbackState.currentTrack?.let { viewModel.toggleFavorite(it.id) } },
                onDownloadTrack = { playbackState.currentTrack?.let { viewModel.downloadTrack(it) } },
                onOpenQueue = { viewModel.setQueueSheetVisible(true) },
                onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetSleepTimer = { minutes, options -> viewModel.setSleepTimer(minutes, options) },
                onUpdateSleepTimerOptions = { viewModel.updateSleepTimerOptions(it) },
                onRefetchLyrics = { viewModel.refetchCurrentLyrics() },
                onFetchTeksteShqipLyrics = { url ->
                    viewModel.fetchLyricsFromTeksteShqip(url, playbackState.currentTrack)
                },
                onArtistClick = { artistId ->
                    viewModel.setFullPlayerVisible(false)
                    viewModel.setTab(NaviromTab.LIBRARY)
                    viewModel.selectArtist(artistId)
                },
                onAlbumClick = { albumId ->
                    viewModel.setFullPlayerVisible(false)
                    viewModel.setTab(NaviromTab.LIBRARY)
                    viewModel.selectAlbum(albumId)
                }
            )
        }

        // Queue Bottom Sheet
        if (isQueueSheetVisible) {
            QueueDrawerSheet(
                queue = queue,
                currentIndex = queueIndex,
                unplayableTrackIds = playbackState.unplayableTrackIds,
                onSelectIndex = { viewModel.playAll(queue, it) },
                onRemoveIndex = { viewModel.removeFromQueue(it) },
                onClearQueue = { viewModel.clearQueue() },
                onDismiss = { viewModel.setQueueSheetVisible(false) }
            )
        }

        // WLAN Receiver Speaker Cast Bottom Sheet
        if (isWlanCastSheetVisible) {
            WlanSpeakerCastSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.hideWlanCastSheet() }
            )
        }

        // Listening Statistics Modal / Screen
        if (isStatsScreenVisible) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("listening_stats_dialog"),
                color = MaterialTheme.colorScheme.background
            ) {
                ListeningStatsScreen(
                    stats = listeningStats,
                    appLanguage = appLanguage,
                    onClearStats = { viewModel.clearListeningStats() },
                    onBack = { viewModel.setStatsScreenVisible(false) }
                )
            }
        }

        // GitHub In-App Update Dialog / Download Progress
        AppUpdateDialog(
            updateState = updateState,
            appLanguage = appLanguage,
            onDismiss = { viewModel.dismissAppUpdate() },
            onDownloadAndInstall = { viewModel.downloadAndInstallUpdate(it) }
        )

        // Exit Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = {
                    Text(
                        text = str("exit_dialog_title"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = str("exit_dialog_desc"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            (context as? Activity)?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(str("exit_dialog_confirm"))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitDialog = false }
                    ) {
                        Text(str("exit_dialog_cancel"))
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
    }
}
}

@Composable
private fun AppTopBar(
    serverConnected: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit,
    onOpenSidebar: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenSidebar != null) {
                    IconButton(
                        onClick = onOpenSidebar,
                        modifier = Modifier.size(40.dp).testTag("open_sidebar_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }

                Text(
                    text = "ZANA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("app_brand_title")
                )

                IconButton(
                    onClick = onCloseApp,
                    modifier = Modifier.size(40.dp).testTag("close_app_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun TabContent(
    currentTab: NaviromTab,
    librarySubTab: LibrarySubTab,
    onSubTabSelected: (LibrarySubTab) -> Unit,
    albums: List<com.labix.navirom.data.model.NaviromAlbum>,
    newestAlbums: List<com.labix.navirom.data.model.NaviromAlbum> = emptyList(),
    mostPlayedAlbums: List<com.labix.navirom.data.model.NaviromAlbum> = emptyList(),
    randomAlbums: List<com.labix.navirom.data.model.NaviromAlbum> = emptyList(),
    onRefreshRandomAlbums: () -> Unit = {},
    profileName: String = "",
    newestTracks: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    artists: List<com.labix.navirom.data.model.NaviromArtist>,
    playlists: List<com.labix.navirom.data.model.NaviromPlaylist>,
    quickMixTracks: List<com.labix.navirom.data.model.NaviromTrack>,
    recentlyPlayedTracks: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    librarySongs: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    songSortOrder: SongSortOrder = SongSortOrder.NAME,
    onSetSongSortOrder: (SongSortOrder) -> Unit = {},
    selectedAlbumId: String?,
    selectedAlbumTracks: List<com.labix.navirom.data.model.NaviromTrack>,
    selectedPlaylistId: String?,
    selectedPlaylistTracks: List<com.labix.navirom.data.model.NaviromTrack>,
    selectedArtistId: String? = null,
    currentArtist: com.labix.navirom.data.model.NaviromArtist? = null,
    currentArtistAlbums: List<com.labix.navirom.data.model.NaviromAlbum> = emptyList(),
    currentArtistSongs: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    isLoadingArtistDetails: Boolean = false,
    onSelectArtist: (String?) -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchFocusTrigger: Int = 0,
    isSearching: Boolean,
    searchResults: Triple<List<com.labix.navirom.data.model.NaviromArtist>, List<com.labix.navirom.data.model.NaviromAlbum>, List<com.labix.navirom.data.model.NaviromTrack>>,
    searchHistory: List<String> = emptyList(),
    onRemoveSearchHistory: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    cachedTracks: List<com.labix.navirom.data.local.CachedTrackEntity>,
    totalCacheSizeBytes: Long,
    isOfflineOnlyMode: Boolean,
    serverState: ServerConnectionUiState,
    allServers: List<com.labix.navirom.data.local.ServerConfigEntity>,
    favoriteIds: List<String>,
    favoriteTracks: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    playbackState: com.labix.navirom.data.model.PlaybackState,
    appLanguage: AppLanguage,
    appThemeMode: AppThemeMode,
    statsSummary: com.labix.navirom.data.stats.ListeningStatsSummary = com.labix.navirom.data.stats.ListeningStatsSummary(),
    isCrossfadeEnabled: Boolean,
    crossfadeDurationSeconds: Int = 5,
    isVinylEffectEnabled: Boolean = false,
    isDualAudioEnabled: Boolean = false,
    wlanSpeakerState: WlanSpeakerState? = null,
    onOpenWlanCast: (() -> Unit)? = null,
    onSetCrossfadeEnabled: (Boolean) -> Unit,
    onSetCrossfadeDurationSeconds: (Int) -> Unit = {},
    onSetVinylEffectEnabled: (Boolean) -> Unit = {},
    onSetDualAudioEnabled: (Boolean) -> Unit = {},
    onViewStats: () -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onSelectPlaylist: (String?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onTrackClick: (com.labix.navirom.data.model.NaviromTrack, List<com.labix.navirom.data.model.NaviromTrack>) -> Unit,
    onPlayAll: (List<com.labix.navirom.data.model.NaviromTrack>) -> Unit,
    onShuffleAll: (List<com.labix.navirom.data.model.NaviromTrack>) -> Unit,
    onDownloadTracks: (List<com.labix.navirom.data.model.NaviromTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadTrack: (com.labix.navirom.data.model.NaviromTrack) -> Unit,
    onPlayNext: (com.labix.navirom.data.model.NaviromTrack) -> Unit,
    onAddToQueue: (com.labix.navirom.data.model.NaviromTrack) -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onDeleteCachedTrack: (String) -> Unit,
    onClearAllCache: () -> Unit,
    onUpdateServerConfig: (String, String, String, String, String, Boolean, String) -> Unit,
    onConnectServer: () -> Unit,
    onSyncLibrary: () -> Unit,
    onScanNetwork: () -> Unit,
    onSelectMusicFolder: (String?) -> Unit = {},
    onToggleMusicFolder: (String) -> Unit = {},
    onSelectAllMusicFolders: () -> Unit = {},
    focusUsernameTrigger: Long = 0L,
    onSelectServer: (com.labix.navirom.data.local.ServerConfigEntity) -> Unit,
    onGoToSettings: () -> Unit,
    onOpenSidebar: () -> Unit,
    updateState: UpdateState = UpdateState.Idle,
    autoCheckUpdates: Boolean = true,
    githubRepo: String = "labibllaca/Zana-player",
    lastUpdateCheckedTime: Long = 0L,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: (AppUpdateInfo) -> Unit = {},
    onSetAutoCheckUpdates: (Boolean) -> Unit = {},
    onSetGithubRepo: (String) -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    localTracks: List<com.labix.navirom.data.model.NaviromTrack> = emptyList(),
    localFolders: List<com.labix.navirom.data.local.LocalMusicFolder> = emptyList(),
    disabledLocalFolderIds: Set<String> = emptySet(),
    onToggleLocalFolder: (String) -> Unit = {},
    onSetLocalFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onSelectAllLocalFolders: () -> Unit = {},
    onDeselectAllLocalFolders: () -> Unit = {},
    allDiscoveredLocalFolders: List<com.labix.navirom.data.local.LocalMusicFolder> = emptyList(),
    settingsEnabledLocalFolderIds: Set<String> = emptySet(),
    onToggleSettingsLocalFolder: (String) -> Unit = {},
    onSetSettingsLocalFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onSelectAllSettingsLocalFolders: () -> Unit = {},
    onDeselectAllSettingsLocalFolders: () -> Unit = {},
    isScanningLocalAudio: Boolean = false,
    onScanLocalAudio: () -> Unit = {},
    currentMusicFolderPath: String = "",
    onNavigateToMusicFolder: (String) -> Unit = {},
    onNavigateUpMusicFolder: () -> Boolean = { false },
    onPlayFolder: (String, Boolean) -> Unit = { _, _ -> }
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (fadeIn(animationSpec = tween(220)) +
                slideInHorizontally(animationSpec = tween(220)) { width -> if (forward) width / 5 else -width / 5 })
                .togetherWith(
                    fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(160)) { width -> if (forward) -width / 5 else width / 5 }
                )
        },
        label = "MainTabTransition"
    ) { activeTab ->
        when (activeTab) {
            NaviromTab.LIBRARY -> {
            LibraryScreen(
                subTab = librarySubTab,
                onSubTabSelected = onSubTabSelected,
                albums = albums,
                newestAlbums = newestAlbums,
                mostPlayedAlbums = mostPlayedAlbums,
                randomAlbums = randomAlbums,
                onRefreshRandomAlbums = onRefreshRandomAlbums,
                profileName = profileName,
                newestTracks = newestTracks,
                artists = artists,
                quickMixTracks = quickMixTracks,
                recentlyPlayedTracks = recentlyPlayedTracks,
                librarySongs = librarySongs,
                songSortOrder = songSortOrder,
                onSetSongSortOrder = onSetSongSortOrder,
                selectedAlbumId = selectedAlbumId,
                selectedAlbumTracks = selectedAlbumTracks,
                currentTrack = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying,
                downloadStatuses = downloadStatuses,
                downloadProgresses = downloadProgresses,
                favoriteIds = favoriteIds,
                favoriteTracks = favoriteTracks,
                appLanguage = appLanguage,
                musicFolders = serverState.musicFolders,
                selectedMusicFolderId = serverState.selectedMusicFolderId,
                selectedMusicFolderIds = serverState.selectedMusicFolderIds,
                onSelectMusicFolder = onSelectMusicFolder,
                onToggleMusicFolder = onToggleMusicFolder,
                onSelectAllMusicFolders = onSelectAllMusicFolders,
                onSyncLibrary = onSyncLibrary,
                onSelectAlbum = onSelectAlbum,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onDownloadTracks = onDownloadTracks,
                onToggleFavorite = onToggleFavorite,
                onDownloadTrack = onDownloadTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onGoToSettings = onGoToSettings,
                onScanNetwork = onScanNetwork,
                onOpenSidebar = onOpenSidebar,
                selectedArtistId = selectedArtistId,
                currentArtist = currentArtist,
                currentArtistAlbums = currentArtistAlbums,
                currentArtistSongs = currentArtistSongs,
                isLoadingArtistDetails = isLoadingArtistDetails,
                onSelectArtist = onSelectArtist,
                localTracks = localTracks,
                localFolders = localFolders,
                disabledLocalFolderIds = disabledLocalFolderIds,
                onToggleLocalFolder = onToggleLocalFolder,
                onSetLocalFolderEnabled = onSetLocalFolderEnabled,
                onSelectAllLocalFolders = onSelectAllLocalFolders,
                onDeselectAllLocalFolders = onDeselectAllLocalFolders,
                isScanningLocalAudio = isScanningLocalAudio,
                onScanLocalAudio = onScanLocalAudio
            )
        }
        NaviromTab.PLAYLISTS -> {
            PlaylistsScreen(
                playlists = playlists,
                selectedPlaylistId = selectedPlaylistId,
                selectedPlaylistTracks = selectedPlaylistTracks,
                currentTrack = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying,
                downloadStatuses = downloadStatuses,
                downloadProgresses = downloadProgresses,
                favoriteIds = favoriteIds,
                appLanguage = appLanguage,
                onSelectPlaylist = onSelectPlaylist,
                onCreatePlaylist = onCreatePlaylist,
                onDeletePlaylist = onDeletePlaylist,
                onTrackClick = onTrackClick,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                onDownloadPlaylist = onDownloadTracks,
                onToggleFavorite = onToggleFavorite,
                onDownloadTrack = onDownloadTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue
            )
        }
        NaviromTab.SEARCH -> {
            SearchScreen(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                isSearching = isSearching,
                searchResults = searchResults,
                searchHistory = searchHistory,
                onRemoveSearchHistory = onRemoveSearchHistory,
                onClearSearchHistory = onClearSearchHistory,
                currentTrack = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying,
                downloadStatuses = downloadStatuses,
                downloadProgresses = downloadProgresses,
                favoriteIds = favoriteIds,
                appLanguage = appLanguage,
                onSelectAlbum = { onSelectAlbum(it) },
                onTrackClick = onTrackClick,
                onToggleFavorite = onToggleFavorite,
                onDownloadTrack = onDownloadTrack,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                searchFocusTrigger = searchFocusTrigger
            )
        }
        NaviromTab.FOLDERS -> {
            MusicFoldersScreen(
                currentFolderPath = currentMusicFolderPath,
                allLocalTracks = localTracks,
                allDiscoveredFolders = allDiscoveredLocalFolders,
                isScanning = isScanningLocalAudio,
                playbackState = playbackState,
                appLanguage = appLanguage,
                onNavigateToFolder = onNavigateToMusicFolder,
                onNavigateUp = onNavigateUpMusicFolder,
                onRescanStorage = onScanLocalAudio,
                onPlayTrack = onTrackClick,
                onPlayFolder = onPlayFolder,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onToggleFavorite = { track -> onToggleFavorite(track.id) },
                favoriteIds = favoriteIds.toSet()
            )
        }
        NaviromTab.SETTINGS -> {
            ServerSettingsScreen(
                serverState = serverState,
                allServers = allServers,
                onSelectServer = onSelectServer,
                appLanguage = appLanguage,
                appThemeMode = appThemeMode,
                statsSummary = statsSummary,
                isCrossfadeEnabled = isCrossfadeEnabled,
                crossfadeDurationSeconds = crossfadeDurationSeconds,
                isVinylEffectEnabled = isVinylEffectEnabled,
                isDualAudioEnabled = isDualAudioEnabled,
                wlanSpeakerState = wlanSpeakerState,
                onOpenWlanCast = onOpenWlanCast,
                onSetCrossfadeEnabled = onSetCrossfadeEnabled,
                onSetCrossfadeDurationSeconds = onSetCrossfadeDurationSeconds,
                onSetVinylEffectEnabled = onSetVinylEffectEnabled,
                onSetDualAudioEnabled = onSetDualAudioEnabled,
                onViewStats = onViewStats,
                onSetLanguage = onSetLanguage,
                onSetThemeMode = onSetThemeMode,
                onUpdateConfig = onUpdateServerConfig,
                onConnect = onConnectServer,
                onSyncLibrary = onSyncLibrary,
                onScanNetwork = onScanNetwork,
                onSelectMusicFolder = onSelectMusicFolder,
                onToggleMusicFolder = onToggleMusicFolder,
                onSelectAllMusicFolders = onSelectAllMusicFolders,
                allDiscoveredLocalFolders = allDiscoveredLocalFolders,
                settingsEnabledLocalFolderIds = settingsEnabledLocalFolderIds,
                onToggleSettingsLocalFolder = onToggleSettingsLocalFolder,
                onSetSettingsLocalFolderEnabled = onSetSettingsLocalFolderEnabled,
                onSelectAllSettingsLocalFolders = onSelectAllSettingsLocalFolders,
                onDeselectAllSettingsLocalFolders = onDeselectAllSettingsLocalFolders,
                isScanningLocalAudio = isScanningLocalAudio,
                onScanLocalAudio = onScanLocalAudio,
                focusUsernameTrigger = focusUsernameTrigger,
                updateState = updateState,
                autoCheckUpdates = autoCheckUpdates,
                githubRepo = githubRepo,
                lastUpdateCheckedTime = lastUpdateCheckedTime,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                onSetAutoCheckUpdates = onSetAutoCheckUpdates,
                onSetGithubRepo = onSetGithubRepo,
                onDismissUpdate = onDismissUpdate
            )
        }
    }
}
}
