package com.example.navirom.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
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
import com.example.navirom.data.cache.OfflineDownloadManager
import com.example.navirom.data.local.CachedTrackDao
import com.example.navirom.data.local.PlaybackQueueDao
import com.example.navirom.data.local.PlaybackQueueEntity
import com.example.navirom.data.model.NaviromTrack
import com.example.navirom.data.model.PlaybackState
import com.example.navirom.data.model.RepeatMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

import com.example.navirom.diagnostics.AppDiagnostics
import com.example.navirom.diagnostics.DiagnosticCodes

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
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Navirom:WifiLock")?.apply {
            setReferenceCounted(false)
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
                // Keep the CPU awake for up to 10 minutes at a time per track
                wakeLock?.acquire(10 * 60 * 1000L)
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

    private val _queue = MutableStateFlow<List<NaviromTrack>>(emptyList())
    val queue: StateFlow<List<NaviromTrack>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private var originalQueueList: List<NaviromTrack> = emptyList()

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var isPreparingNextForCrossfade = false
    private var hasCheckedQueueForCurrentTrack = false
    private var midSongCheckJob: Job? = null

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
                checkQueueRecursively(currentQueue, currentIdx + 1)
            }
        }
    }

    private suspend fun checkQueueRecursively(queueList: List<NaviromTrack>, index: Int) {
        if (index !in queueList.indices) return

        val track = queueList[index]
        if (_playbackState.value.unplayableTrackIds.contains(track.id)) {
            checkQueueRecursively(queueList, index + 1)
            return
        }

        val playable = isTrackPlayable(track)
        if (!playable) {
            Log.w("AudioPlayerController", "Mid-song check: track '${track.title}' (${track.id}) is unplayable. Highlighting red.")
            markTrackUnplayable(track.id)
            checkQueueRecursively(queueList, index + 1)
        } else {
            Log.d("AudioPlayerController", "Mid-song check: track '${track.title}' (${track.id}) is verified playable.")
        }
    }

    private suspend fun isTrackPlayable(track: NaviromTrack): Boolean {
        if (track.id.isBlank()) return false
        if (_playbackState.value.unplayableTrackIds.contains(track.id)) return false

        // 1. Check local/cached files
        val cachedEntity = try { cachedTrackDao.getCachedTrack(track.id) } catch (_: Exception) { null }
        val cachedFilePath = cachedEntity?.localFilePath
        if (!cachedFilePath.isNullOrBlank() && File(cachedFilePath).exists()) return true

        val downloadFile = downloadManager.getLocalFileForTrack(track.id)
        if (downloadFile.exists() && downloadFile.length() > 0) return true

        if (!track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists()) return true

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

        // 3. Network URL probe
        return try {
            val connection = (java.net.URL(resolvedUrl).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-100")
                connectTimeout = 3500
                readTimeout = 3500
                instanceFollowRedirects = true
            }
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..299 || responseCode == 206
        } catch (e: Exception) {
            Log.w("AudioPlayerController", "Track '${track.title}' failed playability check: ${e.message}")
            false
        }
    }

    init {
        NaviromPlaybackService.activePlayerController = java.lang.ref.WeakReference(this)
        initMediaPlayer()
        restoreQueueFromRoom()
        scope.launch {
            _playbackState.collect { state ->
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

    private fun handlePrepared(mp: MediaPlayer) {
        if (mp != mediaPlayer) return
        
        requestAudioFocus()
        registerNoisyReceiver()
        acquireWifiLock()

        _playbackState.update {
            it.copy(
                isBuffering = false,
                isPlaying = true,
                durationMs = mp.duration.toLong().coerceAtLeast(0L)
            )
        }
        applySpeed(_playbackState.value.playbackSpeed)
        
        if (isCrossfadeEnabled && fadingOutPlayer != null) {
            startCrossfade(fadingOutPlayer!!, mp)
        } else {
            mp.setVolume(1f, 1f)
        }
        mp.start()
        isPreparingNextForCrossfade = false
        startTicker()
    }

    private fun startCrossfade(oldPlayer: MediaPlayer, newPlayer: MediaPlayer) {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            val steps = 20
            val interval = crossfadeDurationMs / steps
            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val outVol = 1f - fraction
                val inVol = fraction
                
                try {
                    if (oldPlayer.isPlaying) oldPlayer.setVolume(outVol, outVol)
                    if (newPlayer.isPlaying) newPlayer.setVolume(inVol, inVol)
                } catch (e: Exception) {}
                
                delay(interval)
            }
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

        acquireWifiLock() // Keep CPU and Wi-Fi awake during async preparation phase

        scope.launch(Dispatchers.Default) {
            // Check Room CachedTrackDao for persistent offline track metadata and cache path
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

            val resolvedLocalPath = when {
                isCachedFileValid -> cachedFilePath
                isDownloadFileValid -> downloadFile.absolutePath
                !track.localFilePath.isNullOrBlank() && File(track.localFilePath).exists() -> track.localFilePath
                else -> null
            }

            val updatedTrack = track.copy(
                localFilePath = resolvedLocalPath,
                isCached = resolvedLocalPath != null
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

                if (resolvedLocalPath != null) {
                    mediaPlayer?.setDataSource(resolvedLocalPath)
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
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                }
            } catch (_: Exception) {}
            _playbackState.update { state -> state.copy(isPlaying = false) }
            stopTicker()
        }
    }

    fun resume() {
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
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            val safePos = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
            mp.seekTo(safePos.toInt())
            _playbackState.update { it.copy(currentPositionMs = safePos) }
        }
    }

    fun seekRelative(offsetMs: Long) {
        val current = _playbackState.value.currentPositionMs
        seekTo(current + offsetMs)
    }

    fun next(isCrossfading: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastTrackSwitchTime < 250L) return
        lastTrackSwitchTime = now

        val q = _queue.value
        if (q.isEmpty()) return

        when (_playbackState.value.repeatMode) {
            RepeatMode.ONE -> {
                val currentTrack = _playbackState.value.currentTrack
                if (currentTrack != null && _playbackState.value.unplayableTrackIds.contains(currentTrack.id)) {
                    skipToNextPlayableOrStop()
                } else {
                    seekTo(0)
                    resume()
                }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                val nextIdx = findNextPlayableIndex(_currentIndex.value + 1)
                if (nextIdx != null) {
                    _currentIndex.value = nextIdx
                    playCurrentTrack(isCrossfading)
                } else {
                    pause()
                    seekTo(0)
                }
            }
        }
    }

    fun previous() {
        val now = System.currentTimeMillis()
        if (now - lastTrackSwitchTime < 250L) return
        lastTrackSwitchTime = now

        if (_playbackState.value.currentPositionMs > 3000L) {
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
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Failed to set playback speed", e)
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _playbackState.update { it.copy(sleepTimerMinutesLeft = minutes) }

        if (minutes == null || minutes <= 0) return

        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _playbackState.update { it.copy(sleepTimerMinutesLeft = remaining) }
            }
            pause()
            _playbackState.update { it.copy(sleepTimerMinutesLeft = null) }
        }
    }

    fun addToQueue(track: NaviromTrack) {
        _queue.update { it + track }
        originalQueueList = originalQueueList + track
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
                            val pos = mp.currentPosition.toLong()
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
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
    }

    fun release() {
        stopTicker()
        sleepTimerJob?.cancel()
        crossfadeJob?.cancel()
        unregisterNoisyReceiver()
        releaseWifiLock()
        abandonAudioFocus()
        safelyReleasePlayer(mediaPlayer)
        mediaPlayer = null
        safelyReleasePlayer(fadingOutPlayer)
        fadingOutPlayer = null
    }
}
