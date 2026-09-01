import re

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "r") as f:
    content = f.read()

# Add fields
content = content.replace("private var mediaPlayer: MediaPlayer? = null", """private var mediaPlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var crossfadeJob: Job? = null

    var isCrossfadeEnabled: Boolean = false
    var crossfadeDurationMs: Long = 5000L""")

content = content.replace("private var sleepTimerJob: Job? = null", """private var sleepTimerJob: Job? = null
    private var isPreparingNextForCrossfade = false""")

# Replace initMediaPlayer
init_replacement = """private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = createMediaPlayer()
    }

    private fun createMediaPlayer(): MediaPlayer {
        return MediaPlayer().apply {
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
                    mp.release()
                }
            }
            setOnErrorListener { mp, what, extra ->
                if (mp == mediaPlayer) {
                    Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playback error ($what, $extra)"
                        )
                    }
                }
                true
            }
        }
    }

    private fun handlePrepared(mp: MediaPlayer) {
        if (mp != mediaPlayer) return
        
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
            try {
                oldPlayer.stop()
                oldPlayer.release()
            } catch (e: Exception) {}
            if (fadingOutPlayer == oldPlayer) fadingOutPlayer = null
        }
    }"""

content = re.sub(r'private fun initMediaPlayer\(\) \{.*?(?=fun playTrackList)', init_replacement + "\n\n    ", content, flags=re.DOTALL)

# Modify playCurrentTrack signature and reset logic
content = content.replace("private fun playCurrentTrack() {", "private fun playCurrentTrack(isCrossfading: Boolean = false) {")

reset_replacement = """val oldPlayer = mediaPlayer
            if (isCrossfading && oldPlayer != null && oldPlayer.isPlaying) {
                fadingOutPlayer = oldPlayer
            } else {
                crossfadeJob?.cancel()
                fadingOutPlayer?.release()
                fadingOutPlayer = null
                oldPlayer?.release()
            }
            
            mediaPlayer = createMediaPlayer()"""
content = content.replace("mediaPlayer?.reset()", reset_replacement)

# Modify next signature and calls
content = content.replace("fun next() {", "fun next(isCrossfading: Boolean = false) {")
content = content.replace("playCurrentTrack()\n            }\n            RepeatMode.OFF", "playCurrentTrack(isCrossfading)\n            }\n            RepeatMode.OFF")
content = content.replace("playCurrentTrack()\n                } else {", "playCurrentTrack(isCrossfading)\n                } else {")

# Ticker logic
ticker_logic = """_playbackState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    durationMs = if (dur > 0) dur else it.durationMs
                                )
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
                            }"""
content = content.replace("""_playbackState.update {
                                it.copy(
                                    currentPositionMs = pos,
                                    durationMs = if (dur > 0) dur else it.durationMs
                                )
                            }""", ticker_logic)

# Release
release_logic = """stopTicker()
        sleepTimerJob?.cancel()
        crossfadeJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        fadingOutPlayer?.release()
        fadingOutPlayer = null"""
content = content.replace("""stopTicker()
        sleepTimerJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null""", release_logic)

with open("app/src/main/java/com/example/navirom/player/AudioPlayerController.kt", "w") as f:
    f.write(content)
