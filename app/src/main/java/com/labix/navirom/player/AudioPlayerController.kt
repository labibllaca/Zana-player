package com.labix.navirom.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.labix.navirom.data.cache.OfflineDownloadManager
import com.labix.navirom.data.local.CachedTrackDao
import com.labix.navirom.data.local.NaviromDatabase
import com.labix.navirom.data.local.PlaybackQueueDao
import com.labix.navirom.data.local.PlaybackQueueEntity
import com.labix.navirom.data.model.AudioOutputDevice
import com.labix.navirom.data.model.NaviromTrack
import com.labix.navirom.data.model.PlaybackState
import com.labix.navirom.data.model.SecondaryPlaybackState
import com.labix.navirom.data.model.RepeatMode
import com.labix.navirom.data.model.SleepTimerOptions
import com.labix.navirom.player.wlan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import java.io.File

import com.labix.navirom.diagnostics.AppDiagnostics
import com.labix.navirom.diagnostics.DiagnosticCodes

class AudioPlayerController(
    private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val cachedTrackDao: CachedTrackDao,
    private val playbackQueueDao: PlaybackQueueDao
) {
    var urlResolver: ((String) -> String)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var crossfadeJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playOnFocusGain = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                playOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playOnFocusGain = _playbackState.value.isPlaying
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                } catch (_: Exception) {}
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (_: Exception) {}
                if (playOnFocusGain) {
                    playOnFocusGain = false
                    resume()
                }
            }
        }
    }

    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    private val wifiLock: WifiManager.WifiLock? by lazy {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL
            }
            wm?.createWifiLock(mode, "Navirom:WifiLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (_: Exception) {}
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                context.registerReceiver(
                    noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                isNoisyReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isNoisyReceiverRegistered = false
        }
    }

    private val wakeLock: android.os.PowerManager.WakeLock? by lazy {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Navirom:WakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == false) {
                // Safety timeout on wakeLock (max 45 min) to prevent battery watchdog SIGKILL
                wakeLock?.acquire(45 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    var isCrossfadeEnabled: Boolean = false
    var crossfadeDurationMs: Long = 5000L

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var secondaryMediaPlayer: MediaPlayer? = null
    private val _secondaryPlaybackState = MutableStateFlow(SecondaryPlaybackState())
    val secondaryPlaybackState: StateFlow<SecondaryPlaybackState> = _secondaryPlaybackState.asStateFlow()
    private var secondaryQueue: List<NaviromTrack> = emptyList()
    private var secondaryCurrentIndex: Int = -1

    private val _availableOutputDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableOutputDevices: StateFlow<List<AudioOutputDevice>> = _availableOutputDevices.asStateFlow()

    private val _player1DeviceId = MutableStateFlow<Int?>(null)
    val player1DeviceId: StateFlow<Int?> = _player1DeviceId.asStateFlow()

    private val _player2DeviceId = MutableStateFlow<Int?>(null)
    val player2DeviceId: StateFlow<Int?> = _player2DeviceId.asStateFlow()

    private val _isDeckSyncEnabled = MutableStateFlow<Boolean>(false)
    val isDeckSyncEnabled: StateFlow<Boolean> = _isDeckSyncEnabled.asStateFlow()

    private var audioDeviceCallback: Any? = null

    init {
        initAudioDeviceListener()
    }

    private fun initAudioDeviceListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val callback = object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        refreshOutputDevices()
                    }
                    override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        refreshOutputDevices()
                    }
                }
                audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
                audioDeviceCallback = callback
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to register AudioDeviceCallback", e)
            }
        }
        refreshOutputDevices()
    }

    fun refreshOutputDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val list = devices.mapNotNull { dev ->
                    val typeName = when (dev.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphones"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE Audio"
                        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
                        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Out"
                        else -> "Audio Output"
                    }
                    val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeName
                    } else {
                        typeName
                    }
                    val isBt = dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET || dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST))
                    val isSpeaker = dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    val isHp = dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES

                    AudioOutputDevice(
                        id = dev.id,
                        name = rawName,
                        typeName = typeName,
                        isBluetooth = isBt,
                        isSpeaker = isSpeaker,
                        isHeadphones = isHp
                    )
                }
                _availableOutputDevices.value = list
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to query audio output devices", e)
            }
        }
    }

    fun setPlayer1PreferredDevice(deviceId: Int?) {
        _player1DeviceId.value = deviceId
        applyPreferredDevice(mediaPlayer, deviceId)
    }

    fun setPlayer2PreferredDevice(deviceId: Int?) {
        _player2DeviceId.value = deviceId
        applyPreferredDevice(secondaryMediaPlayer, deviceId)
    }

    private fun applyPreferredDevice(player: MediaPlayer?, deviceId: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && player != null) {
            try {
                val dev = if (deviceId != null) {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
                } else null
                player.setPreferredDevice(dev)
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to setPreferredDevice $deviceId", e)
            }
        }
    }

    fun toggleDeckSync() {
        setDeckSync(!_isDeckSyncEnabled.value)
    }

    fun setDeckSync(enabled: Boolean) {
        _isDeckSyncEnabled.value = enabled
        _secondaryPlaybackState.update { it.copy(isSyncedWithPrimary = enabled) }
        if (enabled) {
            val p1Track = _playbackState.value.currentTrack
            if (p1Track != null) {
                val p2Track = _secondaryPlaybackState.value.currentTrack
                val p1Pos = try { mediaPlayer?.currentPosition?.toLong() ?: _playbackState.value.currentPositionMs } catch (_: Exception) { _playbackState.value.currentPositionMs }
                val p1IsPlaying = try { (mediaPlayer?.isPlaying == true) || _playbackState.value.isPlaying } catch (_: Exception) { false }

                if (p2Track?.id == p1Track.id && secondaryMediaPlayer != null) {
                    // Both players already have the same track loaded! Align directly without reloading
                    seekSecondaryTo(p1Pos, syncPrimary = false)
                    if (p1IsPlaying) {
                        try {
                            secondaryMediaPlayer?.start()
                            _secondaryPlaybackState.update { it.copy(isPlaying = true) }
                            startTicker()
                        } catch (_: Exception) {}
                    } else {
                        pauseSecondary()
                    }
                } else {
                    // Deck 2 needs the track loaded for sync
                    playSecondaryTrack(p1Track, _queue.value)
                }
            }
        } else {
            // Revert secondary speed to standard speed when sync is disabled
            applySecondarySpeed(_playbackState.value.playbackSpeed)
        }
    }

    private val _queue = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val queue: StateFlow<List<NaviromTrack>> = _queue.asStateFlow()

    val wlanSpeakerManager: WlanSpeakerManager by lazy {
        WlanSpeakerManager(
            context = context,
            downloadManager = downloadManager,
            urlResolverProvider = { urlResolver },
            currentTrackProvider = { _playbackState.value.currentTrack },
            currentQueueProvider = { _queue.value },
            onTrackEndedOnSpeaker = {
                scope.launch {
                    next()
                }
            }
        )
    }

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var originalQueueList: List<NaviromTrack> = emptyList()

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var currentSleepTimerOptions = SleepTimerOptions()
    private val deviceConnectionManager = DeviceConnectionManager(context)
    private var isPreparingNextForCrossfade = false
    private var hasCheckedQueueForCurrentTrack = false
    private var midSongCheckJob: Job? = null

    // Seek synchronization & state stabilization for ffw/rev
    @Volatile
    private var isSeeking = false
    @Volatile
    private var pendingSeekPos = -1L
    @Volatile
    private var lastSeekTime = 0L
    @Volatile
    private var targetSeekPositionMs = 0L

    // Deck synchronization & phase-lock loop (PLL)
    @Volatile
    private var isSecondarySeeking = false
    @Volatile
    private var isWaitingForSyncedDeckStart = false
    @Volatile
    private var isDeck1ReadyForSync = false
    @Volatile
    private var isDeck2ReadyForSync = false
    private var syncStartJob: Job? = null
    private var lastSyncSeekTime = 0L
    private var currentSecondaryAppliedSpeed = 1.0f

    fun markTrackUnplayable(trackId: String) {
        if (trackId.isBlank()) return
        _playbackState.update { state ->
            state.copy(unplayableTrackIds = state.unplayableTrackIds + trackId)
        }
    }

    fun findNextPlayableIndex(fromIndex: Int): Int? {
        val q = _queue.value
        if (q.isEmpty()) return null
        val unplayable = _playbackState.value.unplayableTrackIds

        if (fromIndex in q.indices) {
            for (i in fromIndex until q.size) {
                if (!unplayable.contains(q[i].id)) {
                    return i
                }
            }
        }
        if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            val limit = fromIndex.coerceAtMost(q.size)
            for (i in 0 until limit) {
                if (!unplayable.contains(q[i].id)) {
                    return i
                }
            }
        }
        return null
    }

    private fun skipToNextPlayableOrStop() {
        scope.launch(Dispatchers.Main) {
            val nextIdx = findNextPlayableIndex(_currentIndex.value + 1)
            if (nextIdx != null) {
                _currentIndex.value = nextIdx
                playCurrentTrack()
            } else {
                pause()
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "No playable tracks in queue"
                    )
                }
            }
        }
    }

    private fun triggerMidSongQueueCheck() {
        midSongCheckJob?.cancel()
        midSongCheckJob = scope.launch(Dispatchers.IO) {
            val currentIdx = _currentIndex.value
            val currentQueue = _queue.value
            if (currentIdx + 1 in currentQueue.indices) {
                checkQueueRecursively(currentQueue, currentIdx + 1, maxChecks = 2)
            }
        }
    }

    private suspend fun checkQueueRecursively(queueList: List<NaviromTrack>, index: Int, maxChecks: Int = 2) {
        if (index !in queueList.indices || maxChecks <= 0) return

        val track = queueList[index]
        if (_playbackState.value.unplayableTrackIds.contains(track.id)) {
            checkQueueRecursively(queueList, index + 1, maxChecks - 1)
            return
        }

        val playable = isTrackPlayable(track)
        if (!playable) {
            Log.w("AudioPlayerController", "Mid-song check: track '${track.title}' (${track.id}) is unplayable. Highlighting red.")
            markTrackUnplayable(track.id)
            checkQueueRecursively(queueList, index + 1, maxChecks - 1)
        } else {
            Log.d("AudioPlayerController", "Mid-song check: track '${track.title}' (${track.id}) is verified playable.")
        }
    }

    private suspend fun isTrackPlayable(track: NaviromTrack): Boolean {
        if (track.id.isBlank()) return false
        if (_playbackState.value.unplayableTrackIds.contains(track.id)) return false

        // 1. Any local or cached track is ALWAYS playable offline without network!
        if (track.id.startsWith("local_")) return true
        if (track.localFilePath?.startsWith("content://") == true) return true
        if (track.streamUrl.startsWith("content://")) return true

        val cachedEntity = try { cachedTrackDao.getCachedTrack(track.id) } catch (_: Exception) { null }
        val cachedFilePath = cachedEntity?.localFilePath
        if (!cachedFilePath.isNullOrBlank() && File(cachedFilePath).exists()) return true

        val downloadFile = downloadManager.getLocalFileForTrack(track.id)
        if (downloadFile.exists() && downloadFile.length() > 0) return true

        if (!track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists()) return true
        if (!track.path.isNullOrBlank() && File(track.path).exists()) return true

        // 2. Check stream URL
        if (track.streamUrl.isBlank()) return false

        val resolvedUrl = try {
            urlResolver?.invoke(track.streamUrl) ?: track.streamUrl
        } catch (_: Exception) {
            ""
        }

        if (resolvedUrl.isBlank()) return false

        if (resolvedUrl.startsWith("file://") || resolvedUrl.startsWith("/")) {
            val path = resolvedUrl.removePrefix("file://")
            return File(path).exists()
        }

        if (resolvedUrl.startsWith("content://")) {
            return true
        }

        // 3. Network URL probe (safely closing all streams)
        return try {
            val connection = (java.net.URL(resolvedUrl).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-100")
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = true
            }
            val responseCode = connection.responseCode
            try { connection.inputStream?.close() } catch (_: Exception) {}
            try { connection.errorStream?.close() } catch (_: Exception) {}
            connection.disconnect()
            responseCode in 200..299 || responseCode == 206
        } catch (e: Exception) {
            Log.w("AudioPlayerController", "Track '${track.title}' failed playability check: ${e.message}")
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerController? = null

        fun getInstance(context: Context): AudioPlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val db = NaviromDatabase.getDatabase(appContext)
                    val downloadManager = OfflineDownloadManager(appContext, db.cachedTrackDao())
                    AudioPlayerController(
                        context = appContext,
                        downloadManager = downloadManager,
                        cachedTrackDao = db.cachedTrackDao(),
                        playbackQueueDao = db.playbackQueueDao()
                    ).also { INSTANCE = it }
                }
            }
        }
    }

    init {
        initMediaPlayer()
        restoreQueueFromRoom()
        scope.launch {
            _playbackState
                .distinctUntilChanged { old, new ->
                    old.currentTrack?.id == new.currentTrack?.id &&
                    old.isPlaying == new.isPlaying &&
                    old.isBuffering == new.isBuffering &&
                    old.repeatMode == new.repeatMode &&
                    old.isShuffle == new.isShuffle &&
                    old.playbackSpeed == new.playbackSpeed
                }
                .collect { state ->
                    NaviromPlaybackService.updateService(context, state)
                }
        }
    }

    private fun persistQueueToRoom(tracks: List<NaviromTrack>) {
        scope.launch(Dispatchers.IO) {
            try {
                playbackQueueDao.clearQueue()
                val entities = tracks.mapIndexed { idx, t ->
                    PlaybackQueueEntity(
                        position = idx,
                        trackId = t.id,
                        title = t.title,
                        artist = t.artist,
                        artistId = t.artistId,
                        album = t.album,
                        albumId = t.albumId,
                        durationSeconds = t.durationSeconds,
                        coverArtUrl = t.coverArtUrl,
                        streamUrl = t.streamUrl,
                        localFilePath = t.localFilePath,
                        format = t.suffix,
                        year = t.year,
                        genre = t.genre,
                        bitRate = t.bitRate,
                        sizeBytes = t.sizeBytes
                    )
                }
                playbackQueueDao.insertQueue(entities)
            } catch (e: Exception) {
                Log.w("AudioPlayerController", "Failed to persist queue to Room", e)
            }
        }
    }

    private fun restoreQueueFromRoom() {
        scope.launch(Dispatchers.IO) {
            try {
                val queueEntities = playbackQueueDao.getQueueList()
                if (queueEntities.isNotEmpty()) {
                    val restoredTracks = queueEntities.map { entity ->
                        val localPath = entity.localFilePath
                        val isLocalValid = !localPath.isNullOrBlank() && File(localPath).exists()
                        NaviromTrack(
                            id = entity.trackId,
                            title = entity.title,
                            artist = entity.artist,
                            artistId = entity.artistId,
                            album = entity.album,
                            albumId = entity.albumId,
                            durationSeconds = entity.durationSeconds,
                            coverArtUrl = entity.coverArtUrl,
                            streamUrl = entity.streamUrl,
                            localFilePath = if (isLocalValid) localPath else null,
                            suffix = entity.format,
                            year = entity.year,
                            genre = entity.genre,
                            bitRate = entity.bitRate,
                            sizeBytes = entity.sizeBytes,
                            isCached = isLocalValid
                        )
                    }
                    withContext(Dispatchers.Main) {
                        _queue.value = restoredTracks
                        originalQueueList = restoredTracks
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioPlayerController", "Failed to restore queue from Room", e)
            }
        }
    }

    private var lastTrackSwitchTime = 0L
    private var lastPreviousPressTime = 0L

    private fun safelyReleasePlayer(player: MediaPlayer?) {
        if (player == null) return
        try {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.setOnSeekCompleteListener(null)
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}
            player.reset()
            player.release()
        } catch (e: Exception) {
            try {
                player.release()
            } catch (_: Exception) {}
        }
    }

    private fun initMediaPlayer() {
        safelyReleasePlayer(mediaPlayer)
        mediaPlayer = createMediaPlayer()
    }

    private fun createMediaPlayer(): MediaPlayer {
        return MediaPlayer().apply {
            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            applyPreferredDevice(this, _player1DeviceId.value)
            setOnPreparedListener { mp ->
                handlePrepared(mp)
            }
            setOnCompletionListener { mp ->
                if (mp == mediaPlayer) {
                    handleTrackCompletion()
                } else {
                    safelyReleasePlayer(mp)
                }
            }
            setOnSeekCompleteListener { mp ->
                if (mp == mediaPlayer) {
                    val nextSeek = pendingSeekPos
                    if (nextSeek >= 0L) {
                        pendingSeekPos = -1L
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(nextSeek, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(nextSeek.toInt())
                            }
                        } catch (_: Exception) {
                            isSeeking = false
                        }
                    } else {
                        isSeeking = false
                        val finalPos = try { mp.currentPosition.toLong() } catch (_: Exception) { targetSeekPositionMs }
                        _playbackState.update { it.copy(currentPositionMs = finalPos) }
                    }
                }
            }
            setOnErrorListener { mp, what, extra ->
                if (mp == mediaPlayer) {
                    val current = _playbackState.value.currentTrack
                    val errMsg = "MediaPlayer error: what=$what, extra=$extra"
                    Log.e("AudioPlayer", errMsg)
                    AppDiagnostics.logError(
                        errorCode = DiagnosticCodes.PLAYER_PLAYBACK_ERR_303,
                        tag = "AudioPlayer",
                        message = errMsg,
                        contextInfo = "Track: ${current?.title}"
                    )
                    
                    // -38 is MEDIA_ERROR_INVALID_OPERATION, which is a state machine warning (not a media stream error).
                    // We must ignore it to prevent false skip-to-next and marking tracks as unplayable.
                    if (what == -38 || extra == -38) {
                        Log.w("AudioPlayer", "Ignoring non-fatal invalid operation error (-38)")
                        return@setOnErrorListener true
                    }

                    if (current != null) {
                        markTrackUnplayable(current.id)
                        skipToNextPlayableOrStop()
                    } else {
                        _playbackState.update {
                            it.copy(
                                isPlaying = false,
                                isBuffering = false,
                                errorMessage = "Playback error ($what, $extra)"
                            )
                        }
                    }
                }
                true
            }
        }
    }

    private fun createSecondaryMediaPlayer(): MediaPlayer {
        return MediaPlayer().apply {
            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
            applyPreferredDevice(this, _player2DeviceId.value)
            val currentVol = _secondaryPlaybackState.value.volume
            setVolume(currentVol, currentVol)

            setOnSeekCompleteListener { mp ->
                if (mp == secondaryMediaPlayer) {
                    isSecondarySeeking = false
                    val finalPos = try { mp.currentPosition.toLong() } catch (_: Exception) { _secondaryPlaybackState.value.currentPositionMs }
                    _secondaryPlaybackState.update { it.copy(currentPositionMs = finalPos) }
                }
            }

            setOnPreparedListener { mp ->
                if (mp == secondaryMediaPlayer) {
                    acquireWifiLock()
                    val dur = try { mp.duration.toLong() } catch (_: Exception) { 0L }
                    val shouldPlay = if (_isDeckSyncEnabled.value) _playbackState.value.isPlaying else true
                    val liveP1 = try { if (mediaPlayer?.isPlaying == true) mediaPlayer?.currentPosition?.toLong() else _playbackState.value.currentPositionMs } catch (_: Exception) { _playbackState.value.currentPositionMs }
                    val syncPos = if (_isDeckSyncEnabled.value) (liveP1 ?: 0L) else 0L

                    _secondaryPlaybackState.update {
                        it.copy(
                            isBuffering = false,
                            currentPositionMs = syncPos,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }

                    if (_isDeckSyncEnabled.value && isWaitingForSyncedDeckStart) {
                        isDeck2ReadyForSync = true
                        if (isDeck1ReadyForSync) {
                            startBothSyncedPlayers()
                        }
                    } else if (_isDeckSyncEnabled.value && syncPos > 25L) {
                        // Seeking to align with already-playing Primary Deck
                        mp.setOnSeekCompleteListener { seekedMp ->
                            if (seekedMp == secondaryMediaPlayer) {
                                isSecondarySeeking = false
                                val finalPos = try { seekedMp.currentPosition.toLong() } catch (_: Exception) { syncPos }
                                _secondaryPlaybackState.update { it.copy(currentPositionMs = finalPos) }
                                if (shouldPlay && _playbackState.value.isPlaying) {
                                    try {
                                        seekedMp.start()
                                        _secondaryPlaybackState.update { it.copy(isPlaying = true) }
                                        startTicker()
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        isSecondarySeeking = true
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(syncPos, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(syncPos.toInt())
                            }
                        } catch (_: Exception) {
                            isSecondarySeeking = false
                            if (shouldPlay) {
                                mp.start()
                                _secondaryPlaybackState.update { it.copy(isPlaying = true) }
                                startTicker()
                            }
                        }
                    } else {
                        if (shouldPlay) {
                            mp.start()
                            _secondaryPlaybackState.update { it.copy(isPlaying = true) }
                            startTicker()
                        }
                    }
                }
            }

            setOnCompletionListener { mp ->
                if (mp == secondaryMediaPlayer) {
                    if (_isDeckSyncEnabled.value) {
                        _secondaryPlaybackState.update {
                            it.copy(isPlaying = false, currentPositionMs = 0L)
                        }
                    } else if (secondaryCurrentIndex + 1 < secondaryQueue.size) {
                        playSecondaryNext()
                    } else {
                        val mode = _playbackState.value.repeatMode
                        if (mode == RepeatMode.ALL && secondaryQueue.isNotEmpty()) {
                            val nextTrack = secondaryQueue[0]
                            playSecondaryTrack(nextTrack, secondaryQueue)
                        } else {
                            _secondaryPlaybackState.update {
                                it.copy(isPlaying = false, currentPositionMs = 0L)
                            }
                        }
                    }
                } else {
                    safelyReleasePlayer(mp)
                }
            }

            setOnErrorListener { mp, what, extra ->
                if (mp == secondaryMediaPlayer) {
                    val current = _secondaryPlaybackState.value.currentTrack
                    val errMsg = "Secondary MediaPlayer error: what=$what, extra=$extra"
                    Log.e("AudioPlayer", errMsg)
                    
                    if (what == -38 || extra == -38) {
                        return@setOnErrorListener true
                    }

                    _secondaryPlaybackState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playback error ($what, $extra)"
                        )
                    }
                    safelyReleasePlayer(mp)
                    if (secondaryMediaPlayer == mp) secondaryMediaPlayer = null

                    if (!_isDeckSyncEnabled.value && secondaryCurrentIndex + 1 < secondaryQueue.size) {
                        playSecondaryNext()
                    }
                }
                true
            }
        }
    }

    private suspend fun resolveTrackMediaSource(track: NaviromTrack): Pair<Uri?, String?> {
        val cachedEntity = withContext(Dispatchers.IO) {
            try {
                cachedTrackDao.getCachedTrack(track.id)
            } catch (e: Exception) {
                null
            }
        }

        val cachedFilePath = cachedEntity?.localFilePath
        val isCachedFileValid = !cachedFilePath.isNullOrBlank() && File(cachedFilePath).exists()

        val downloadFile = downloadManager.getLocalFileForTrack(track.id)
        val isDownloadFileValid = downloadFile.exists() && downloadFile.length() > 0

        val isLocalContentUri = track.localFilePath?.startsWith("content://") == true || track.streamUrl.startsWith("content://")
        val isLocalTrack = track.id.startsWith("local_") || isLocalContentUri

        val isLocalFileDirect = !track.localFilePath.isNullOrBlank() && !track.localFilePath.startsWith("content://") && File(track.localFilePath).exists()
        val isPathFileDirect = !track.path.isNullOrBlank() && File(track.path).exists()

        val resolvedLocalUri: Uri? = when {
            isCachedFileValid -> Uri.fromFile(File(cachedFilePath!!))
            isDownloadFileValid -> Uri.fromFile(downloadFile)
            isLocalFileDirect -> Uri.fromFile(File(track.localFilePath!!))
            isPathFileDirect -> Uri.fromFile(File(track.path))
            track.localFilePath?.startsWith("content://") == true -> Uri.parse(track.localFilePath)
            track.streamUrl.startsWith("content://") -> Uri.parse(track.streamUrl)
            isLocalTrack && track.streamUrl.isNotBlank() -> Uri.parse(track.streamUrl)
            else -> null
        }

        val resolvedLocalPath = when {
            isCachedFileValid -> cachedFilePath
            isDownloadFileValid -> downloadFile.absolutePath
            isLocalFileDirect -> track.localFilePath
            isPathFileDirect -> track.path
            resolvedLocalUri != null -> resolvedLocalUri.toString()
            else -> null
        }

        return Pair(resolvedLocalUri, resolvedLocalPath)
    }

    private fun handlePrepared(mp: MediaPlayer) {
        if (mp != mediaPlayer) return
        
        requestAudioFocus()
        registerNoisyReceiver()
        acquireWifiLock()

        _playbackState.update {
            it.copy(
                isBuffering = false,
                durationMs = mp.duration.toLong().coerceAtLeast(0L)
            )
        }
        applySpeed(_playbackState.value.playbackSpeed)
        
        val oldPlayer = fadingOutPlayer
        if (isCrossfadeEnabled && oldPlayer != null) {
            val oldIsPlaying = try { oldPlayer.isPlaying } catch (_: Exception) { false }
            if (oldIsPlaying) {
                startCrossfade(oldPlayer, mp)
            } else {
                crossfadeJob?.cancel()
                safelyReleasePlayer(oldPlayer)
                fadingOutPlayer = null
                mp.setVolume(1.0f, 1.0f)
            }
        } else {
            crossfadeJob?.cancel()
            safelyReleasePlayer(fadingOutPlayer)
            fadingOutPlayer = null
            mp.setVolume(1.0f, 1.0f)
        }

        if (_isDeckSyncEnabled.value && isWaitingForSyncedDeckStart) {
            isDeck1ReadyForSync = true
            if (isDeck2ReadyForSync) {
                startBothSyncedPlayers()
            }
            // else wait for syncStartJob timeout or Deck 2's onPrepared to call startBothSyncedPlayers()
        } else {
            _playbackState.update { it.copy(isPlaying = true) }
            val wlanSpeaker = wlanSpeakerManager.wlanState.value.selectedDevice
            val wlanMode = wlanSpeakerManager.wlanState.value.playbackMode
            if (wlanSpeaker != null) {
                val curTrack = _playbackState.value.currentTrack
                if (curTrack != null) {
                    wlanSpeakerManager.playTrackOnSpeaker(curTrack, 0L)
                }
                if (wlanMode == WlanPlaybackMode.WLAN_SPEAKER_ONLY) {
                    try { mp.setVolume(0f, 0f) } catch (_: Exception) {}
                }
            }
            mp.start()
            isPreparingNextForCrossfade = false
            startTicker()
        }
    }

    private fun startBothSyncedPlayers() {
        syncStartJob?.cancel()
        isWaitingForSyncedDeckStart = false
        isDeck1ReadyForSync = false
        isDeck2ReadyForSync = false

        try { mediaPlayer?.start() } catch (_: Exception) {}
        try { secondaryMediaPlayer?.start() } catch (_: Exception) {}

        _playbackState.update { it.copy(isPlaying = true, isBuffering = false) }
        _secondaryPlaybackState.update { it.copy(isPlaying = true, isBuffering = false) }
        isPreparingNextForCrossfade = false
        startTicker()
    }

    private fun startCrossfade(oldPlayer: MediaPlayer, newPlayer: MediaPlayer) {
        crossfadeJob?.cancel()
        try {
            newPlayer.setVolume(0f, 0f)
        } catch (_: Exception) {}

        crossfadeJob = scope.launch {
            val steps = 25
            val interval = (crossfadeDurationMs / steps).coerceAtLeast(30L)
            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val outVol = (1f - fraction).coerceIn(0f, 1f)
                val inVol = fraction.coerceIn(0f, 1f)
                
                try {
                    if (oldPlayer.isPlaying) oldPlayer.setVolume(outVol, outVol)
                } catch (_: Exception) {}
                try {
                    if (newPlayer.isPlaying) newPlayer.setVolume(inVol, inVol)
                } catch (_: Exception) {}
                
                delay(interval)
            }
            // Ensure next/incoming track is guaranteed 100% full original volume
            try {
                if (newPlayer.isPlaying) newPlayer.setVolume(1.0f, 1.0f)
            } catch (_: Exception) {}
            safelyReleasePlayer(oldPlayer)
            if (fadingOutPlayer == oldPlayer) fadingOutPlayer = null
        }
    }

    fun playTrackList(tracks: List<NaviromTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        originalQueueList = tracks
        _queue.value = if (_playbackState.value.isShuffle) tracks.shuffled() else tracks
        persistQueueToRoom(_queue.value)
        val safeIndex = startIndex.coerceIn(0, _queue.value.lastIndex)
        _currentIndex.value = safeIndex
        playCurrentTrack()
    }

    fun playTrack(track: NaviromTrack, queueContext: List<NaviromTrack> = listOf(track)) {
        val index = queueContext.indexOfFirst { it.id == track.id }.let { if (it >= 0) it else 0 }
        playTrackList(queueContext, index)
    }

    private fun playCurrentTrack(isCrossfading: Boolean = false) {
        hasCheckedQueueForCurrentTrack = false
        val currentQueue = _queue.value
        val index = _currentIndex.value
        if (index !in currentQueue.indices) return

        val track = currentQueue[index]
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L,
                errorMessage = null
            )
        }

        if (_isDeckSyncEnabled.value) {
            isWaitingForSyncedDeckStart = true
            isDeck1ReadyForSync = false
            isDeck2ReadyForSync = false
            syncStartJob?.cancel()
            syncStartJob = scope.launch {
                delay(650)
                if (isWaitingForSyncedDeckStart) {
                    isWaitingForSyncedDeckStart = false
                    try { mediaPlayer?.start() } catch (_: Exception) {}
                    _playbackState.update { it.copy(isPlaying = true) }
                    startTicker()
                }
            }
            playSecondaryTrack(track, currentQueue)
        }

        acquireWifiLock() // Keep CPU and Wi-Fi awake during async preparation phase

        scope.launch(Dispatchers.Default) {
            val (resolvedLocalUri, resolvedLocalPath) = resolveTrackMediaSource(track)
            val updatedTrack = track.copy(
                localFilePath = resolvedLocalPath ?: track.localFilePath,
                isCached = resolvedLocalUri != null || track.id.startsWith("local_")
            )

            _playbackState.update { it.copy(currentTrack = updatedTrack) }

            try {
                val oldPlayer = mediaPlayer
                val oldIsPlaying = try { oldPlayer?.isPlaying == true } catch (_: Exception) { false }
                if (isCrossfading && oldPlayer != null && oldIsPlaying) {
                    fadingOutPlayer = oldPlayer
                } else {
                    crossfadeJob?.cancel()
                    safelyReleasePlayer(fadingOutPlayer)
                    fadingOutPlayer = null
                    safelyReleasePlayer(oldPlayer)
                }
                
                mediaPlayer = createMediaPlayer()

                if (resolvedLocalUri != null) {
                    mediaPlayer?.setDataSource(context, resolvedLocalUri)
                    mediaPlayer?.prepareAsync()
                } else if (resolvedLocalPath != null) {
                    mediaPlayer?.setDataSource(context, Uri.parse(resolvedLocalPath))
                    mediaPlayer?.prepareAsync()
                } else if (updatedTrack.streamUrl.isNotBlank()) {
                    mediaPlayer?.setDataSource(context, Uri.parse((urlResolver?.invoke(updatedTrack.streamUrl) ?: updatedTrack.streamUrl)))
                    mediaPlayer?.prepareAsync()
                } else {
                    markTrackUnplayable(track.id)
                    skipToNextPlayableOrStop()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing track ${track.title}", e)
                AppDiagnostics.logError(
                    errorCode = DiagnosticCodes.PLAYER_PREPARE_FAIL_302,
                    tag = "AudioPlayer",
                    message = "Failed to prepare track '${track.title}': ${e.message}",
                    throwable = e,
                    contextInfo = "StreamURL: ${track.streamUrl}"
                )
                markTrackUnplayable(track.id)
                skipToNextPlayableOrStop()
                releaseWifiLock()
            }
        }
    }

    fun playSecondaryTrack(track: NaviromTrack, queue: List<NaviromTrack>? = null) {
        val effectiveQueue = when {
            !queue.isNullOrEmpty() -> queue
            secondaryQueue.isNotEmpty() && secondaryQueue.any { it.id == track.id } -> secondaryQueue
            _queue.value.isNotEmpty() && _queue.value.any { it.id == track.id } -> _queue.value
            _queue.value.isNotEmpty() -> listOf(track) + _queue.value.filter { it.id != track.id }
            else -> listOf(track)
        }
        secondaryQueue = effectiveQueue
        val foundIdx = effectiveQueue.indexOfFirst { it.id == track.id }
        secondaryCurrentIndex = if (foundIdx >= 0) foundIdx else 0

        _secondaryPlaybackState.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L,
                errorMessage = null,
                queue = secondaryQueue,
                currentIndex = secondaryCurrentIndex,
                hasNext = secondaryCurrentIndex < secondaryQueue.size - 1 || _playbackState.value.repeatMode == RepeatMode.ALL,
                hasPrevious = secondaryCurrentIndex > 0 || _playbackState.value.repeatMode == RepeatMode.ALL
            )
        }

        acquireWifiLock()

        scope.launch(Dispatchers.Default) {
            val (resolvedLocalUri, resolvedLocalPath) = resolveTrackMediaSource(track)
            val updatedTrack = track.copy(
                localFilePath = resolvedLocalPath ?: track.localFilePath,
                isCached = resolvedLocalUri != null || track.id.startsWith("local_")
            )
            _secondaryPlaybackState.update { it.copy(currentTrack = updatedTrack) }

            try {
                safelyReleasePlayer(secondaryMediaPlayer)
                secondaryMediaPlayer = createSecondaryMediaPlayer()
                val mp = secondaryMediaPlayer ?: return@launch

                if (resolvedLocalUri != null) {
                    mp.setDataSource(context, resolvedLocalUri)
                    mp.prepareAsync()
                } else if (resolvedLocalPath != null) {
                    mp.setDataSource(context, Uri.parse(resolvedLocalPath))
                    mp.prepareAsync()
                } else if (updatedTrack.streamUrl.isNotBlank()) {
                    val stream = urlResolver?.invoke(updatedTrack.streamUrl) ?: updatedTrack.streamUrl
                    mp.setDataSource(context, Uri.parse(stream))
                    mp.prepareAsync()
                } else {
                    _secondaryPlaybackState.update {
                        it.copy(isBuffering = false, isPlaying = false, errorMessage = "Cannot resolve audio source")
                    }
                    if (!_isDeckSyncEnabled.value && secondaryCurrentIndex + 1 < secondaryQueue.size) {
                        playSecondaryNext()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing secondary track ${track.title}", e)
                _secondaryPlaybackState.update {
                    it.copy(isBuffering = false, isPlaying = false, errorMessage = e.message)
                }
                if (!_isDeckSyncEnabled.value && secondaryCurrentIndex + 1 < secondaryQueue.size) {
                    delay(300L)
                    playSecondaryNext()
                }
            }
        }
    }

    fun playSecondaryNext() {
        if (_isDeckSyncEnabled.value) {
            next()
            return
        }
        if (secondaryQueue.isNotEmpty()) {
            val nextIdx = secondaryCurrentIndex + 1
            if (nextIdx < secondaryQueue.size) {
                val nextTrack = secondaryQueue[nextIdx]
                playSecondaryTrack(nextTrack, secondaryQueue)
            } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
                val nextTrack = secondaryQueue[0]
                playSecondaryTrack(nextTrack, secondaryQueue)
            }
        }
    }

    fun playSecondaryPrevious() {
        if (_isDeckSyncEnabled.value) {
            previous()
            return
        }
        if (secondaryQueue.isNotEmpty()) {
            val prevIdx = secondaryCurrentIndex - 1
            if (prevIdx >= 0) {
                val prevTrack = secondaryQueue[prevIdx]
                playSecondaryTrack(prevTrack, secondaryQueue)
            } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
                val prevTrack = secondaryQueue[secondaryQueue.lastIndex]
                playSecondaryTrack(prevTrack, secondaryQueue)
            }
        }
    }

    fun toggleSecondaryPlayPause() {
        if (_isDeckSyncEnabled.value) {
            togglePlayPause()
            return
        }
        if (_secondaryPlaybackState.value.isPlaying) {
            pauseSecondary()
        } else {
            if (_secondaryPlaybackState.value.currentTrack != null) {
                playSecondary()
            }
        }
    }

    fun pauseSecondary() {
        secondaryMediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                }
            } catch (_: Exception) {}
            _secondaryPlaybackState.update { state -> state.copy(isPlaying = false) }
        }
    }

    fun playSecondary() {
        val mp = secondaryMediaPlayer
        if (mp != null) {
            try {
                mp.start()
                _secondaryPlaybackState.update { state -> state.copy(isPlaying = true) }
                startTicker()
            } catch (_: Exception) {}
        } else {
            val current = _secondaryPlaybackState.value.currentTrack
            if (current != null) {
                playSecondaryTrack(current)
            }
        }
    }

    fun seekSecondaryTo(positionMs: Long, syncPrimary: Boolean = true) {
        val mp = secondaryMediaPlayer ?: return
        try {
            val duration = _secondaryPlaybackState.value.durationMs.coerceAtLeast(0L)
            val safePos = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            isSecondarySeeking = true
            _secondaryPlaybackState.update { it.copy(currentPositionMs = safePos) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(safePos, MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(safePos.toInt())
            }
        } catch (_: Exception) {
            isSecondarySeeking = false
        }
        if (syncPrimary && _isDeckSyncEnabled.value && !isSeeking) {
            seekTo(positionMs)
        }
    }

    private fun applySecondarySpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (kotlin.math.abs(currentSecondaryAppliedSpeed - speed) > 0.005f) {
                    currentSecondaryAppliedSpeed = speed
                    secondaryMediaPlayer?.let { mp ->
                        val params = mp.playbackParams ?: PlaybackParams()
                        params.speed = speed
                        mp.playbackParams = params
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to set secondary playback speed", e)
            }
        }
    }

    fun seekSecondaryRelative(offsetMs: Long) {
        val current = _secondaryPlaybackState.value.currentPositionMs
        seekSecondaryTo(current + offsetMs)
    }

    fun setSecondaryVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _secondaryPlaybackState.update { it.copy(volume = clamped) }
        try {
            secondaryMediaPlayer?.setVolume(clamped, clamped)
        } catch (_: Exception) {}
    }

    fun stopSecondaryTrack() {
        isWaitingForSyncedDeckStart = false
        isDeck2ReadyForSync = false
        syncStartJob?.cancel()
        try {
            secondaryMediaPlayer?.stop()
            safelyReleasePlayer(secondaryMediaPlayer)
            secondaryMediaPlayer = null
        } catch (_: Exception) {}
        secondaryQueue = emptyList()
        secondaryCurrentIndex = -1
        _secondaryPlaybackState.update { SecondaryPlaybackState() }
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            if (_playbackState.value.currentTrack != null) {
                resume()
            } else if (_queue.value.isNotEmpty()) {
                _currentIndex.value = 0
                playCurrentTrack()
            }
        }
    }

    fun pause() {
        unregisterNoisyReceiver()
        releaseWifiLock()
        wlanSpeakerManager.pause()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                }
            } catch (_: Exception) {}
            _playbackState.update { state -> state.copy(isPlaying = false) }
            stopTicker()
        }
        if (_isDeckSyncEnabled.value) {
            pauseSecondary()
        }
    }

    fun resume() {
        if (_isDeckSyncEnabled.value) {
            val p1Pos = try { mediaPlayer?.currentPosition?.toLong() ?: _playbackState.value.currentPositionMs } catch (_: Exception) { _playbackState.value.currentPositionMs }
            val p2Pos = try { secondaryMediaPlayer?.currentPosition?.toLong() ?: _secondaryPlaybackState.value.currentPositionMs } catch (_: Exception) { _secondaryPlaybackState.value.currentPositionMs }
            if (kotlin.math.abs(p1Pos - p2Pos) > 25L) {
                seekSecondaryTo(p1Pos, syncPrimary = false)
            }
        }
        wlanSpeakerManager.resume()
        mediaPlayer?.let {
            if (requestAudioFocus()) {
                registerNoisyReceiver()
                acquireWifiLock()
                try {
                    it.start()
                } catch (_: Exception) {}
                _playbackState.update { state -> state.copy(isPlaying = true) }
                startTicker()
            }
        }
        if (_isDeckSyncEnabled.value) {
            playSecondary()
        }
    }

    fun seekTo(positionMs: Long) {
        wlanSpeakerManager.seekTo(positionMs)
        mediaPlayer?.let { mp ->
            val duration = _playbackState.value.durationMs.coerceAtLeast(0L)
            val safePos = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            targetSeekPositionMs = safePos
            lastSeekTime = System.currentTimeMillis()
            
            // Immediate state update ensures lyrics preview and slider reflect seek immediately without flickering
            _playbackState.update { it.copy(currentPositionMs = safePos) }
            NaviromPlaybackService.notifySeek(context, safePos)
            
            if (isSeeking) {
                // Queue the latest seek position so MediaPlayer processes it when the current seek completes
                pendingSeekPos = safePos
            } else {
                isSeeking = true
                pendingSeekPos = -1L
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(safePos, MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(safePos.toInt())
                    }
                } catch (_: Exception) {
                    isSeeking = false
                }
            }
            
            // If user seeks during crossfade or away from fade zone, cancel crossfade and restore full volume
            if (fadingOutPlayer != null) {
                crossfadeJob?.cancel()
                safelyReleasePlayer(fadingOutPlayer)
                fadingOutPlayer = null
                try { mp.setVolume(1.0f, 1.0f) } catch (_: Exception) {}
            }
        }
        if (_isDeckSyncEnabled.value) {
            seekSecondaryTo(positionMs, syncPrimary = false)
        }
    }

    fun seekRelative(offsetMs: Long) {
        val now = System.currentTimeMillis()
        val basePos = if (isSeeking || (now - lastSeekTime < 500L)) {
            targetSeekPositionMs
        } else {
            _playbackState.value.currentPositionMs
        }
        seekTo(basePos + offsetMs)
    }

    fun next(isCrossfading: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastTrackSwitchTime < 250L) return
        lastTrackSwitchTime = now

        val q = _queue.value
        if (q.isEmpty()) return

        // If automated crossfading with RepeatMode.ONE, loop the current track if playable
        if (isCrossfading && _playbackState.value.repeatMode == RepeatMode.ONE) {
            val currentTrack = _playbackState.value.currentTrack
            if (currentTrack != null && !_playbackState.value.unplayableTrackIds.contains(currentTrack.id)) {
                seekTo(0)
                resume()
                return
            }
        }

        // When skipping, look for the next playable track in the queue
        val nextIdx = findNextPlayableIndex(_currentIndex.value + 1)
            ?: if (_playbackState.value.repeatMode == RepeatMode.ALL) findNextPlayableIndex(0) else null

        if (nextIdx != null) {
            _currentIndex.value = nextIdx
            playCurrentTrack(isCrossfading)
        } else {
            // Reached the end of the queue. If user explicitly pressed next, wrap around if multiple tracks exist
            val wrapIdx = findNextPlayableIndex(0)
            if (wrapIdx != null && wrapIdx != _currentIndex.value) {
                _currentIndex.value = wrapIdx
                playCurrentTrack(isCrossfading)
            } else {
                pause()
                seekTo(0)
            }
        }
    }

    fun previous() {
        val now = System.currentTimeMillis()
        if (now - lastTrackSwitchTime < 250L) return
        lastTrackSwitchTime = now

        val doublePressThresholdMs = 1800L
        val isDoublePress = (now - lastPreviousPressTime) < doublePressThresholdMs
        lastPreviousPressTime = now

        // If more than 3 seconds in and not a quick double-press, seek to start of current track
        if (!isDoublePress && _playbackState.value.currentPositionMs > 3000L) {
            seekTo(0)
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return
        val currentIdx = _currentIndex.value

        if (currentIdx > 0) {
            _currentIndex.value = currentIdx - 1
            playCurrentTrack()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            _currentIndex.value = q.lastIndex
            playCurrentTrack()
        } else {
            seekTo(0)
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        _playbackState.update { it.copy(isShuffle = newShuffle) }

        val currentTrack = _playbackState.value.currentTrack
        if (newShuffle) {
            val shuffled = originalQueueList.shuffled().toMutableList()
            if (currentTrack != null) {
                shuffled.remove(currentTrack)
                shuffled.add(0, currentTrack)
            }
            _queue.value = shuffled
            _currentIndex.value = 0
        } else {
            _queue.value = originalQueueList
            _currentIndex.value = originalQueueList.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.update { it.copy(playbackSpeed = speed) }
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying || _playbackState.value.isPlaying) {
                        val params = mp.playbackParams ?: PlaybackParams()
                        params.speed = speed
                        mp.playbackParams = params
                    }
                }
                if (_isDeckSyncEnabled.value) {
                    secondaryMediaPlayer?.let { mp ->
                        if (mp.isPlaying || _secondaryPlaybackState.value.isPlaying) {
                            val params = mp.playbackParams ?: PlaybackParams()
                            params.speed = speed
                            mp.playbackParams = params
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to set playback speed", e)
            }
        }
    }

    fun updateSleepTimerOptions(options: SleepTimerOptions) {
        currentSleepTimerOptions = options
        _playbackState.update { it.copy(sleepTimerOptions = options) }
    }

    fun setSleepTimer(minutes: Int?, options: SleepTimerOptions = currentSleepTimerOptions) {
        sleepTimerJob?.cancel()
        currentSleepTimerOptions = options
        val totalSecs = if (minutes != null && minutes > 0) minutes * 60 else null
        _playbackState.update { 
            it.copy(
                sleepTimerMinutesLeft = minutes, 
                sleepTimerSecondsLeft = totalSecs,
                sleepTimerOptions = options
            ) 
        }

        if (minutes == null || minutes <= 0) return

        sleepTimerJob = scope.launch {
            var remainingSecs = minutes * 60
            while (remainingSecs > 0) {
                delay(1000L)
                remainingSecs--
                val minsLeft = (remainingSecs + 59) / 60
                _playbackState.update {
                    it.copy(
                        sleepTimerMinutesLeft = minsLeft,
                        sleepTimerSecondsLeft = remainingSecs
                    )
                }
            }
            pause()
            _playbackState.update { it.copy(sleepTimerMinutesLeft = null, sleepTimerSecondsLeft = null) }

            // Execute requested connection shutoffs (Bluetooth, Wi-Fi, Mobile Data)
            deviceConnectionManager.executeSleepTimerActions(currentSleepTimerOptions)
        }
    }

    fun addToQueue(track: NaviromTrack) {
        _queue.update { it + track }
        originalQueueList = originalQueueList + track
    }

    fun addToQueueBeginning(track: NaviromTrack) {
        val curList = _queue.value.toMutableList()
        if (curList.isEmpty()) {
            playTrack(track)
            return
        }
        curList.add(0, track)
        _queue.value = curList
        originalQueueList = listOf(track) + originalQueueList
        if (_currentIndex.value >= 0) {
            _currentIndex.value = _currentIndex.value + 1
        }
    }

    fun addToQueueEnd(track: NaviromTrack) {
        addToQueue(track)
    }

    fun playNext(track: NaviromTrack) {
        val curList = _queue.value.toMutableList()
        val insertIndex = (_currentIndex.value + 1).coerceIn(0, curList.size)
        curList.add(insertIndex, track)
        _queue.value = curList
    }

    fun removeFromQueue(index: Int) {
        val curList = _queue.value.toMutableList()
        if (index in curList.indices) {
            val removingCurrent = (index == _currentIndex.value)
            curList.removeAt(index)
            _queue.value = curList
            if (removingCurrent) {
                if (curList.isNotEmpty()) {
                    _currentIndex.value = index.coerceIn(0, curList.lastIndex)
                    playCurrentTrack()
                } else {
                    _currentIndex.value = -1
                    pause()
                    _playbackState.update { it.copy(currentTrack = null) }
                }
            } else if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }

    fun clearQueue() {
        pause()
        _queue.value = emptyList()
        originalQueueList = emptyList()
        _currentIndex.value = -1
        _playbackState.update {
            it.copy(
                currentTrack = null,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
        }
    }

    private fun handleTrackCompletion() {
        val mode = _playbackState.value.repeatMode
        when (mode) {
            RepeatMode.ONE -> {
                val currentTrack = _playbackState.value.currentTrack
                if (currentTrack != null && _playbackState.value.unplayableTrackIds.contains(currentTrack.id)) {
                    skipToNextPlayableOrStop()
                } else {
                    seekTo(0)
                    mediaPlayer?.start()
                    _playbackState.update { it.copy(isPlaying = true) }
                }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                val nextIdx = findNextPlayableIndex(_currentIndex.value + 1)
                if (nextIdx != null) {
                    _currentIndex.value = nextIdx
                    playCurrentTrack()
                } else {
                    pause()
                    _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                    stopTicker()
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    try {
                        val isPlay = try { mp.isPlaying } catch (_: Exception) { false }
                        if (isPlay) {
                            val now = System.currentTimeMillis()
                            val isStabilizingSeek = isSeeking || (now - lastSeekTime < 600L)
                            val pos = if (isStabilizingSeek) targetSeekPositionMs else mp.currentPosition.toLong()
                            val dur = mp.duration.toLong().coerceAtLeast(0L)
                            _playbackState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    durationMs = if (dur > 0) dur else it.durationMs
                                )
                            }

                            // Pre-check queued tracks around mid-song (50% position)
                            if (dur > 0L && pos >= (dur / 2L) && !hasCheckedQueueForCurrentTrack) {
                                hasCheckedQueueForCurrentTrack = true
                                triggerMidSongQueueCheck()
                            }
                            
                            if (isCrossfadeEnabled && dur > 0 && dur - pos <= crossfadeDurationMs && !isPreparingNextForCrossfade) {
                                val currentIdx = _currentIndex.value
                                val q = _queue.value
                                val mode = _playbackState.value.repeatMode
                                if (mode != RepeatMode.ONE) {
                                    if (mode == RepeatMode.ALL || currentIdx + 1 < q.size) {
                                        isPreparingNextForCrossfade = true
                                        next(isCrossfading = true)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore state changes
                    }
                }

                secondaryMediaPlayer?.let { mp ->
                    try {
                        val isPlay = try { mp.isPlaying } catch (_: Exception) { false }
                        if (isPlay) {
                            val pos = mp.currentPosition.toLong()
                            val dur = mp.duration.toLong().coerceAtLeast(0L)
                            _secondaryPlaybackState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    durationMs = if (dur > 0) dur else it.durationMs
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // ignore state changes
                    }
                }

                // Drift Synchronization Controller
                val isSameTrack = _playbackState.value.currentTrack?.id != null &&
                        _playbackState.value.currentTrack?.id == _secondaryPlaybackState.value.currentTrack?.id
                val isSyncActive = _isDeckSyncEnabled.value || isSameTrack

                val p1Playing = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
                val p2Playing = try { secondaryMediaPlayer?.isPlaying == true } catch (_: Exception) { false }

                if (isSyncActive && !isSeeking && !isSecondarySeeking && !isWaitingForSyncedDeckStart && p1Playing && p2Playing) {
                    val p1Pos = try { mediaPlayer?.currentPosition?.toLong() } catch (_: Exception) { null }
                    val p2Pos = try { secondaryMediaPlayer?.currentPosition?.toLong() } catch (_: Exception) { null }

                    if (p1Pos != null && p2Pos != null) {
                        val diff = p1Pos - p2Pos // Positive: Deck 1 ahead (Deck 2 lagging). Negative: Deck 2 ahead.
                        val absDiff = kotlin.math.abs(diff)
                        val baseSpeed = _playbackState.value.playbackSpeed
                        val now = System.currentTimeMillis()

                        if (absDiff > 350L && (now - lastSyncSeekTime > 800L)) {
                            // Large desync (e.g. after buffer underrun or jump): perform a clean seekTo
                            lastSyncSeekTime = now
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    secondaryMediaPlayer?.seekTo(p1Pos, MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    secondaryMediaPlayer?.seekTo(p1Pos.toInt())
                                }
                            } catch (_: Exception) {}
                            applySecondarySpeed(baseSpeed)
                        } else if (absDiff > 12L) {
                            // Fine-grained smooth phase adjustment via playback speed (zero audio glitching)
                            val speedAdj = when {
                                diff > 150L -> 1.08f   // Deck 2 lagging significantly: +8%
                                diff > 75L  -> 1.05f   // Deck 2 lagging moderately: +5%
                                diff > 30L  -> 1.03f   // Deck 2 lagging slightly: +3%
                                diff > 12L  -> 1.015f  // Deck 2 lagging by a hair: +1.5%
                                diff < -150L -> 0.92f  // Deck 2 leading significantly: -8%
                                diff < -75L  -> 0.95f  // Deck 2 leading moderately: -5%
                                diff < -30L  -> 0.97f  // Deck 2 leading slightly: -3%
                                diff < -12L  -> 0.985f // Deck 2 leading by a hair: -1.5%
                                else -> 1.0f
                            }
                            applySecondarySpeed(baseSpeed * speedAdj)
                        } else {
                            // Locked in sync (within 12ms)!
                            if (kotlin.math.abs(currentSecondaryAppliedSpeed - baseSpeed) > 0.001f) {
                                applySecondarySpeed(baseSpeed)
                            }
                        }
                    }
                }

                val tickInterval = if (isSyncActive && p1Playing && p2Playing) 60L else 250L
                delay(tickInterval)
            }
        }
    }

    private fun stopTicker() {
        val p1Playing = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
        val p2Playing = try { secondaryMediaPlayer?.isPlaying == true } catch (_: Exception) { false }
        if (!p1Playing && !p2Playing) {
            tickerJob?.cancel()
        }
    }

    fun release() {
        tickerJob?.cancel()
        sleepTimerJob?.cancel()
        crossfadeJob?.cancel()
        unregisterNoisyReceiver()
        releaseWifiLock()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback as android.media.AudioDeviceCallback)
            } catch (_: Exception) {}
            audioDeviceCallback = null
        }
        safelyReleasePlayer(mediaPlayer)
        mediaPlayer = null
        safelyReleasePlayer(secondaryMediaPlayer)
        secondaryMediaPlayer = null
        safelyReleasePlayer(fadingOutPlayer)
        fadingOutPlayer = null
        wlanSpeakerManager.release()
    }
}
