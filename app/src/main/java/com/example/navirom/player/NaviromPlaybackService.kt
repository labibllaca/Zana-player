package com.example.navirom.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidPlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log
import android.view.KeyEvent
import com.example.MainActivity
import com.example.R
import com.example.navirom.data.api.HttpClientProvider
import com.example.navirom.data.api.NaviromSubsonicClient
import com.example.navirom.data.cache.OfflineDownloadManager
import com.example.navirom.data.local.CachedTrackEntity
import com.example.navirom.data.local.NaviromDatabase
import com.example.navirom.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.Request
import java.lang.ref.WeakReference

class NaviromPlaybackService : MediaBrowserService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastCoverUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var isForegroundService = false
    private val httpClient = HttpClientProvider.client
    private val subsonicClient = NaviromSubsonicClient()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "navirom_playback_channel"

        const val ACTION_PLAY = "com.example.navirom.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.navirom.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.example.navirom.ACTION_TOGGLE_PLAY"
        const val ACTION_PREVIOUS = "com.example.navirom.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.navirom.ACTION_NEXT"
        const val ACTION_SEEK = "com.example.navirom.ACTION_SEEK"
        const val ACTION_STOP = "com.example.navirom.ACTION_STOP"
        const val ACTION_CLOSE = "com.example.navirom.ACTION_CLOSE"
        const val ACTION_UPDATE_STATE = "com.example.navirom.ACTION_UPDATE_STATE"
        const val EXTRA_SEEK_POSITION = "extra_seek_position"

        // MediaBrowser IDs for Android Auto / Android Automotive
        const val MEDIA_ROOT_ID = "navirom_root"
        const val MEDIA_ID_QUICK_MIX = "media_quick_mix"
        const val MEDIA_ID_SONGS = "media_songs"
        const val MEDIA_ID_ALBUMS = "media_albums"
        const val MEDIA_ID_ARTISTS = "media_artists"
        const val MEDIA_ID_PLAYLISTS = "media_playlists"
        const val MEDIA_ID_RECENT = "media_recent"
        const val MEDIA_ID_OFFLINE = "media_offline"
        const val MEDIA_ID_SHUFFLE_ALL = "media_shuffle_all"

        var activePlayerController: WeakReference<AudioPlayerController>? = null

        fun updateService(context: Context, state: PlaybackState) {
            try {
                val intent = Intent(context, NaviromPlaybackService::class.java).apply {
                    action = ACTION_UPDATE_STATE
                }
                if (state.isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            context.startForegroundService(intent)
                        } catch (e: Exception) {
                            Log.w("PlaybackService", "startForegroundService restricted: ${e.message}")
                            context.startService(intent)
                        }
                    } else {
                        context.startService(intent)
                    }
                } else if (state.currentTrack != null) {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("PlaybackService", "Failed to start/update service", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, NaviromPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("PlaybackService", "Failed to stop service", e)
            }
        }
    }

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                try {
                    val controller = activePlayerController?.get()
                    if (controller != null && controller.playbackState.value.isShuffle) {
                        controller.toggleShuffle()
                        Log.i("NaviromPlaybackService", "Screen turned off: automatically disabled shuffle to prevent accidental pocket playback/random songs.")
                    }
                } catch (e: Exception) {
                    Log.w("NaviromPlaybackService", "Error handling screen off shuffle disable", e)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        sessionToken = mediaSession?.sessionToken
        try {
            val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenOffReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w("NaviromPlaybackService", "Failed to register screen off receiver", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    private fun getOrInitPlayerController(): AudioPlayerController {
        activePlayerController?.get()?.let { return it }
        val db = NaviromDatabase.getDatabase(this)
        val downloadManager = OfflineDownloadManager(this, db.cachedTrackDao())
        val controller = AudioPlayerController(
            context = this,
            downloadManager = downloadManager,
            cachedTrackDao = db.cachedTrackDao(),
            playbackQueueDao = db.playbackQueueDao()
        )
        controller.urlResolver = { url -> subsonicClient.resolveUrl(url) }
        activePlayerController = WeakReference(controller)
        return controller
    }

    private suspend fun ensureSubsonicClientConfigured() {
        if (subsonicClient.serverUrl.isNotBlank() && subsonicClient.username.isNotBlank()) return
        val db = NaviromDatabase.getDatabase(this)
        val activeConfig = db.serverConfigDao().getActiveServer()
        if (activeConfig != null) {
            val cleanUrl = activeConfig.serverUrl.trim()
            val parsedProto = if (cleanUrl.startsWith("https://", ignoreCase = true)) "https" else "http"
            val stripped = cleanUrl.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
            val parts = stripped.split(":")
            val parsedPort = if (parts.size > 1) parts[1].substringBefore("/") else ""

            val altUrl = NaviromSubsonicClient.buildAlternativeUrl(
                defaultProtocol = parsedProto,
                alternativeHost = activeConfig.alternativeHost,
                defaultPort = parsedPort
            )

            subsonicClient.configure(
                serverUrl = activeConfig.serverUrl,
                username = activeConfig.username,
                password = activeConfig.password,
                useTokenAuth = activeConfig.useTokenAuth,
                alternativeServerUrl = altUrl
            )
            subsonicClient.activeMusicFolderId = activeConfig.activeMusicFolderId
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        val extras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
            putBoolean("android.service.media.extra.OFFLINE", true)
        }
        return BrowserRoot(MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            ensureSubsonicClientConfigured()
            val items = loadMediaItemsForParent(parentId)
            withContext(Dispatchers.Main) {
                result.sendResult(items)
            }
        }
    }

    private suspend fun loadMediaItemsForParent(parentId: String): MutableList<MediaBrowser.MediaItem> {
        val items = mutableListOf<MediaBrowser.MediaItem>()
        val db = NaviromDatabase.getDatabase(this)

        when (parentId) {
            MEDIA_ROOT_ID -> {
                // Android Auto Top-Level Category Hub
                items.add(createBrowsableCategory(MEDIA_ID_QUICK_MIX, "⚡ Quick Mix", "Instant smart music deck"))
                items.add(createPlayableAction(MEDIA_ID_SHUFFLE_ALL, "🎲 Shuffle All", "Play random tracks from library"))
                items.add(createBrowsableCategory(MEDIA_ID_SONGS, "🎵 All Songs", "Browse entire song catalog"))
                items.add(createBrowsableCategory(MEDIA_ID_ALBUMS, "💿 Albums", "Browse albums collection"))
                items.add(createBrowsableCategory(MEDIA_ID_ARTISTS, "🎤 Artists", "Browse artists catalog"))
                items.add(createBrowsableCategory(MEDIA_ID_PLAYLISTS, "📑 Playlists", "Custom and server playlists"))
                items.add(createBrowsableCategory(MEDIA_ID_RECENT, "🕒 Recently Added", "Latest music additions"))
                items.add(createBrowsableCategory(MEDIA_ID_OFFLINE, "💾 Offline Downloads", "Play cached music"))
            }
            MEDIA_ID_QUICK_MIX -> {
                val mixResult = subsonicClient.getRandomTracks(size = 35)
                mixResult.onSuccess { tracks ->
                    tracks.forEach { items.add(trackToMediaItem(it)) }
                }.onFailure {
                    val cached = db.cachedTrackDao().getAllCachedTracks().firstOrNull() ?: emptyList()
                    cached.forEach { items.add(cachedEntityToMediaItem(it)) }
                }
            }
            MEDIA_ID_SONGS -> {
                val songsResult = subsonicClient.getLibrarySongs(size = 80)
                songsResult.onSuccess { tracks ->
                    tracks.forEach { items.add(trackToMediaItem(it)) }
                }.onFailure {
                    val cached = db.cachedTrackDao().getAllCachedTracks().firstOrNull() ?: emptyList()
                    cached.forEach { items.add(cachedEntityToMediaItem(it)) }
                }
            }
            MEDIA_ID_ALBUMS -> {
                val albumsResult = subsonicClient.getAlbums(type = "newest", size = 60)
                albumsResult.onSuccess { albums ->
                    albums.forEach { items.add(albumToMediaItem(it)) }
                }
            }
            MEDIA_ID_ARTISTS -> {
                val artistsResult = subsonicClient.getArtists()
                artistsResult.onSuccess { artists ->
                    artists.forEach { items.add(artistToMediaItem(it)) }
                }
            }
            MEDIA_ID_PLAYLISTS -> {
                // Load local playlists from Room
                val localPlaylists = db.playlistDao().getAllLocalPlaylists().firstOrNull() ?: emptyList()
                localPlaylists.forEach { lp ->
                    val desc = MediaDescription.Builder()
                        .setMediaId("playlist_${lp.id}")
                        .setTitle(lp.name)
                        .setSubtitle("Local Playlist (${lp.songCount} songs)")
                        .setIconUri(if (lp.coverArtUrl.isNotBlank()) Uri.parse(lp.coverArtUrl) else null)
                        .build()
                    items.add(MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE))
                }
                // Load server playlists from Subsonic
                val serverPlaylists = subsonicClient.getPlaylists()
                serverPlaylists.onSuccess { plList ->
                    plList.forEach { items.add(playlistToMediaItem(it)) }
                }
            }
            MEDIA_ID_RECENT -> {
                val recentRes = subsonicClient.getAlbums(type = "newest", size = 30)
                recentRes.onSuccess { albums ->
                    albums.forEach { items.add(albumToMediaItem(it)) }
                }
            }
            MEDIA_ID_OFFLINE -> {
                val cached = db.cachedTrackDao().getAllCachedTracks().firstOrNull() ?: emptyList()
                cached.forEach { items.add(cachedEntityToMediaItem(it)) }
            }
            else -> {
                if (parentId.startsWith("album_")) {
                    val albumId = parentId.removePrefix("album_")
                    val albumRes = subsonicClient.getAlbumDetails(albumId)
                    albumRes.onSuccess { pair ->
                        pair.second.forEach { items.add(trackToMediaItem(it)) }
                    }
                } else if (parentId.startsWith("artist_")) {
                    val artistId = parentId.removePrefix("artist_")
                    val albumsRes = subsonicClient.getAlbums(type = "byArtist", size = 50)
                    albumsRes.onSuccess { list ->
                        val artistAlbums = list.filter { it.artistId == artistId }
                        artistAlbums.forEach { items.add(albumToMediaItem(it)) }
                    }
                } else if (parentId.startsWith("playlist_")) {
                    val plId = parentId.removePrefix("playlist_")
                    val localItems = db.playlistDao().getPlaylistItems(plId).firstOrNull() ?: emptyList()
                    if (localItems.isNotEmpty()) {
                        localItems.forEach { pi ->
                            val desc = MediaDescription.Builder()
                                .setMediaId("track_${pi.trackId}")
                                .setTitle(pi.title)
                                .setSubtitle(pi.artist)
                                .setDescription(pi.album)
                                .setIconUri(if (pi.coverArtUrl.isNotBlank()) Uri.parse(pi.coverArtUrl) else null)
                                .build()
                            items.add(MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE))
                        }
                    } else {
                        val serverPlRes = subsonicClient.getPlaylistDetails(plId)
                        serverPlRes.onSuccess { pair ->
                            pair.second.forEach { items.add(trackToMediaItem(it)) }
                        }
                    }
                }
            }
        }

        return items
    }

    private fun createBrowsableCategory(id: String, title: String, subtitle: String): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE)
    }

    private fun createPlayableAction(id: String, title: String, subtitle: String): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    private fun trackToMediaItem(track: NaviromTrack): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId("track_${track.id}")
            .setTitle(track.title)
            .setSubtitle(track.artist)
            .setDescription(track.album)
            .setIconUri(if (track.coverArtUrl.isNotBlank()) Uri.parse(track.coverArtUrl) else null)
            .setExtras(Bundle().apply {
                putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationSeconds * 1000L)
            })
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    private fun cachedEntityToMediaItem(entity: CachedTrackEntity): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId("track_${entity.id}")
            .setTitle(entity.title)
            .setSubtitle(entity.artist)
            .setDescription(entity.album)
            .setIconUri(if (entity.coverArtUrl.isNotBlank()) Uri.parse(entity.coverArtUrl) else null)
            .setExtras(Bundle().apply {
                putLong(MediaMetadata.METADATA_KEY_DURATION, entity.durationSeconds * 1000L)
            })
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE)
    }

    private fun albumToMediaItem(album: NaviromAlbum): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId("album_${album.id}")
            .setTitle(album.name)
            .setSubtitle(album.artist)
            .setDescription("${album.songCount} songs • ${album.year ?: ""}")
            .setIconUri(if (album.coverArtUrl.isNotBlank()) Uri.parse(album.coverArtUrl) else null)
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE)
    }

    private fun playlistToMediaItem(playlist: NaviromPlaylist): MediaBrowser.MediaItem {
        val coverUrl = if (playlist.coverArt.isNotBlank()) subsonicClient.getCoverArtUrl(playlist.coverArt) else ""
        val desc = MediaDescription.Builder()
            .setMediaId("playlist_${playlist.id}")
            .setTitle(playlist.name)
            .setSubtitle("${playlist.songCount} songs")
            .setIconUri(if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else null)
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE)
    }

    private fun artistToMediaItem(artist: NaviromArtist): MediaBrowser.MediaItem {
        val desc = MediaDescription.Builder()
            .setMediaId("artist_${artist.id}")
            .setTitle(artist.name)
            .setSubtitle("${artist.albumCount} albums")
            .setIconUri(if (artist.coverArtUrl.isNotBlank()) Uri.parse(artist.coverArtUrl) else null)
            .build()
        return MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Navirom Playback"
            val descriptionText = "Music playback controls and lock screen status"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "NaviromPlaybackSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackToLocal(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    } ?: return super.onMediaButtonEvent(mediaButtonIntent)

                    if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                        val player = getOrInitPlayerController()
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                                player.next()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                                player.previous()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                                player.togglePlayPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                player.resume()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                                player.pause()
                                return true
                            }
                            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                                return false
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPlay() {
                    getOrInitPlayerController().resume()
                }

                override fun onPause() {
                    getOrInitPlayerController().pause()
                }

                override fun onSkipToNext() {
                    getOrInitPlayerController().next()
                }

                override fun onSkipToPrevious() {
                    getOrInitPlayerController().previous()
                }

                override fun onSeekTo(pos: Long) {
                    getOrInitPlayerController().seekTo(pos)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId.isNullOrBlank()) return
                    val player = getOrInitPlayerController()

                    serviceScope.launch(Dispatchers.IO) {
                        ensureSubsonicClientConfigured()
                        val db = NaviromDatabase.getDatabase(this@NaviromPlaybackService)

                        when {
                            mediaId == MEDIA_ID_SHUFFLE_ALL -> {
                                val songsRes = subsonicClient.getRandomTracks(size = 50)
                                val list = songsRes.getOrNull() ?: db.cachedTrackDao().getAllCachedTracks().firstOrNull()?.map {
                                    NaviromTrack(
                                        id = it.id,
                                        title = it.title,
                                        artist = it.artist,
                                        artistId = it.artistId,
                                        album = it.album,
                                        albumId = it.albumId,
                                        durationSeconds = it.durationSeconds,
                                        coverArtUrl = it.coverArtUrl,
                                        streamUrl = subsonicClient.getStreamUrl(it.id),
                                        localFilePath = it.localFilePath,
                                        isCached = true
                                    )
                                } ?: emptyList()
                                if (list.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(list.shuffled(), 0)
                                    }
                                }
                            }
                            mediaId == MEDIA_ID_QUICK_MIX -> {
                                val mixRes = subsonicClient.getRandomTracks(size = 40)
                                mixRes.onSuccess { tracks ->
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(tracks, 0)
                                    }
                                }
                            }
                            mediaId == MEDIA_ID_OFFLINE -> {
                                val cached = db.cachedTrackDao().getAllCachedTracks().firstOrNull() ?: emptyList()
                                val tracks = cached.map {
                                    NaviromTrack(
                                        id = it.id,
                                        title = it.title,
                                        artist = it.artist,
                                        artistId = it.artistId,
                                        album = it.album,
                                        albumId = it.albumId,
                                        durationSeconds = it.durationSeconds,
                                        coverArtUrl = it.coverArtUrl,
                                        streamUrl = subsonicClient.getStreamUrl(it.id),
                                        localFilePath = it.localFilePath,
                                        isCached = true
                                    )
                                }
                                if (tracks.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(tracks, 0)
                                    }
                                }
                            }
                            mediaId.startsWith("album_") -> {
                                val albId = mediaId.removePrefix("album_")
                                val albumRes = subsonicClient.getAlbumDetails(albId)
                                albumRes.onSuccess { pair ->
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(pair.second, 0)
                                    }
                                }
                            }
                            mediaId.startsWith("playlist_") -> {
                                val plId = mediaId.removePrefix("playlist_")
                                val localItems = db.playlistDao().getPlaylistItems(plId).firstOrNull() ?: emptyList()
                                if (localItems.isNotEmpty()) {
                                    val tracks = localItems.map { pi ->
                                        NaviromTrack(
                                            id = pi.trackId,
                                            title = pi.title,
                                            artist = pi.artist,
                                            artistId = "",
                                            album = pi.album,
                                            albumId = "",
                                            durationSeconds = pi.durationSeconds,
                                            coverArtUrl = pi.coverArtUrl,
                                            streamUrl = subsonicClient.getStreamUrl(pi.trackId),
                                            localFilePath = pi.localFilePath,
                                            isCached = pi.localFilePath != null
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(tracks, 0)
                                    }
                                } else {
                                    val serverPlRes = subsonicClient.getPlaylistDetails(plId)
                                    serverPlRes.onSuccess { pair ->
                                        withContext(Dispatchers.Main) {
                                            player.playTrackList(pair.second, 0)
                                        }
                                    }
                                }
                            }
                            mediaId.startsWith("track_") -> {
                                val trackId = mediaId.removePrefix("track_")
                                val currentQueue = player.queue.value
                                val queueIdx = currentQueue.indexOfFirst { it.id == trackId }
                                if (queueIdx >= 0) {
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(currentQueue, queueIdx)
                                    }
                                } else {
                                    val cached = db.cachedTrackDao().getCachedTrack(trackId)
                                    if (cached != null) {
                                        val trk = NaviromTrack(
                                            id = cached.id,
                                            title = cached.title,
                                            artist = cached.artist,
                                            artistId = cached.artistId,
                                            album = cached.album,
                                            albumId = cached.albumId,
                                            durationSeconds = cached.durationSeconds,
                                            coverArtUrl = cached.coverArtUrl,
                                            streamUrl = subsonicClient.getStreamUrl(cached.id),
                                            localFilePath = cached.localFilePath,
                                            isCached = true
                                        )
                                        withContext(Dispatchers.Main) {
                                            player.playTrack(trk)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    if (query.isNullOrBlank()) {
                        getOrInitPlayerController().resume()
                        return
                    }

                    val player = getOrInitPlayerController()
                    serviceScope.launch(Dispatchers.IO) {
                        ensureSubsonicClientConfigured()
                        val searchRes = subsonicClient.search(query.trim())
                        searchRes.onSuccess { triple ->
                            val tracks = triple.third
                            val albums = triple.second
                            if (tracks.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    player.playTrackList(tracks, 0)
                                }
                            } else if (albums.isNotEmpty()) {
                                val albumRes = subsonicClient.getAlbumDetails(albums.first().id)
                                albumRes.onSuccess { pair ->
                                    withContext(Dispatchers.Main) {
                                        player.playTrackList(pair.second, 0)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    val player = getOrInitPlayerController()
                    when (action) {
                        "ACTION_SHUFFLE_TOGGLE", "android.media.session.action.SHUFFLE" -> player.toggleShuffle()
                        "ACTION_REPEAT_CYCLE", "android.media.session.action.REPEAT" -> player.cycleRepeatMode()
                        "ACTION_REPEAT_ONE" -> player.setRepeatMode(RepeatMode.ONE)
                        "ACTION_REPEAT_ALL" -> player.setRepeatMode(RepeatMode.ALL)
                        "ACTION_REPEAT_OFF" -> player.setRepeatMode(RepeatMode.OFF)
                        "ACTION_CLOSE", "ACTION_EXIT", "CLOSE", "EXIT" -> {
                            player.pause()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                            }
                            isForegroundService = false
                            stopSelf()
                            val closeAppIntent = Intent(this@NaviromPlaybackService, MainActivity::class.java).apply {
                                setAction("ACTION_CLOSE_APP")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(closeAppIntent)
                        }
                    }
                }

                override fun onStop() {
                    val player = getOrInitPlayerController()
                    player.pause()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                    isForegroundService = false
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = getOrInitPlayerController()

        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_NAVIGATE_NEXT -> player.next()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> player.previous()
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> player.togglePlayPause()
                        KeyEvent.KEYCODE_MEDIA_PLAY -> player.resume()
                        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> player.pause()
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (keyEvent.isLongPress || keyEvent.repeatCount == 1) player.next()
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (keyEvent.isLongPress || keyEvent.repeatCount == 1) player.previous()
                        }
                    }
                }
            }
            ACTION_PLAY -> player.resume()
            ACTION_PAUSE -> player.pause()
            ACTION_TOGGLE_PLAY -> player.togglePlayPause()
            ACTION_PREVIOUS -> player.previous()
            ACTION_NEXT -> player.next()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                player.seekTo(pos)
            }
            ACTION_STOP -> {
                player.pause()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForegroundService = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CLOSE -> {
                player.pause()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForegroundService = false
                stopSelf()
                val closeAppIntent = Intent(this, MainActivity::class.java).apply {
                    action = "ACTION_CLOSE_APP"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                try {
                    startActivity(closeAppIntent)
                } catch (_: Exception) {}
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE -> {
                updatePlaybackAndNotification()
            }
        }

        updatePlaybackAndNotification()
        return START_STICKY
    }

    private fun updatePlaybackAndNotification() {
        val player = getOrInitPlayerController()
        val state = player.playbackState.value
        val track = state.currentTrack

        if (track == null) {
            if (!isForegroundService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notifBuilder = Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notif_music)
                    .setContentTitle("Navirom")
                    .setContentText("Playback stopped")
                val notification = notifBuilder.build()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {}
            }
            if (isForegroundService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForegroundService = false
            }
            stopSelf()
            return
        }

        // Update MediaSession PlaybackState
        updateMediaSessionPlaybackState(state)

        // Update Metadata & Notification
        if (lastCoverUrl != track.coverArtUrl) {
            lastCoverUrl = track.coverArtUrl
            cachedBitmap = null
            updateMediaSessionMetadata(track, state.durationMs, null)
            showNotification(track, state.isPlaying, null)
            serviceScope.launch(Dispatchers.IO) {
                val bitmap = loadCoverArtBitmap(track.coverArtUrl)
                cachedBitmap = bitmap
                withContext(Dispatchers.Main) {
                    updateMediaSessionMetadata(track, state.durationMs, bitmap)
                    showNotification(track, state.isPlaying, bitmap)
                }
            }
        } else {
            updateMediaSessionMetadata(track, state.durationMs, cachedBitmap)
            showNotification(track, state.isPlaying, cachedBitmap)
        }
    }

    private fun updateMediaSessionPlaybackState(state: PlaybackState) {
        val session = mediaSession ?: return
        val androidState = when {
            state.isBuffering -> AndroidPlaybackState.STATE_BUFFERING
            state.isPlaying -> AndroidPlaybackState.STATE_PLAYING
            state.currentTrack != null -> AndroidPlaybackState.STATE_PAUSED
            else -> AndroidPlaybackState.STATE_STOPPED
        }

        val actions = AndroidPlaybackState.ACTION_PLAY or
                AndroidPlaybackState.ACTION_PAUSE or
                AndroidPlaybackState.ACTION_PLAY_PAUSE or
                AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                AndroidPlaybackState.ACTION_SEEK_TO or
                AndroidPlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                AndroidPlaybackState.ACTION_PLAY_FROM_SEARCH or
                AndroidPlaybackState.ACTION_STOP

        val stateBuilder = AndroidPlaybackState.Builder()
            .setActions(actions)
            .addCustomAction(
                AndroidPlaybackState.CustomAction.Builder(
                    "ACTION_CLOSE",
                    "Exit",
                    R.drawable.ic_notif_close
                ).build()
            )
            .setState(
                androidState,
                state.currentPositionMs,
                if (state.isPlaying) state.playbackSpeed else 0f,
                SystemClock.elapsedRealtime()
            )

        session.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaSessionMetadata(track: NaviromTrack, durationMs: Long, bitmap: Bitmap?) {
        val session = mediaSession ?: return
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else (track.durationSeconds * 1000L))
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, track.coverArtUrl)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, track.coverArtUrl)

        if (bitmap != null && !bitmap.isRecycled) {
            try {
                val copiedBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                if (copiedBitmap != null && !copiedBitmap.isRecycled) {
                    metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, copiedBitmap)
                    metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, copiedBitmap)
                }
            } catch (e: Exception) {
                if (!bitmap.isRecycled) {
                    metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                    metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
                }
            }
        }

        session.setMetadata(metadataBuilder.build())
    }

    private fun showNotification(track: NaviromTrack, isPlaying: Boolean, coverBitmap: Bitmap?) {
        val session = mediaSession ?: return

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NaviromPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, NaviromPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, NaviromPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitAppActivityIntent = PendingIntent.getActivity(
            this,
            4,
            Intent(this, MainActivity::class.java).apply {
                action = "ACTION_CLOSE_APP"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deleteServiceIntent = PendingIntent.getService(
            this,
            5,
            Intent(this, NaviromPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val player = getOrInitPlayerController()
        val state = player.playbackState.value

        val notifBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        notifBuilder
            .setSmallIcon(R.drawable.ic_notif_music)
            .setContentTitle(track.title)
            .setContentText("${track.artist} • ${track.album}")
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteServiceIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_notif_previous,
                    "Previous",
                    prevIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    playPauseIcon,
                    playPauseTitle,
                    playPauseIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_notif_next,
                    "Next",
                    nextIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_notif_close,
                    "Exit",
                    exitAppActivityIntent
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 3)
            )

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            try {
                val copiedBitmap = coverBitmap.copy(coverBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                if (copiedBitmap != null && !copiedBitmap.isRecycled) {
                    notifBuilder.setLargeIcon(copiedBitmap)
                }
            } catch (e: Exception) {
                if (!coverBitmap.isRecycled) {
                    notifBuilder.setLargeIcon(coverBitmap)
                }
            }
        }

        val notification = notifBuilder.build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundService = true
        } catch (e: Exception) {
            Log.w("PlaybackService", "Foreground service start disallowed: ${e.message}")
            isForegroundService = false
            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (ne: Exception) {
                Log.e("PlaybackService", "Error posting fallback notification", ne)
            }
        }
    }

    private suspend fun loadCoverArtBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            val maxDim = 512
                            val width = bitmap.width
                            val height = bitmap.height
                            if (width > maxDim || height > maxDim) {
                                val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                                val scaled = Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
                                if (scaled != bitmap) {
                                    bitmap.recycle()
                                }
                                scaled
                            } else bitmap
                        } else null
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
        serviceScope.cancel()
        cachedBitmap?.recycle()
        cachedBitmap = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        stopForeground(true)
    }
}
