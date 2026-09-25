package com.labix.navirom.player.wlan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.labix.navirom.data.cache.OfflineDownloadManager
import com.labix.navirom.data.model.NaviromTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WlanSpeakerManager(
    private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val urlResolverProvider: () -> ((String) -> String)?,
    private val currentTrackProvider: () -> NaviromTrack?,
    private val currentQueueProvider: () -> List<NaviromTrack>,
    private val onTrackEndedOnSpeaker: () -> Unit
) {
    companion object {
        private const val TAG = "WlanSpeakerManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val upnpClient = WlanUpnpClient()

    val audioServer = WlanAudioServer(
        context = context,
        downloadManager = downloadManager,
        urlResolverProvider = urlResolverProvider,
        currentTrackProvider = currentTrackProvider,
        currentQueueProvider = currentQueueProvider
    )

    private val _wlanState = MutableStateFlow(WlanSpeakerState())
    val wlanState: StateFlow<WlanSpeakerState> = _wlanState.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var pollingJob: Job? = null
    private var scanJob: Job? = null

    init {
        updateServerUrls()
    }

    private fun acquireWifiLocks() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                if (multicastLock == null) {
                    multicastLock = wm.createMulticastLock("NaviromMulticastLock").apply {
                        setReferenceCounted(true)
                    }
                }
                if (multicastLock?.isHeld == false) {
                    multicastLock?.acquire()
                }

                if (wifiLock == null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm.createWifiLock(mode, "NaviromWlanSpeakerWifiLock").apply {
                        setReferenceCounted(false)
                    }
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wifi/multicast locks: ${e.message}")
        }
    }

    private fun releaseWifiLocks() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun updateServerUrls() {
        val ip = audioServer.getLocalIpAddress()
        val port = audioServer.actualPort
        val curTrack = currentTrackProvider()
        val streamUrl = audioServer.getCurrentTrackStreamUrl()
        val trackUrl = curTrack?.let { audioServer.getTrackStreamUrl(it.id) } ?: ""

        _wlanState.update {
            it.copy(
                localServerIp = ip,
                localServerPort = port,
                directStreamUrl = streamUrl,
                directTrackUrl = trackUrl
            )
        }
    }

    fun startDiscovery() {
        scanJob?.cancel()
        _wlanState.update { it.copy(isScanning = true, lastError = null) }
        acquireWifiLocks()

        scanJob = scope.launch {
            try {
                audioServer.start()
                updateServerUrls()

                val devices = upnpClient.discoverRenderers(timeoutMs = 4500L)
                val currentSelectedId = _wlanState.value.selectedDevice?.id

                val updatedList = devices.map { dev ->
                    if (dev.id == currentSelectedId) {
                        dev.copy(isConnected = true)
                    } else {
                        dev
                    }
                }

                _wlanState.update { state ->
                    state.copy(
                        discoveredDevices = updatedList,
                        isScanning = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error discovering WLAN speakers: ${e.message}")
                _wlanState.update { it.copy(isScanning = false, lastError = e.message) }
            }
        }
    }

    fun connectToDevice(device: WlanSpeakerDevice) {
        scope.launch {
            _wlanState.update {
                it.copy(
                    selectedDevice = device.copy(isConnected = true),
                    connectionStatus = WlanConnectionStatus.CONNECTING,
                    lastError = null
                )
            }

            audioServer.start()
            updateServerUrls()
            acquireWifiLocks()

            // Query initial volume
            val renderingUrl = device.renderingControlUrl
            if (!renderingUrl.isNullOrEmpty()) {
                val vol = upnpClient.getVolume(renderingUrl)
                if (vol != null) {
                    _wlanState.update { it.copy(speakerVolume = vol / 100f) }
                }
            }

            _wlanState.update {
                it.copy(
                    connectionStatus = WlanConnectionStatus.CONNECTED,
                    playbackMode = if (it.playbackMode == WlanPlaybackMode.LOCAL_ONLY) WlanPlaybackMode.WLAN_SPEAKER_ONLY else it.playbackMode
                )
            }

            // If a track is already active, start streaming to the speaker
            val curTrack = currentTrackProvider()
            if (curTrack != null) {
                playTrackOnSpeaker(curTrack, 0L)
            }
        }
    }

    fun disconnect() {
        val selected = _wlanState.value.selectedDevice
        scope.launch {
            pollingJob?.cancel()
            pollingJob = null

            if (selected?.avTransportControlUrl != null) {
                try {
                    upnpClient.stop(selected.avTransportControlUrl)
                } catch (_: Exception) {}
            }

            _wlanState.update {
                it.copy(
                    selectedDevice = null,
                    connectionStatus = WlanConnectionStatus.DISCONNECTED,
                    playbackMode = WlanPlaybackMode.LOCAL_ONLY,
                    isStreaming = false,
                    currentTransportState = "STOPPED"
                )
            }
            releaseWifiLocks()
        }
    }

    fun setPlaybackMode(mode: WlanPlaybackMode) {
        _wlanState.update { it.copy(playbackMode = mode) }
    }

    fun playTrackOnSpeaker(track: NaviromTrack, startPositionMs: Long = 0L) {
        val device = _wlanState.value.selectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return

        scope.launch {
            audioServer.start()
            updateServerUrls()
            val streamUrl = audioServer.getTrackStreamUrl(track.id)

            _wlanState.update {
                it.copy(
                    currentTrack = track,
                    currentPositionMs = startPositionMs,
                    durationMs = track.durationSeconds * 1000L,
                    isStreaming = true,
                    connectionStatus = WlanConnectionStatus.STREAMING,
                    currentTransportState = "TRANSITIONING"
                )
            }

            val setUriOk = upnpClient.setAvTransportUri(controlUrl, streamUrl, track)
            if (setUriOk) {
                if (startPositionMs > 1000L) {
                    upnpClient.seek(controlUrl, startPositionMs)
                }
                val playOk = upnpClient.play(controlUrl)
                if (playOk) {
                    _wlanState.update { it.copy(currentTransportState = "PLAYING") }
                    startPollingPosition(controlUrl)
                } else {
                    _wlanState.update { it.copy(lastError = "Speaker rejected Play command") }
                }
            } else {
                _wlanState.update { it.copy(lastError = "Failed to set audio URL on speaker") }
            }
        }
    }

    fun resume() {
        val device = _wlanState.value.selectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return
        scope.launch {
            val ok = upnpClient.play(controlUrl)
            if (ok) {
                _wlanState.update { it.copy(currentTransportState = "PLAYING") }
                startPollingPosition(controlUrl)
            }
        }
    }

    fun pause() {
        val device = _wlanState.value.selectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return
        scope.launch {
            val ok = upnpClient.pause(controlUrl)
            if (ok) {
                _wlanState.update { it.copy(currentTransportState = "PAUSED_PLAYBACK") }
            }
        }
    }

    fun stop() {
        val device = _wlanState.value.selectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return
        scope.launch {
            pollingJob?.cancel()
            pollingJob = null
            upnpClient.stop(controlUrl)
            _wlanState.update {
                it.copy(
                    currentTransportState = "STOPPED",
                    isStreaming = false
                )
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val device = _wlanState.value.selectedDevice ?: return
        val controlUrl = device.avTransportControlUrl ?: return
        scope.launch {
            _wlanState.update { it.copy(currentPositionMs = positionMs) }
            upnpClient.seek(controlUrl, positionMs)
        }
    }

    fun setSpeakerVolume(volumeFraction: Float) {
        val device = _wlanState.value.selectedDevice ?: return
        val renderingUrl = device.renderingControlUrl ?: return
        val volClamped = volumeFraction.coerceIn(0f, 1f)
        _wlanState.update { it.copy(speakerVolume = volClamped, isMuted = false) }

        scope.launch {
            upnpClient.setVolume(renderingUrl, (volClamped * 100).toInt())
        }
    }

    fun toggleMute() {
        val device = _wlanState.value.selectedDevice ?: return
        val renderingUrl = device.renderingControlUrl ?: return
        val newMute = !_wlanState.value.isMuted
        _wlanState.update { it.copy(isMuted = newMute) }

        scope.launch {
            upnpClient.setMute(renderingUrl, newMute)
        }
    }

    fun addCustomDevice(ip: String, port: Int, customName: String) {
        scope.launch {
            _wlanState.update { it.copy(isScanning = true, lastError = null) }
            // Try probing standard UPnP description paths
            val candidatePaths = listOf(
                "http://$ip:$port/description.xml",
                "http://$ip:$port/device.xml",
                "http://$ip:$port/xml/device_description.xml",
                "http://$ip:$port/rootDesc.xml",
                "http://$ip:$port/upnp/desc.xml"
            )

            var foundDevice: WlanSpeakerDevice? = null
            for (path in candidatePaths) {
                val parsed = upnpClient.parseDeviceXml(path, ip)
                if (parsed != null) {
                    foundDevice = parsed
                    break
                }
            }

            val finalDevice = foundDevice ?: WlanSpeakerDevice(
                id = "custom_$ip:$port",
                name = customName.ifBlank { "Custom WLAN Speaker ($ip:$port)" },
                ipAddress = ip,
                port = port,
                manufacturer = "Custom Network Device",
                modelName = "Direct Stream / DLNA",
                speakerType = WlanSpeakerType.CUSTOM_IP,
                locationUrl = "http://$ip:$port",
                avTransportControlUrl = "http://$ip:$port/AVTransport/control",
                renderingControlUrl = "http://$ip:$port/RenderingControl/control",
                isOnline = true
            )

            _wlanState.update { state ->
                val list = state.discoveredDevices.filter { it.id != finalDevice.id } + finalDevice
                state.copy(
                    discoveredDevices = list,
                    isScanning = false
                )
            }
            connectToDevice(finalDevice)
        }
    }

    private fun startPollingPosition(controlUrl: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _wlanState.value.currentTransportState == "PLAYING") {
                delay(800L)
                try {
                    val posInfo = upnpClient.getPositionInfo(controlUrl)
                    val transportState = upnpClient.getTransportInfo(controlUrl)

                    if (posInfo != null) {
                        val (curPos, dur) = posInfo
                        _wlanState.update {
                            it.copy(
                                currentPositionMs = curPos,
                                durationMs = if (dur > 0) dur else it.durationMs
                            )
                        }

                        // Auto-advance track if reached end
                        if (dur > 0 && curPos >= dur - 1500L && dur > 10000L) {
                            onTrackEndedOnSpeaker()
                            break
                        }
                    }

                    if (transportState != null) {
                        _wlanState.update { it.copy(currentTransportState = transportState) }
                        if (transportState == "STOPPED" && _wlanState.value.isStreaming) {
                            // Track might have finished
                            onTrackEndedOnSpeaker()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Polling exception: ${e.message}")
                }
            }
        }
    }

    fun release() {
        disconnect()
        audioServer.stop()
        scope.cancel()
    }
}
