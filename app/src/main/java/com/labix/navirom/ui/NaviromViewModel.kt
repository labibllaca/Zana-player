package com.labix.navirom.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.labix.navirom.data.api.NaviromSubsonicClient
import com.labix.navirom.data.cache.OfflineDownloadManager
import com.labix.navirom.data.local.*
import com.labix.navirom.data.lyrics.LyricsData
import com.labix.navirom.data.lyrics.LyricsRepository
import com.labix.navirom.data.model.*
import com.labix.navirom.data.stats.ListeningStatsManager
import com.labix.navirom.data.stats.ListeningStatsSummary
import com.labix.navirom.player.AudioPlayerController
import com.labix.navirom.update.AppUpdateInfo
import com.labix.navirom.update.UpdateManager
import com.labix.navirom.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Calendar
import java.util.UUID

enum class NaviromTab {
    LIBRARY,
    PLAYLISTS,
    SEARCH,
    OFFLINE,
    SETTINGS
}

enum class LibrarySubTab {
    OVERVIEW,
    SONGS,
    ALBUMS,
    ARTISTS,
    NEWEST,
    QUICK_MIX,
    RECENT,
    LIBRARIES,
    GENRES
}

enum class SongSortOrder {
    NAME,
    DURATION,
    RECENTLY_ADDED,
    ARTIST
}

data class ServerConnectionUiState(
    val serverUrl: String = "",
    val protocol: String = "http",
    val host: String = "",
    val port: String = "4533",
    val username: String = "",
    val password: String = "",
    val useTokenAuth: Boolean = true,
    val isConnecting: Boolean = false,
    val connectionStatusMessage: String? = null,
    val isConnected: Boolean = false,
    val serverVersion: String? = null,
    val musicFolders: List<com.labix.navirom.data.api.dto.MusicFolderDto> = emptyList(),
    val selectedMusicFolderIds: Set<String> = emptySet(),
    val alternativeHost: String = ""
) {
    val selectedMusicFolderId: String? get() = if (selectedMusicFolderIds.size == 1) selectedMusicFolderIds.first() else null
}

class NaviromViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("navirom_prefs", Context.MODE_PRIVATE)

    private val db = NaviromDatabase.getDatabase(application)
    private val cachedTrackDao = db.cachedTrackDao()
    private val playlistDao = db.playlistDao()
    private val favoriteDao = db.favoriteDao()
    private val serverConfigDao = db.serverConfigDao()
    private val playbackHistoryDao = db.playbackHistoryDao()
    private val lyricsDao = db.lyricsDao()
    private val playbackQueueDao = db.playbackQueueDao()
    private val recentSongsDao = db.recentSongsDao()

    val subsonicClient = NaviromSubsonicClient()
    val downloadManager = OfflineDownloadManager(application, cachedTrackDao)
    val playerController = AudioPlayerController(application, downloadManager, cachedTrackDao, playbackQueueDao)
    val statsManager = ListeningStatsManager(playbackHistoryDao)
    val lyricsRepository = LyricsRepository(application, lyricsDao, subsonicClient)
    val recentSongsRepository = RecentSongsRepository(recentSongsDao)
    val updateManager = UpdateManager(application)

    val updateState: StateFlow<UpdateState> = updateManager.updateState
    val autoCheckUpdates: StateFlow<Boolean> = updateManager.autoCheckEnabled
    val updateGithubRepo: StateFlow<String> = updateManager.githubRepo
    val lastUpdateCheckedTime: StateFlow<Long> = updateManager.lastCheckedTime

    // Language & Theme states
    private val _appLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("app_language", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name)
        } catch (e: Exception) {
            AppLanguage.ENGLISH
        }
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _appThemeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString("app_theme_mode", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    )
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode.asStateFlow()

    // Current navigation state
    private val _currentTab = MutableStateFlow(NaviromTab.LIBRARY)
    val currentTab: StateFlow<NaviromTab> = _currentTab.asStateFlow()

    private val _librarySubTab = MutableStateFlow(LibrarySubTab.OVERVIEW)
    val librarySubTab: StateFlow<LibrarySubTab> = _librarySubTab.asStateFlow()

    private val _selectedAlbumId = MutableStateFlow<String?>(null)
    val selectedAlbumId: StateFlow<String?> = _selectedAlbumId.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _selectedArtistId = MutableStateFlow<String?>(null)
    val selectedArtistId: StateFlow<String?> = _selectedArtistId.asStateFlow()

    private val _currentArtist = MutableStateFlow<NaviromArtist?>(null)
    val currentArtist: StateFlow<NaviromArtist?> = _currentArtist.asStateFlow()

    private val _currentArtistAlbums = MutableStateFlow<List<NaviromAlbum>>(emptyList())
    val currentArtistAlbums: StateFlow<List<NaviromAlbum>> = _currentArtistAlbums.asStateFlow()

    private val _currentArtistSongs = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val currentArtistSongs: StateFlow<List<NaviromTrack>> = _currentArtistSongs.asStateFlow()

    private val _isLoadingArtistDetails = MutableStateFlow(false)
    val isLoadingArtistDetails: StateFlow<Boolean> = _isLoadingArtistDetails.asStateFlow()

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()

    private val _isStatsScreenVisible = MutableStateFlow(false)
    val isStatsScreenVisible: StateFlow<Boolean> = _isStatsScreenVisible.asStateFlow()

    // Offline only mode toggle
    private val _isOfflineOnlyMode = MutableStateFlow(false)
    val isOfflineOnlyMode: StateFlow<Boolean> = _isOfflineOnlyMode.asStateFlow()

    // Data lists
    private val _albums = MutableStateFlow<List<NaviromAlbum>>(emptyList())
    val albums: StateFlow<List<NaviromAlbum>> = _albums.asStateFlow()

    private val _newestAlbums = MutableStateFlow<List<NaviromAlbum>>(emptyList())
    val newestAlbums: StateFlow<List<NaviromAlbum>> = _newestAlbums.asStateFlow()

    private val _mostPlayedAlbums = MutableStateFlow<List<NaviromAlbum>>(emptyList())
    val mostPlayedAlbums: StateFlow<List<NaviromAlbum>> = _mostPlayedAlbums.asStateFlow()

    private val _randomAlbums = MutableStateFlow<List<NaviromAlbum>>(emptyList())
    val randomAlbums: StateFlow<List<NaviromAlbum>> = _randomAlbums.asStateFlow()

    private val _newestTracks = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val newestTracks: StateFlow<List<NaviromTrack>> = _newestTracks.asStateFlow()

    private val _artists = MutableStateFlow<List<NaviromArtist>>(emptyList())
    val artists: StateFlow<List<NaviromArtist>> = _artists.asStateFlow()

    private val _playlists = MutableStateFlow<List<NaviromPlaylist>>(emptyList())

    private val _currentAlbumTracks = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val currentAlbumTracks: StateFlow<List<NaviromTrack>> = _currentAlbumTracks.asStateFlow()

    private val _currentPlaylistTracks = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val currentPlaylistTracks: StateFlow<List<NaviromTrack>> = _currentPlaylistTracks.asStateFlow()

    private val _quickMixTracks = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val quickMixTracks: StateFlow<List<NaviromTrack>> = _quickMixTracks.asStateFlow()

    private val _rawLibrarySongs = MutableStateFlow<List<NaviromTrack>>(emptyList())

    val recentlyPlayedTracks: StateFlow<List<NaviromTrack>> = recentSongsRepository.getRecentlyPlayed(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _songSortOrder = MutableStateFlow(SongSortOrder.NAME)
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    val librarySongs: StateFlow<List<NaviromTrack>> = combine(_rawLibrarySongs, _songSortOrder) { songs, order ->
        when (order) {
            SongSortOrder.NAME -> songs.sortedBy { it.title.lowercase() }
            SongSortOrder.DURATION -> songs.sortedByDescending { it.durationSeconds }
            SongSortOrder.RECENTLY_ADDED -> songs // Order as received from newest/last input
            SongSortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _shakeNotificationMessage = MutableStateFlow<String?>(null)
    val shakeNotificationMessage: StateFlow<String?> = _shakeNotificationMessage.asStateFlow()

    // Lyrics State
    private val _currentLyrics = MutableStateFlow(LyricsData())
    val currentLyrics: StateFlow<LyricsData> = _currentLyrics.asStateFlow()

    // Listening Stats State
    val listeningStats: StateFlow<ListeningStatsSummary> = statsManager.statsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningStatsSummary())

    // Focus Username event trigger
    private val _focusUsernameTrigger = MutableStateFlow(0L)
    val focusUsernameTrigger: StateFlow<Long> = _focusUsernameTrigger.asStateFlow()

    fun requestFocusUsername() {
        _focusUsernameTrigger.value = System.currentTimeMillis()
    }

    fun startAutoScanAndFocusUsername() {
        setTab(NaviromTab.SETTINGS)
        scanLocalNetwork()
        requestFocusUsername()
    }

    // Search results
    // Search history state
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFocusTrigger = MutableStateFlow(0)
    val searchFocusTrigger: StateFlow<Int> = _searchFocusTrigger.asStateFlow()

    fun triggerSearchFocus() {
        _searchFocusTrigger.value = _searchFocusTrigger.value + 1
    }

    private val _searchResults = MutableStateFlow<Triple<List<NaviromArtist>, List<NaviromAlbum>, List<NaviromTrack>>>(
        Triple(emptyList(), emptyList(), emptyList())
    )
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    val allServers: StateFlow<List<ServerConfigEntity>> = serverConfigDao.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Server connection state
    private val _serverState = MutableStateFlow(ServerConnectionUiState())
    val serverState: StateFlow<ServerConnectionUiState> = _serverState.asStateFlow()

    // Offline cached tracks from Room
    val cachedTracks: StateFlow<List<CachedTrackEntity>> = cachedTrackDao.getAllCachedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCacheSizeBytes: StateFlow<Long> = cachedTrackDao.getTotalCacheSizeBytes()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val favoriteIds: StateFlow<List<String>> = favoriteDao.getAllFavoriteTrackIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<NaviromPlaylist>> = combine(_playlists, favoriteIds) { plist, favs ->
        val favoritePlaylist = NaviromPlaylist(
            id = "favorites_dynamic_playlist_id",
            name = "Favorite Songs",
            comment = "Your favorite tracks",
            songCount = favs.size,
            durationSeconds = 0,
            coverArt = "",
            isLocal = true
        )
        listOf(favoritePlaylist) + plist
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadStatuses = downloadManager.downloadStatusMap
    val downloadProgresses = downloadManager.downloadProgressMap
    val playbackState = playerController.playbackState
    val currentQueue = playerController.queue
    val currentQueueIndex = playerController.currentIndex

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var networkScanJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkConnectionState()
        }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            checkConnectionState()
        }
        override fun onLost(network: Network) {
            checkConnectionState()
        }
    }

    private val _isCrossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val isCrossfadeEnabled: StateFlow<Boolean> = _isCrossfadeEnabled.asStateFlow()

    private val _crossfadeDurationSeconds = MutableStateFlow(prefs.getInt("crossfade_duration_seconds", 5))
    val crossfadeDurationSeconds: StateFlow<Int> = _crossfadeDurationSeconds.asStateFlow()

    init {
        playerController.isCrossfadeEnabled = _isCrossfadeEnabled.value
        playerController.crossfadeDurationMs = _crossfadeDurationSeconds.value * 1000L
        playerController.urlResolver = { url -> subsonicClient.resolveUrl(url) }
        loadSearchHistoryFromPrefs()
        loadActiveServerConfig()
        observePlaybackForLyricsAndStats()

        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            android.util.Log.w("NaviromViewModel", "Failed to register NetworkCallback", e)
        }

        // Sync favorite tracks in real-time when the dynamic Favorites playlist is active
        viewModelScope.launch {
            combine(_selectedPlaylistId, favoriteIds, _rawLibrarySongs) { selectedId, favIds, rawSongs ->
                if (selectedId == "favorites_dynamic_playlist_id") {
                    rawSongs.filter { it.id in favIds }
                } else {
                    null
                }
            }.collect { matchedTracks ->
                if (matchedTracks != null) {
                    _currentPlaylistTracks.value = matchedTracks
                }
            }
        }

        // Automatic background update check on app launch
        viewModelScope.launch {
            delay(2500)
            updateManager.checkForUpdates(isManual = false)
        }
    }

    fun checkForAppUpdates(isManual: Boolean = true) {
        viewModelScope.launch {
            updateManager.checkForUpdates(isManual = isManual)
        }
    }

    fun downloadAndInstallUpdate(updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            updateManager.downloadAndInstall(updateInfo)
        }
    }

    fun dismissAppUpdate() {
        updateManager.dismissUpdate()
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        updateManager.setAutoCheckEnabled(enabled)
    }

    fun setUpdateGithubRepo(repo: String) {
        updateManager.setGithubRepo(repo)
    }

    private fun loadSearchHistoryFromPrefs() {
        val saved = prefs.getString("search_history_items", "") ?: ""
        if (saved.isNotBlank()) {
            _searchHistory.value = saved.split("||").filter { it.isNotBlank() }
        }
    }

    private fun saveSearchHistoryToPrefs(list: List<String>) {
        prefs.edit().putString("search_history_items", list.joinToString("||")).apply()
    }

    fun addSearchQueryToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed.length < 2) return
        val current = _searchHistory.value
        val updated = (listOf(trimmed) + current.filter { !it.equals(trimmed, ignoreCase = true) }).take(15)
        _searchHistory.value = updated
        saveSearchHistoryToPrefs(updated)
    }

    fun removeSearchHistoryItem(item: String) {
        val updated = _searchHistory.value.filter { !it.equals(item, ignoreCase = true) }
        _searchHistory.value = updated
        saveSearchHistoryToPrefs(updated)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistoryToPrefs(emptyList())
    }

    private var lastLoggedTrackId: String? = null

    fun recordTrackToHistory(track: NaviromTrack) {
        if (track.id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Persist to RecentSongs Room schema via repository
                recentSongsRepository.addSong(track)

                val calendar = Calendar.getInstance()
                playbackHistoryDao.insertSession(
                    PlaybackHistoryEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        coverArtUrl = track.coverArtUrl,
                        durationSeconds = track.durationSeconds,
                        listenedSeconds = 1L,
                        timestamp = System.currentTimeMillis(),
                        dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
                        hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun observePlaybackForLyricsAndStats() {
        // Instant logging & Auto-fetch lyrics on track change
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    if (track.id != lastLoggedTrackId) {
                        lastLoggedTrackId = track.id
                        recordTrackToHistory(track)
                    }
                    if (!state.isBuffering && (state.durationMs > 0 || track.durationSeconds > 0) && track.id != _currentLyrics.value.trackId) {
                        fetchLyricsForTrack(track)
                    }
                }
            }
        }

        // Auto-record playback history for listening statistics
        viewModelScope.launch {
            var lastTrackId: String? = null
            var lastPositionMs: Long = 0L
            var accumulatedPlaySeconds: Long = 0L

            while (true) {
                delay(2000) // check every 2 seconds
                val state = playerController.playbackState.value
                val track = state.currentTrack

                if (track != null && state.isPlaying) {
                    if (track.id != lastTrackId) {
                        // Flushed previous track if any
                        if (accumulatedPlaySeconds >= 3) {
                            val prevTrack = playerController.queue.value.find { it.id == lastTrackId }
                            if (prevTrack != null) {
                                statsManager.recordPlaybackSession(prevTrack, accumulatedPlaySeconds)
                            }
                        }
                        lastTrackId = track.id
                        lastPositionMs = state.currentPositionMs
                        accumulatedPlaySeconds = 0L
                    } else {
                        val delta = (state.currentPositionMs - lastPositionMs).coerceAtLeast(0)
                        if (delta in 500..4000) {
                            accumulatedPlaySeconds += (delta / 1000).coerceAtLeast(1)
                            if (accumulatedPlaySeconds >= 10) {
                                statsManager.recordPlaybackSession(track, accumulatedPlaySeconds)
                                accumulatedPlaySeconds = 0L
                            }
                        }
                        lastPositionMs = state.currentPositionMs
                    }
                }
            }
        }
    }

    fun fetchLyricsForTrack(track: NaviromTrack, forceRefresh: Boolean = false) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _currentLyrics.value = LyricsData(trackId = track.id, isLoading = true)
            // Check local file if downloaded
            val localFile = downloadManager.getLocalFileForTrack(track.id)
            val cachedFile = if (localFile.exists() && localFile.length() > 0) localFile.absolutePath else null
            val lyrics = lyricsRepository.getLyricsForTrack(track, cachedFile, forceRefresh = forceRefresh)
            _currentLyrics.value = lyrics
        }
    }

    fun refetchCurrentLyrics() {
        val track = playbackState.value.currentTrack ?: return
        fetchLyricsForTrack(track, forceRefresh = true)
    }

    fun setStatsScreenVisible(visible: Boolean) {
        _isStatsScreenVisible.value = visible
    }

    fun clearListeningStats() {
        viewModelScope.launch {
            statsManager.clearStats()
        }
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        prefs.edit().putString("app_language", language.name).apply()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        playerController.isCrossfadeEnabled = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
    }

    fun setCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 20)
        _crossfadeDurationSeconds.value = clamped
        playerController.crossfadeDurationMs = clamped * 1000L
        prefs.edit().putInt("crossfade_duration_seconds", clamped).apply()
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        _appThemeMode.value = themeMode
        prefs.edit().putString("app_theme_mode", themeMode.name).apply()
    }

    private fun buildAlternativeUrl(protocol: String, alternativeHost: String, port: String): String {
        return NaviromSubsonicClient.buildAlternativeUrl(protocol, alternativeHost, port)
    }

    private fun loadActiveServerConfig() {
        viewModelScope.launch {
            val saved = serverConfigDao.getActiveServer()
            if (saved != null && saved.serverUrl.isNotBlank()) {
                val cleanUrl = saved.serverUrl.trim()
                val parsedProto = if (cleanUrl.startsWith("https://", ignoreCase = true)) "https" else "http"
                val stripped = cleanUrl.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                val parts = stripped.split(":")
                val parsedHost = parts[0].substringBefore("/")
                val parsedPort = if (parts.size > 1) parts[1].substringBefore("/") else ""
                val savedFolderIds = saved.activeMusicFolderId?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

                _serverState.value = ServerConnectionUiState(
                    serverUrl = saved.serverUrl,
                    protocol = parsedProto,
                    host = parsedHost,
                    port = parsedPort,
                    username = saved.username,
                    password = saved.password,
                    useTokenAuth = saved.useTokenAuth,
                    selectedMusicFolderIds = savedFolderIds,
                    isConnected = false,
                    alternativeHost = saved.alternativeHost
                )
                subsonicClient.configure(
                    serverUrl = saved.serverUrl,
                    username = saved.username,
                    password = saved.password,
                    useTokenAuth = saved.useTokenAuth,
                    alternativeServerUrl = buildAlternativeUrl(parsedProto, saved.alternativeHost, parsedPort)
                )
                subsonicClient.activeMusicFolderId = if (savedFolderIds.size == 1) savedFolderIds.first() else null
                connectServer()
            }
        }
    }

    fun setTab(tab: NaviromTab) {
        _currentTab.value = tab
    }

    fun setLibrarySubTab(subTab: LibrarySubTab) {
        _librarySubTab.value = subTab
    }

    fun selectAlbum(albumId: String?) {
        _selectedAlbumId.value = albumId
        if (albumId != null) {
            loadAlbumDetails(albumId)
        } else {
            _currentAlbumTracks.value = emptyList()
        }
    }

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
        if (playlistId != null) {
            loadPlaylistDetails(playlistId)
        } else {
            _currentPlaylistTracks.value = emptyList()
        }
    }

    fun selectArtist(artistId: String?) {
        _selectedArtistId.value = artistId
        if (artistId != null) {
            loadArtistDetails(artistId)
        } else {
            _currentArtist.value = null
            _currentArtistAlbums.value = emptyList()
            _currentArtistSongs.value = emptyList()
        }
    }

    fun loadArtistDetails(artistId: String) {
        viewModelScope.launch {
            _isLoadingArtistDetails.value = true
            try {
                // 1. Get artist info & albums
                val res = subsonicClient.getArtistDetails(artistId)
                res.onSuccess { (artist, albums) ->
                    _currentArtist.value = artist
                    _currentArtistAlbums.value = albums
                    
                    // 2. Load songs from each album in parallel
                    val allSongs = mutableListOf<NaviromTrack>()
                    val deferredSongs = albums.map { album ->
                        async(Dispatchers.IO) {
                            subsonicClient.getAlbumDetails(album.id).getOrNull()?.second ?: emptyList()
                        }
                    }
                    val songsList = deferredSongs.awaitAll()
                    songsList.forEach { allSongs.addAll(it) }
                    
                    _currentArtistSongs.value = allSongs.distinctBy { it.id }.sortedBy { it.title.lowercase() }
                }.onFailure {
                    // Fallback to local/offline filtering
                    val localArtist = _artists.value.find { it.id == artistId }
                    if (localArtist != null) {
                        _currentArtist.value = localArtist
                        val localAlbums = _newestAlbums.value.filter { it.artistId == artistId || it.artist.equals(localArtist.name, ignoreCase = true) }
                        _currentArtistAlbums.value = localAlbums
                        
                        val localSongs = _rawLibrarySongs.value.filter { it.artistId == artistId || it.artist.equals(localArtist.name, ignoreCase = true) }
                        _currentArtistSongs.value = localSongs.sortedBy { it.title.lowercase() }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NaviromViewModel", "Error loading artist details", e)
            } finally {
                _isLoadingArtistDetails.value = false
            }
        }
    }

    fun setFullPlayerVisible(show: Boolean) {
        _isFullPlayerVisible.value = show
    }

    fun showFullPlayer(show: Boolean) {
        _isFullPlayerVisible.value = show
    }

    fun setQueueSheetVisible(show: Boolean) {
        _isQueueSheetVisible.value = show
    }

    fun showQueueSheet(show: Boolean) {
        _isQueueSheetVisible.value = show
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        _isOfflineOnlyMode.value = enabled
    }

    fun setServerConfig(server: ServerConfigEntity) {
        val cleanUrl = server.serverUrl.trim()
        val parsedProto = if (cleanUrl.startsWith("https://", ignoreCase = true)) "https" else "http"
        val stripped = cleanUrl.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        val parts = stripped.split(":")
        val parsedHost = parts[0].substringBefore("/")
        val parsedPort = if (parts.size > 1) parts[1].substringBefore("/") else ""

        val savedFolderIds = server.activeMusicFolderId?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

        _serverState.value = ServerConnectionUiState(
            serverUrl = server.serverUrl,
            protocol = parsedProto,
            host = parsedHost,
            port = parsedPort,
            username = server.username,
            password = server.password,
            useTokenAuth = server.useTokenAuth,
            selectedMusicFolderIds = savedFolderIds,
            isConnected = false,
            alternativeHost = server.alternativeHost
        )
    }

    fun updateServerConfig(
        protocol: String,
        host: String,
        port: String,
        username: String,
        password: String,
        useTokenAuth: Boolean,
        alternativeHost: String = ""
    ) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        val cleanAltHost = alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        val fullUrl = if (port.isNotBlank()) {
            "$protocol://$cleanHost:$port"
        } else {
            "$protocol://$cleanHost"
        }
        _serverState.update {
            it.copy(
                serverUrl = fullUrl,
                protocol = protocol,
                host = cleanHost,
                port = port,
                username = username,
                password = password,
                useTokenAuth = useTokenAuth,
                alternativeHost = cleanAltHost
            )
        }
    }

    fun selectMusicFolder(folderId: String?) {
        val newSelection = if (folderId == null) emptySet() else setOf(folderId)
        setMusicFoldersSelection(newSelection)
    }

    fun toggleMusicFolder(folderId: String) {
        val current = _serverState.value.selectedMusicFolderIds.toMutableSet()
        val allFolderIds = _serverState.value.musicFolders.map { it.id }.toSet()

        // If currently in 'All' (empty), and user taps one, we start selection with just that one
        val newSelection = if (current.isEmpty()) {
            setOf(folderId)
        } else if (current.contains(folderId)) {
            current.remove(folderId)
            if (current.isEmpty() || (allFolderIds.isNotEmpty() && current.size == allFolderIds.size)) {
                emptySet()
            } else {
                current
            }
        } else {
            current.add(folderId)
            if (allFolderIds.isNotEmpty() && current.size == allFolderIds.size) {
                emptySet() // all selected means no filter
            } else {
                current
            }
        }
        setMusicFoldersSelection(newSelection)
    }

    fun selectAllMusicFolders() {
        setMusicFoldersSelection(emptySet())
    }

    fun setMusicFoldersSelection(folderIds: Set<String>) {
        _serverState.update { it.copy(selectedMusicFolderIds = folderIds) }
        subsonicClient.activeMusicFolderId = if (folderIds.size == 1) folderIds.first() else null
        viewModelScope.launch {
            val active = serverConfigDao.getActiveServer()
            if (active != null) {
                val folderString = if (folderIds.isEmpty()) null else folderIds.joinToString(",")
                serverConfigDao.updateServer(
                    active.copy(
                        activeMusicFolderId = folderString
                    )
                )
            }
        }
        syncLibrary()
    }

    
    fun checkConnectionState() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _serverState.value
            val activeConfig = if (state.username.isBlank() || state.serverUrl.isBlank()) {
                serverConfigDao.getActiveServer()
            } else null

            val user = if (state.username.isNotBlank()) state.username else activeConfig?.username ?: ""
            val pass = if (state.password.isNotBlank()) state.password else activeConfig?.password ?: ""
            val proto = if (state.protocol.isNotBlank()) state.protocol else "http"
            val port = if (state.port.isNotBlank()) state.port else ""
            val altHost = if (state.alternativeHost.isNotBlank()) state.alternativeHost else activeConfig?.alternativeHost ?: ""
            val primaryUrl = if (state.serverUrl.isNotBlank()) state.serverUrl else activeConfig?.serverUrl ?: ""
            val tokenAuth = state.useTokenAuth

            // If credentials or server url are not provided, do not execute ping
            if (user.isBlank() || (primaryUrl.isBlank() && altHost.isBlank())) {
                return@launch
            }

            val constructedAltUrl = buildAlternativeUrl(proto, altHost, port)

            subsonicClient.configure(
                serverUrl = primaryUrl,
                username = user,
                password = pass,
                useTokenAuth = tokenAuth,
                alternativeServerUrl = constructedAltUrl
            )

            // Ping will automatically switch activeServerUrl to alternative if primary fails
            val ping = subsonicClient.ping()
            if (ping.isSuccess) {
                val activeUrl = subsonicClient.activeServerUrl
                withContext(Dispatchers.Main) {
                    _serverState.update {
                        it.copy(
                            serverUrl = if (activeUrl.isNotBlank()) activeUrl else it.serverUrl,
                            isConnected = true,
                            username = user
                        )
                    }
                    if (activeUrl.isNotBlank() && activeUrl != state.serverUrl) {
                        syncLibrary()
                    }
                }
            }
        }
    }

    fun isDeviceConnectedToNetwork(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
    }

    fun scanLocalNetwork() {
        if (networkScanJob?.isActive == true) {
            networkScanJob?.cancel()
            networkScanJob = null
            val isSq = _appLanguage.value == AppLanguage.ALBANIAN
            _serverState.update {
                it.copy(
                    isConnecting = false,
                    connectionStatusMessage = if (isSq) "Skanimi u ndalua nga përdoruesi." else "Scan stopped by user."
                )
            }
            return
        }

        val isSq = _appLanguage.value == AppLanguage.ALBANIAN
        if (!isDeviceConnectedToNetwork()) {
            _serverState.update {
                it.copy(
                    isConnecting = false,
                    connectionStatusMessage = if (isSq) 
                        "⚠️ Nuk ka lidhje me rrjet! Ju lutem lidhni telefonin me Wi-Fi ose rrjet lokal për të përdorur skanimin automatik."
                    else 
                        "⚠️ No network connection! Please connect your phone to Wi-Fi or a local network to use auto-scan."
                )
            }
            return
        }

        networkScanJob = viewModelScope.launch(Dispatchers.IO) {
            val enteredPort = _serverState.value.port.trim().toIntOrNull() ?: 4533
            val portsToScan = if (enteredPort != 4533) listOf(enteredPort, 4533) else listOf(4533)
            val primaryPort = portsToScan.first()

            val msgScanning = if (isSq) "Duke skanuar rrjetin lokal për Navidrome (Porta $primaryPort)..." else "Scanning local network for Navidrome (Port $primaryPort)..."
            _serverState.update { it.copy(isConnecting = true, connectionStatusMessage = msgScanning) }

             try {
                val candidateHosts = LinkedHashSet<String>()
                val discoveredSubnets = LinkedHashSet<String>()

                // Discover all active IPv4 interfaces and local subnets first
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces != null && interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        val addresses = ni.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                val hostAddr = addr.hostAddress ?: ""
                                if (hostAddr.isNotBlank()) {
                                    val parts = hostAddr.split(".")
                                    if (parts.size == 4) {
                                        discoveredSubnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NaviromViewModel", "Error scanning network interfaces", e)
                }

                val currentEnteredHost = _serverState.value.host.trim()
                    .removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                    .substringBefore(":").substringBefore("/")

                val currentAltHost = _serverState.value.alternativeHost.trim()
                    .removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                    .substringBefore(":").substringBefore("/")

                val enteredParts = currentEnteredHost.split(".")
                val enteredSubnet = if (enteredParts.size == 4) "${enteredParts[0]}.${enteredParts[1]}.${enteredParts[2]}" else ""
                val enteredNotInLocal = enteredSubnet.isNotBlank() && discoveredSubnets.isNotEmpty() && !discoveredSubnets.contains(enteredSubnet)

                // If primary host is clearly not in the same network, jump to / prioritize alternative IP address
                if (enteredNotInLocal && currentAltHost.isNotBlank()) {
                    android.util.Log.i("NaviromViewModel", "Primary host $currentEnteredHost is not in local network subnets $discoveredSubnets. Jumping to alternative host $currentAltHost.")
                    candidateHosts.add(currentAltHost)
                    val altParts = currentAltHost.split(".")
                    if (altParts.size == 4) discoveredSubnets.add("${altParts[0]}.${altParts[1]}.${altParts[2]}")
                    if (currentEnteredHost.isNotBlank()) candidateHosts.add(currentEnteredHost)
                } else {
                    if (currentEnteredHost.isNotBlank()) {
                        candidateHosts.add(currentEnteredHost)
                        if (enteredSubnet.isNotBlank()) discoveredSubnets.add(enteredSubnet)
                    }
                    if (currentAltHost.isNotBlank()) {
                        candidateHosts.add(currentAltHost)
                        val altParts = currentAltHost.split(".")
                        if (altParts.size == 4) discoveredSubnets.add("${altParts[0]}.${altParts[1]}.${altParts[2]}")
                    }
                }

                // Add discovered local interface IPs and gateway/common addresses
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces != null && interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        val addresses = ni.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                val hostAddr = addr.hostAddress ?: ""
                                if (hostAddr.isNotBlank()) {
                                    candidateHosts.add(hostAddr)
                                    val parts = hostAddr.split(".")
                                    if (parts.size == 4) {
                                        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                                        discoveredSubnets.add(prefix)
                                        candidateHosts.add("$prefix.1")
                                        candidateHosts.add("$prefix.2")
                                        candidateHosts.add("$prefix.100")
                                        candidateHosts.add("$prefix.200")
                                        candidateHosts.add("$prefix.254")
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. Fallback common local subnets
                val fallbackSubnets = listOf(
                    "192.168.1", "192.168.0", "192.168.8", "192.168.2", "192.168.178",
                    "192.168.86", "192.168.43", "192.168.10", "10.0.0", "10.0.1", "172.20.10"
                )
                discoveredSubnets.addAll(fallbackSubnets)

                for (subnet in discoveredSubnets) {
                    candidateHosts.add("$subnet.1")
                    candidateHosts.add("$subnet.2")
                    candidateHosts.add("$subnet.100")
                    candidateHosts.add("$subnet.254")
                }

                var foundHost: String? = null
                var foundPort: Int = primaryPort

                // PHASE 1: Priority direct probe for candidate hosts on configured ports
                for (targetPort in portsToScan) {
                    if (foundHost != null) break
                    for (host in candidateHosts) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(host, targetPort), 350)
                                foundHost = host
                                foundPort = targetPort
                                break
                            }
                        } catch (_: Exception) { }
                    }
                }

                // PHASE 2: Parallel high-speed sweep on port 4533 (and configured port) across discovered subnets
                if (foundHost == null) {
                    for (subnetPrefix in discoveredSubnets) {
                        if (foundHost != null) break
                        for (targetPort in portsToScan) {
                            if (foundHost != null) break
                            for (chunk in (1..254).chunked(32)) {
                                if (foundHost != null) break
                                coroutineScope {
                                    val jobs = chunk.map { lastOctet ->
                                        async(Dispatchers.IO) {
                                            val targetIp = "$subnetPrefix.$lastOctet"
                                            try {
                                                Socket().use { socket ->
                                                    socket.connect(InetSocketAddress(targetIp, targetPort), 350)
                                                    return@async targetIp
                                                }
                                            } catch (_: Exception) { }
                                            null
                                        }
                                    }
                                    val hit = jobs.awaitAll().firstOrNull { it != null }
                                    if (hit != null) {
                                        foundHost = hit
                                        foundPort = targetPort
                                    }
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (foundHost != null) {
                        _serverState.update {
                            it.copy(
                                host = foundHost!!,
                                port = foundPort.toString(),
                                isConnecting = false,
                                connectionStatusMessage = if (isSq) "U gjet serveri Navidrome në $foundHost:$foundPort!" else "Found Navidrome server at $foundHost:$foundPort!"
                            )
                        }
                        connectServer()
                    } else {
                        _serverState.update {
                            it.copy(
                                isConnecting = false,
                                connectionStatusMessage = if (isSq) "Nuk u gjet asnjë server Navidrome në portën $primaryPort." else "No Navidrome server found on port $primaryPort."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _serverState.update {
                        it.copy(
                            isConnecting = false,
                            connectionStatusMessage = if (isSq) "Skanimi dështoi: ${e.message}" else "Scan failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    fun connectServer() {
        val state = _serverState.value
        val rawHost = state.host.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        var cleanHost = rawHost
        var cleanPort = state.port.trim()

        if (cleanHost.contains(":")) {
            val parts = cleanHost.split(":")
            cleanHost = parts[0].substringBefore("/")
            if (parts.size > 1 && parts[1].isNotBlank()) {
                cleanPort = parts[1].substringBefore("/").trim()
            }
        }

        if (cleanPort.isBlank()) {
            cleanPort = "4533"
        }

        val fullUrl = "${state.protocol}://$cleanHost:$cleanPort"
        val isSq = _appLanguage.value == AppLanguage.ALBANIAN

        if (cleanHost.isBlank()) {
            _serverState.update {
                it.copy(
                    connectionStatusMessage = if (isSq) "Ju lutemi vendosni IP ose adresën e serverit." else "Please enter a valid server host / IP."
                )
            }
            return
        }

        _serverState.update {
            it.copy(
                host = cleanHost,
                port = cleanPort,
                isConnecting = true,
                connectionStatusMessage = if (isSq) "Duke u lidhur me serverin $fullUrl..." else "Connecting to server $fullUrl...",
                serverUrl = fullUrl
            )
        }

        val altUrl = buildAlternativeUrl(state.protocol, state.alternativeHost, cleanPort)
        subsonicClient.configure(
            serverUrl = fullUrl,
            username = state.username,
            password = state.password,
            useTokenAuth = state.useTokenAuth,
            alternativeServerUrl = altUrl
        )
        subsonicClient.activeMusicFolderId = state.selectedMusicFolderId

         viewModelScope.launch(Dispatchers.IO) {
            val localSubnets = LinkedHashSet<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val ni = interfaces.nextElement()
                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val hostAddr = addr.hostAddress ?: ""
                            val parts = hostAddr.split(".")
                            if (parts.size == 4) {
                                localSubnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val enteredParts = cleanHost.split(".")
            val enteredSubnet = if (enteredParts.size == 4) "${enteredParts[0]}.${enteredParts[1]}.${enteredParts[2]}" else ""
            val primaryNotInLocal = enteredSubnet.isNotBlank() && localSubnets.isNotEmpty() && !localSubnets.contains(enteredSubnet)

            var pingResult = Result.failure<String>(Exception("Not connected"))
            var effectiveHost = cleanHost
            var effectivePort = cleanPort

            // If primary host is clearly not in local network and alternative host is configured, try alternative host FIRST (jump to alternative IP)
            if (primaryNotInLocal && state.alternativeHost.isNotBlank()) {
                val testAltUrl = buildAlternativeUrl(state.protocol, state.alternativeHost, cleanPort)
                subsonicClient.configure(
                    serverUrl = testAltUrl,
                    username = state.username,
                    password = state.password,
                    useTokenAuth = state.useTokenAuth,
                    alternativeServerUrl = fullUrl
                )
                val testPing = subsonicClient.ping()
                if (testPing.isSuccess) {
                    pingResult = testPing
                    subsonicClient.activeServerUrl = testAltUrl
                    effectiveHost = state.alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                }
            }

            // If alternative ping wasn't tried or failed, try primary URL
            if (pingResult.isFailure) {
                subsonicClient.configure(
                    serverUrl = fullUrl,
                    username = state.username,
                    password = state.password,
                    useTokenAuth = state.useTokenAuth,
                    alternativeServerUrl = altUrl
                )
                pingResult = subsonicClient.ping()
                effectiveHost = cleanHost
            }

            // Try alternative URL ping if primary fails and we didn't try it already
            if (pingResult.isFailure && !primaryNotInLocal && state.alternativeHost.isNotBlank()) {
                val testAltUrl = buildAlternativeUrl(state.protocol, state.alternativeHost, cleanPort)
                subsonicClient.configure(
                    serverUrl = testAltUrl,
                    username = state.username,
                    password = state.password,
                    useTokenAuth = state.useTokenAuth,
                    alternativeServerUrl = fullUrl
                )
                val testPing = subsonicClient.ping()
                if (testPing.isSuccess) {
                    pingResult = testPing
                    subsonicClient.activeServerUrl = testAltUrl
                    effectiveHost = state.alternativeHost.trim().removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
                } else {
                    subsonicClient.configure(
                        serverUrl = fullUrl,
                        username = state.username,
                        password = state.password,
                        useTokenAuth = state.useTokenAuth,
                        alternativeServerUrl = testAltUrl
                    )
                }
            }

            // If initial ping and alternative ping failed, try fallback on default port 4533 (if different)
            if (pingResult.isFailure && cleanPort != "4533") {
                withContext(Dispatchers.Main) {
                    _serverState.update {
                        it.copy(connectionStatusMessage = if (isSq) "Duke kërkuar serverin në portën 4533..." else "Trying default port 4533...")
                    }
                }

                val testUrl = "${state.protocol}://$cleanHost:4533"
                subsonicClient.configure(
                    serverUrl = testUrl,
                    username = state.username,
                    password = state.password,
                    useTokenAuth = state.useTokenAuth,
                    alternativeServerUrl = altUrl
                )
                val testPing = subsonicClient.ping()
                if (testPing.isSuccess) {
                    pingResult = testPing
                    effectiveHost = cleanHost
                    effectivePort = "4533"
                }
            }

            val finalUrl = "${state.protocol}://$effectiveHost:$effectivePort"
            withContext(Dispatchers.Main) {
                pingResult.onSuccess { version ->
                    _serverState.update {
                        it.copy(
                            host = effectiveHost,
                            port = effectivePort,
                            serverUrl = finalUrl,
                            isConnecting = false,
                            isConnected = true,
                            serverVersion = version,
                            connectionStatusMessage = if (isSq) "U lidh me sukses! (Versioni: $version)" else "Connected successfully! (Version: $version)"
                        )
                    }

                    // Persist config to database
                    viewModelScope.launch {
                        serverConfigDao.deactivateAllServers()
                        serverConfigDao.insertServer(
                            ServerConfigEntity(
                                name = "Server ${System.currentTimeMillis() % 10000}",
                                serverUrl = finalUrl,
                                username = state.username,
                                password = state.password,
                                useTokenAuth = state.useTokenAuth,
                                isConnected = true,
                                activeMusicFolderId = if (state.selectedMusicFolderIds.isEmpty()) null else state.selectedMusicFolderIds.joinToString(","),
                                alternativeHost = state.alternativeHost
                            )
                        )
                    }

                    // Load music folders
                    viewModelScope.launch {
                        val foldersResult = subsonicClient.getMusicFolders()
                        foldersResult.onSuccess { folders ->
                            _serverState.update { it.copy(musicFolders = folders) }
                        }
                        syncLibrary()
                    }
                }.onFailure { err ->
                    _serverState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            connectionStatusMessage = if (isSq) "Lidhja dështoi: ${err.message ?: "Gabim i panjohur"}" else "Connection failed: ${err.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        }
    }

    fun syncLibrary() {
        viewModelScope.launch {
            // Fetch Music Folders (Server Libraries)
            val foldersRes = subsonicClient.getMusicFolders()
            val folders = foldersRes.getOrDefault(emptyList())
            if (folders.isNotEmpty()) {
                _serverState.update { it.copy(musicFolders = folders) }
            }

            val selectedIds = _serverState.value.selectedMusicFolderIds

            if (selectedIds.isEmpty() || (folders.isNotEmpty() && selectedIds.size >= folders.size)) {
                // Unified catalog (all folders)
                subsonicClient.activeMusicFolderId = null

                // Fetch Albums
                val albumsRes = subsonicClient.getAlbums(type = "newest", size = 150)
                albumsRes.onSuccess { list ->
                    _albums.value = list
                    _newestAlbums.value = list.take(15)
                }

                // Fetch Artists
                val artistsRes = subsonicClient.getArtists()
                artistsRes.onSuccess { list ->
                    _artists.value = list
                }

                // Fetch Playlists
                val playlistsRes = subsonicClient.getPlaylists()
                playlistsRes.onSuccess { list ->
                    _playlists.value = list
                }

                // Fetch Frequent / Most Played Albums
                val frequentRes = subsonicClient.getAlbums(type = "frequent", size = 20)
                frequentRes.onSuccess { list ->
                    _mostPlayedAlbums.value = if (list.isNotEmpty()) list else _albums.value.take(15)
                }

                // Fetch Random Albums for discovery
                val randomRes = subsonicClient.getAlbums(type = "random", size = 20)
                randomRes.onSuccess { list ->
                    _randomAlbums.value = if (list.isNotEmpty()) list else _albums.value.shuffled().take(15)
                }

                // Fetch Quick Mix & Newly Added Tracks
                val mixRes = subsonicClient.getRandomTracks(size = 30)
                mixRes.onSuccess { list ->
                    _quickMixTracks.value = list
                    _newestTracks.value = list.take(20)
                }

                // Fetch Library Songs for All Songs list
                val songsRes = subsonicClient.getLibrarySongs(size = 300)
                songsRes.onSuccess { list ->
                    _rawLibrarySongs.value = list
                }
            } else if (selectedIds.size == 1) {
                // Single folder selected
                val singleId = selectedIds.first()
                subsonicClient.activeMusicFolderId = singleId

                val albumsRes = subsonicClient.getAlbums(type = "newest", size = 150)
                albumsRes.onSuccess { list ->
                    _albums.value = list
                    _newestAlbums.value = list.take(15)
                }

                val artistsRes = subsonicClient.getArtists()
                artistsRes.onSuccess { list ->
                    _artists.value = list
                }

                val playlistsRes = subsonicClient.getPlaylists()
                playlistsRes.onSuccess { list ->
                    _playlists.value = list
                }

                // Fetch Frequent / Most Played Albums
                val frequentRes = subsonicClient.getAlbums(type = "frequent", size = 20)
                frequentRes.onSuccess { list ->
                    _mostPlayedAlbums.value = if (list.isNotEmpty()) list else _albums.value.take(15)
                }

                // Fetch Random Albums for discovery
                val randomRes = subsonicClient.getAlbums(type = "random", size = 20)
                randomRes.onSuccess { list ->
                    _randomAlbums.value = if (list.isNotEmpty()) list else _albums.value.shuffled().take(15)
                }

                val mixRes = subsonicClient.getRandomTracks(size = 30)
                mixRes.onSuccess { list ->
                    _quickMixTracks.value = list
                    _newestTracks.value = list.take(20)
                }

                val songsRes = subsonicClient.getLibrarySongs(size = 300)
                songsRes.onSuccess { list ->
                    _rawLibrarySongs.value = list
                }
            } else {
                // Multi-library aggregate selection!
                val mergedAlbums = mutableMapOf<String, NaviromAlbum>()
                val mergedArtists = mutableMapOf<String, NaviromArtist>()
                val mergedTracks = mutableMapOf<String, NaviromTrack>()
                val mergedMix = mutableListOf<NaviromTrack>()

                for (folderId in selectedIds) {
                    subsonicClient.activeMusicFolderId = folderId
                    subsonicClient.getAlbums(type = "newest", size = 100).onSuccess { list ->
                        list.forEach { mergedAlbums[it.id] = it }
                    }
                    subsonicClient.getArtists().onSuccess { list ->
                        list.forEach { mergedArtists[it.id] = it }
                    }
                    subsonicClient.getLibrarySongs(size = 150).onSuccess { list ->
                        list.forEach { mergedTracks[it.id] = it }
                    }
                    subsonicClient.getRandomTracks(size = 20).onSuccess { list ->
                        mergedMix.addAll(list)
                    }
                }

                // Restore activeMusicFolderId to null for global actions
                subsonicClient.activeMusicFolderId = null

                val albumList = mergedAlbums.values.toList()
                _albums.value = albumList
                _newestAlbums.value = albumList.take(15)
                _mostPlayedAlbums.value = albumList.take(15)
                _randomAlbums.value = albumList.shuffled().take(15)
                _artists.value = mergedArtists.values.sortedBy { it.name.lowercase() }
                _rawLibrarySongs.value = mergedTracks.values.toList()
                _quickMixTracks.value = mergedMix.distinctBy { it.id }.take(30)
                _newestTracks.value = mergedMix.distinctBy { it.id }.take(20)

                subsonicClient.getPlaylists().onSuccess { list ->
                    _playlists.value = list
                }
            }
        }
    }

    fun refreshRandomAlbums() {
        viewModelScope.launch {
            val randomRes = subsonicClient.getAlbums(type = "random", size = 20)
            randomRes.onSuccess { list ->
                if (list.isNotEmpty()) {
                    _randomAlbums.value = list
                } else if (_albums.value.isNotEmpty()) {
                    _randomAlbums.value = _albums.value.shuffled().take(15)
                }
            }.onFailure {
                if (_albums.value.isNotEmpty()) {
                    _randomAlbums.value = _albums.value.shuffled().take(15)
                }
            }
        }
    }

    fun setSongSortOrder(order: SongSortOrder) {
        _songSortOrder.value = order
    }

    fun dismissShakeNotification() {
        _shakeNotificationMessage.value = null
    }

    fun playRandomTrackFromCurrentLibrary() {
        viewModelScope.launch {
            val state = _serverState.value
            val selectedIds = state.selectedMusicFolderIds
            val folderName = when {
                selectedIds.isEmpty() || (state.musicFolders.isNotEmpty() && selectedIds.size >= state.musicFolders.size) -> {
                    if (_appLanguage.value == AppLanguage.ALBANIAN) "Të gjitha bibliotekat" else "All Libraries"
                }
                selectedIds.size == 1 -> {
                    state.musicFolders.find { it.id == selectedIds.first() }?.name ?: "Library"
                }
                else -> {
                    val names = state.musicFolders.filter { it.id in selectedIds }.map { it.name }
                    if (names.isNotEmpty()) names.joinToString(", ") else "${selectedIds.size} Libraries"
                }
            }

            // Prioritize candidate tracks in current library
            var candidateTracks = _rawLibrarySongs.value
            if (candidateTracks.isEmpty()) {
                candidateTracks = _quickMixTracks.value
            }
            if (candidateTracks.isEmpty()) {
                val randomFetch = subsonicClient.getRandomTracks(size = 20)
                randomFetch.onSuccess {
                    candidateTracks = it
                    _rawLibrarySongs.value = it
                }
            }

            if (candidateTracks.isNotEmpty()) {
                val randomTrack = candidateTracks.random()
                playTrack(randomTrack, candidateTracks)

                val isSq = _appLanguage.value == AppLanguage.ALBANIAN
                val msg = if (isSq) {
                    "🎲 Lëkundje 2s! Po luhet '${randomTrack.title}' nga $folderName"
                } else {
                    "🎲 Shake 2s! Playing '${randomTrack.title}' from $folderName"
                }
                _shakeNotificationMessage.value = msg

                // Clear notification after 4 seconds
                delay(4000)
                if (_shakeNotificationMessage.value == msg) {
                    _shakeNotificationMessage.value = null
                }
            }
        }
    }

    fun loadAlbumDetails(albumId: String) {
        viewModelScope.launch {
            val res = subsonicClient.getAlbumDetails(albumId)
            res.onSuccess { (_, tracks) ->
                _currentAlbumTracks.value = tracks
            }
        }
    }

    fun loadPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            if (playlistId == "favorites_dynamic_playlist_id") {
                val favIds = favoriteIds.value
                val matchedTracks = _rawLibrarySongs.value.filter { it.id in favIds }
                _currentPlaylistTracks.value = matchedTracks
            } else {
                val res = subsonicClient.getPlaylistDetails(playlistId)
                res.onSuccess { (_, tracks) ->
                    _currentPlaylistTracks.value = tracks
                }
            }
        }
    }

    private fun performLocalSearch(query: String): Triple<List<NaviromArtist>, List<NaviromAlbum>, List<NaviromTrack>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return Triple(emptyList(), emptyList(), emptyList())

        val tokens = cleanQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 1. Artists search
        val matchedArtists = _artists.value.filter { artist ->
            val nameLower = artist.name.lowercase()
            tokens.all { nameLower.contains(it) }
        }

        // 2. Albums search
        val matchedAlbums = _albums.value.filter { album ->
            val textToSearch = "${album.name} ${album.artist} ${album.genre} ${album.year ?: ""}".lowercase()
            tokens.all { textToSearch.contains(it) }
        }

        // 3. Tracks search from all loaded tracks + cached tracks
        val allLocalTracks = mutableMapOf<String, NaviromTrack>()

        // Cached offline tracks
        cachedTracks.value.forEach { entity ->
            allLocalTracks[entity.id] = NaviromTrack(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                artistId = entity.artistId,
                album = entity.album,
                albumId = entity.albumId,
                durationSeconds = entity.durationSeconds,
                coverArtId = "",
                coverArtUrl = entity.coverArtUrl,
                streamUrl = entity.localFilePath.ifBlank { subsonicClient.getStreamUrl(entity.id) },
                year = entity.year,
                genre = entity.genre,
                suffix = entity.format,
                isFavorite = favoriteIds.value.contains(entity.id)
            )
        }

        // Memory loaded tracks
        (_newestTracks.value + _currentAlbumTracks.value + _currentPlaylistTracks.value + _quickMixTracks.value).forEach { track ->
            if (!allLocalTracks.containsKey(track.id)) {
                allLocalTracks[track.id] = track
            }
        }

        val matchedTracks = allLocalTracks.values.filter { track ->
            val textToSearch = "${track.title} ${track.artist} ${track.album} ${track.genre} ${track.year ?: ""}".lowercase()
            tokens.all { textToSearch.contains(it) }
        }.toList()

        return Triple(matchedArtists, matchedAlbums, matchedTracks)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = Triple(emptyList(), emptyList(), emptyList())
            _isSearching.value = false
            return
        }

        // Instant sub-millisecond local search
        val localResults = performLocalSearch(query)
        _searchResults.value = localResults

        if (_isOfflineOnlyMode.value || !_serverState.value.isConnected) {
            _isSearching.value = false
            if (query.trim().length >= 2 && (localResults.first.isNotEmpty() || localResults.second.isNotEmpty() || localResults.third.isNotEmpty())) {
                addSearchQueryToHistory(query.trim())
            }
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(200)

            val result = subsonicClient.search(query)
            result.onSuccess { (serverArtists, serverAlbums, serverTracks) ->
                val mergedArtists = (localResults.first + serverArtists).distinctBy { if (it.id.isNotBlank()) it.id else it.name.lowercase() }
                val mergedAlbums = (localResults.second + serverAlbums).distinctBy { if (it.id.isNotBlank()) it.id else it.name.lowercase() }
                val mergedTracks = (localResults.third + serverTracks).distinctBy { it.id }

                _searchResults.value = Triple(mergedArtists, mergedAlbums, mergedTracks)

                if (query.trim().length >= 2) {
                    addSearchQueryToHistory(query.trim())
                }
            }.onFailure {
                if (query.trim().length >= 2 && (localResults.first.isNotEmpty() || localResults.second.isNotEmpty() || localResults.third.isNotEmpty())) {
                    addSearchQueryToHistory(query.trim())
                }
            }
            _isSearching.value = false
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = subsonicClient.createPlaylist(name)
            result.onSuccess {
                syncLibrary()
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            subsonicClient.deletePlaylist(playlistId)
            syncLibrary()
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            val isFav = favoriteDao.isFavorite(trackId)
            if (isFav) {
                favoriteDao.removeFavorite(trackId)
                subsonicClient.starTrack(trackId, false)
            } else {
                favoriteDao.addFavorite(FavoriteTrackEntity(trackId = trackId))
                subsonicClient.starTrack(trackId, true)
            }
        }
    }

    fun playTrack(track: NaviromTrack, queue: List<NaviromTrack> = listOf(track)) {
        playerController.playTrack(track, queue)
        recordTrackToHistory(track)
    }

    fun playAll(tracks: List<NaviromTrack>, startIndex: Int = 0) {
        playerController.playTrackList(tracks, startIndex)
        tracks.getOrNull(startIndex)?.let { recordTrackToHistory(it) }
    }

    fun shuffleAll(tracks: List<NaviromTrack>) {
        val shuffled = tracks.shuffled()
        playerController.playTrackList(shuffled, 0)
        shuffled.firstOrNull()?.let { recordTrackToHistory(it) }
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun setRepeatMode(mode: RepeatMode) {
        playerController.setRepeatMode(mode)
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int?) {
        playerController.setSleepTimer(minutes)
    }

    fun playNext(track: NaviromTrack) {
        playerController.playNext(track)
    }

    fun addToQueue(track: NaviromTrack) {
        playerController.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerController.removeFromQueue(index)
    }

    fun clearQueue() {
        playerController.clearQueue()
    }

    fun downloadTrack(track: NaviromTrack) {
        downloadManager.downloadTrack(track)
    }

    fun downloadTracks(tracks: List<NaviromTrack>) {
        downloadManager.downloadTracks(tracks)
    }

    fun deleteCachedTrack(trackId: String) {
        viewModelScope.launch {
            downloadManager.deleteCachedTrack(trackId)
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            downloadManager.clearAllCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        playerController.release()
    }
}
