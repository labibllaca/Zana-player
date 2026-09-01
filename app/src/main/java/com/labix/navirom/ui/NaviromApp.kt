package com.labix.navirom.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labix.navirom.data.model.DownloadStatus
import com.labix.navirom.ui.components.*
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

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val queue by viewModel.currentQueue.collectAsStateWithLifecycle()
    val queueIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()
    val downloadStatuses by viewModel.downloadStatuses.collectAsStateWithLifecycle()
    val downloadProgresses by viewModel.downloadProgresses.collectAsStateWithLifecycle()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()
    val isQueueSheetVisible by viewModel.isQueueSheetVisible.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isFullPlayerVisible) {
            viewModel.setFullPlayerVisible(false)
        } else if (isQueueSheetVisible) {
            viewModel.setQueueSheetVisible(false)
        } else if (isStatsScreenVisible) {
            viewModel.setStatsScreenVisible(false)
        } else {
            // Prevent back button from closing app
        }
    }

    fun str(key: String): String = NaviromStrings.get(key, appLanguage)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullPlayerVisible && !isQueueSheetVisible && !isStatsScreenVisible,
        drawerContent = {
            LibrariesSidebarContent(
                musicFolders = serverState.musicFolders,
                selectedMusicFolderIds = serverState.selectedMusicFolderIds,
                onToggleMusicFolder = { viewModel.toggleMusicFolder(it) },
                onSelectAllMusicFolders = { viewModel.selectAllMusicFolders() },
                onSyncLibrary = { viewModel.syncLibrary() },
                onCloseSidebar = { scope.launch { drawerState.close() } },
                str = ::str
            )
        }
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

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
                        selected = currentTab == NaviromTab.OFFLINE,
                        onClick = {
                            haptics.tick()
                            viewModel.setTab(NaviromTab.OFFLINE)
                        },
                        icon = { Icon(if (currentTab == NaviromTab.OFFLINE) Icons.Filled.CloudDone else Icons.Outlined.CloudDone, contentDescription = str("tab_offline")) },
                        label = { Text(str("tab_offline")) },
                        modifier = Modifier.testTag("rail_item_offline"),
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
                            onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                            onSetCrossfadeDurationSeconds = { viewModel.setCrossfadeDurationSeconds(it) },
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
                            onOpenSidebar = { scope.launch { drawerState.open() } }
                        )
                    }

                    // Bottom Persistent Mini Player on Tablet
                    if (playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onExpandPlayer = { viewModel.setFullPlayerVisible(true) },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.next() }
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
                        }
                    )
                },
                bottomBar = {
                    Column {
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
                                }
                            )
                        }

                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
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
                                selected = currentTab == NaviromTab.OFFLINE,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setTab(NaviromTab.OFFLINE)
                                },
                                icon = { Icon(if (currentTab == NaviromTab.OFFLINE) Icons.Filled.CloudDone else Icons.Outlined.CloudDone, contentDescription = str("tab_offline")) },
                                label = { Text(str("tab_offline"), fontWeight = if (currentTab == NaviromTab.OFFLINE) FontWeight.Bold else FontWeight.Medium) },
                                modifier = Modifier.testTag("nav_item_offline"),
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
                        onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                        onSetCrossfadeDurationSeconds = { viewModel.setCrossfadeDurationSeconds(it) },
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
                        onOpenSidebar = { scope.launch { drawerState.open() } }
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
                queueSize = queue.size,
                lyricsData = currentLyrics,
                appLanguage = appLanguage,
                onDismiss = { viewModel.setFullPlayerVisible(false) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeekTo = { viewModel.seekTo(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleFavorite = { playbackState.currentTrack?.let { viewModel.toggleFavorite(it.id) } },
                onDownloadTrack = { playbackState.currentTrack?.let { viewModel.downloadTrack(it) } },
                onOpenQueue = { viewModel.setQueueSheetVisible(true) },
                onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onRefetchLyrics = { viewModel.refetchCurrentLyrics() },
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
    }
    }
}

@Composable
private fun AppTopBar(
    serverConnected: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Navirom Logo",
                    tint = if (serverConnected) Color.Green else Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Zana",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCloseApp,
                modifier = Modifier.size(44.dp).testTag("close_app_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close App",
                    tint = MaterialTheme.colorScheme.error
                )
            }
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
    downloadStatuses: Map<String, DownloadStatus>,
    downloadProgresses: Map<String, Float>,
    playbackState: com.labix.navirom.data.model.PlaybackState,
    appLanguage: AppLanguage,
    appThemeMode: AppThemeMode,
    statsSummary: com.labix.navirom.data.stats.ListeningStatsSummary = com.labix.navirom.data.stats.ListeningStatsSummary(),
    isCrossfadeEnabled: Boolean,
    crossfadeDurationSeconds: Int = 5,
    onSetCrossfadeEnabled: (Boolean) -> Unit,
    onSetCrossfadeDurationSeconds: (Int) -> Unit = {},
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
    onOpenSidebar: () -> Unit
) {
    when (currentTab) {
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
                onSelectArtist = onSelectArtist
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
        NaviromTab.OFFLINE -> {
            OfflineCacheScreen(
                cachedTracks = cachedTracks,
                totalCacheSizeBytes = totalCacheSizeBytes,
                isOfflineOnlyMode = isOfflineOnlyMode,
                onToggleOfflineOnly = onToggleOfflineOnly,
                currentTrack = playbackState.currentTrack,
                isPlaying = playbackState.isPlaying,
                favoriteIds = favoriteIds,
                appLanguage = appLanguage,
                onPlayAll = onPlayAll,
                onTrackClick = onTrackClick,
                onToggleFavorite = onToggleFavorite,
                onDeleteCachedTrack = onDeleteCachedTrack,
                onClearAllCache = onClearAllCache,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue
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
                onSetCrossfadeEnabled = onSetCrossfadeEnabled,
                onSetCrossfadeDurationSeconds = onSetCrossfadeDurationSeconds,
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
                focusUsernameTrigger = focusUsernameTrigger
            )
        }
    }
}
