package com.labix.navirom.player.wlan

import com.labix.navirom.data.model.NaviromTrack

enum class WlanSpeakerType {
    DLNA_RENDERER,
    SONOS,
    HEOS,
    BOSE,
    CHROMECAST_AUDIO,
    UPNP_AV,
    AIRPLAY_BRIDGE,
    DIRECT_HTTP_RECEIVER,
    CUSTOM_IP
}

enum class WlanConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR
}

enum class WlanPlaybackMode {
    LOCAL_ONLY,          // Play only on phone / local output
    WLAN_SPEAKER_ONLY,   // Stream only to WLAN speaker (phone acts as remote)
    DUAL_SYNC            // Play simultaneously on phone + WLAN speaker
}

data class WlanSpeakerDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 1400,
    val manufacturer: String = "Generic",
    val modelName: String = "WLAN Receiver",
    val modelNumber: String = "",
    val speakerType: WlanSpeakerType = WlanSpeakerType.DLNA_RENDERER,
    val locationUrl: String = "",
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val iconUrl: String? = null,
    val isOnline: Boolean = true,
    val isConnected: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class WlanSpeakerState(
    val selectedDevice: WlanSpeakerDevice? = null,
    val discoveredDevices: List<WlanSpeakerDevice> = emptyList(),
    val isScanning: Boolean = false,
    val connectionStatus: WlanConnectionStatus = WlanConnectionStatus.DISCONNECTED,
    val playbackMode: WlanPlaybackMode = WlanPlaybackMode.LOCAL_ONLY,
    val speakerVolume: Float = 0.8f, // 0.0 to 1.0
    val isMuted: Boolean = false,
    val currentTransportState: String = "STOPPED", // STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING
    val currentTrack: NaviromTrack? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isStreaming: Boolean = false,
    val lastError: String? = null,
    val localServerIp: String = "",
    val localServerPort: Int = 8765,
    val directStreamUrl: String = "",
    val directTrackUrl: String = ""
)
