# WLAN Receiver Speaker Playback Architecture & Implementation Plan

## 1. Overview & Objective
Enable Navirom to stream and control audio playback directly to **WLAN / Wi-Fi Receiver Speakers** (such as DLNA/UPnP MediaRenderers, Sonos, Bose, Denon HEOS, Yamaha MusicCast, Pioneer, smart TVs, Raspberry Pi receivers like Volumio/Moode/Kodi/gmrender, and custom HTTP stream endpoints) over the local Wi-Fi network.

---

## 2. Core Architecture Components

```
+---------------------------------------------------------------------------------+
|                                Navirom App                                      |
|                                                                                 |
|  +--------------------+    +--------------------+    +-----------------------+  |
|  |  WlanSpeakerManager |<-->| LocalAudioServer   |<-->| AudioPlayerController |  |
|  |  - SSDP / UPnP Scan |    | - Embedded HTTP Srv|    | - Playback State Sync |  |
|  |  - AVTransport Ctrl |    | - Range / MP3/FLAC |    | - Queue & Metadata   |  |
|  |  - Volume & Status  |    | - Wi-Fi IP Handler |    | - Dual/Remote Routing |  |
|  +---------+----------+    +---------+----------+    +-----------+-----------+  |
+------------|-------------------------|---------------------------|---------------+
             | (SOAP / AVTransport)    | (HTTP Audio Stream)       |
             v                         v                           v
  +-------------------------------------------------------------------------------+
  |                     WLAN Network (239.255.255.250 / LAN)                      |
  |                                                                               |
  |  +---------------------------+       +------------------------------------+  |
  |  |   DLNA / UPnP Speaker     |       |   Direct HTTP / Web Receiver       |  |
  |  |   (Sonos, Smart TV, AVR)  |       |   (Browser, VLC, Multiroom Stream) |  |
  |  +---------------------------+       +------------------------------------+  |
  +-------------------------------------------------------------------------------+
```

---

## 3. Detailed Component Specifications

### A. WLAN & SSDP Discovery Engine (`WlanDiscoveryManager`)
- **Protocol**: SSDP (Simple Service Discovery Protocol) over UDP multicast (`239.255.255.250:1900`).
- **Target Search**: `urn:schemas-upnp-org:device:MediaRenderer:1`, `urn:schemas-upnp-org:service:AVTransport:1`, `ssdp:all`.
- **Parser**: Fetches UPnP XML device descriptions from `LOCATION` header; extracts:
  - `friendlyName`, `manufacturer`, `modelName`, `iconUrl`
  - `AVTransport` control URL and event sub URL
  - `RenderingControl` control URL (for hardware volume & mute control)
- **Manual Endpoint Fallback**: Support adding custom WLAN receiver endpoints via IP address & port (e.g. `192.168.1.150:1400` or direct stream URL).

### B. Embedded Local Wi-Fi Audio Streaming Server (`WlanAudioServer`)
- Lightweight, asynchronous HTTP server running on the device's local Wi-Fi IP (e.g., port `8765`).
- Serves:
  1. `/audio/track/{id}`: Serves downloaded/cached track files or proxies remote Subsonic stream with full HTTP `Range` byte-seeking support (HTTP 206 Partial Content).
  2. `/audio/current`: Serves the currently active track stream dynamically.
  3. `/stream/live`: Live HTTP PCM/MP3 broadcast for streaming clients.
- Generates DIDL-Lite XML metadata for DLNA clients with rich tags (title, artist, album, duration, album art URL).

### C. UPnP / DLNA AVTransport & Rendering Control Client (`WlanUpnpClient`)
- Dispatches SOAP XML commands:
  - `SetAVTransportURI(CurrentURI, CurrentURIMetaData)`
  - `Play(Speed=1)`
  - `Pause()`
  - `Stop()`
  - `Seek(Unit=REL_TIME, Target=00:02:15)`
  - `SetVolume(DesiredVolume=0..100)`
  - `GetPositionInfo()` & `GetTransportInfo()` for active position & state polling.

### D. AudioPlayerController Integration (`AudioPlayerController` & `NaviromViewModel`)
- **Routing Modes**:
  - `LOCAL_ONLY`: Default phone speaker / Bluetooth audio output.
  - `WLAN_SPEAKER`: Audio streamed directly to the Wi-Fi speaker; phone acts as remote controller with real-time seek and volume synchronization.
  - `DUAL_SYNC`: Local player + WLAN receiver playing simultaneously.
- State preservation: Track position, volume, playback state, and queue transitions smoothly during speaker connection/disconnection.

### E. User Interface & Experience
- **WLAN Speaker Cast Dialog / Sheet**:
  - Scanning radar animation & list of discovered Wi-Fi speakers.
  - Current connection status badge (Connected, Connecting, Idle).
  - Speaker hardware volume slider.
  - Direct HTTP Stream URL generator & clipboard copy button (for manual streaming to web players or network AVRs).
  - Manual IP:Port add modal.
- **Player Bar & Full Player Integration**:
  - Cast icon with active WLAN streaming glow and speaker name indicator.
  - Quick speaker switch and volume control.
- **Sidebar & Settings Integration**:
  - WLAN Speaker status tile in Sidebar Drawer and Server/Output Settings.

---

## 4. Implementation Steps & Milestones

1. **Permissions & Gradle Setup**:
   - Add `CHANGE_WIFI_MULTICAST_STATE` to `AndroidManifest.xml`.
   - Bump app version in `gradle/libs.versions.toml` to `1.16.0` (Code 16).
2. **Core WLAN & DLNA Networking**:
   - Implement `WlanDeviceModel.kt` (data classes for speakers, connection state, capabilities).
   - Implement `WlanAudioServer.kt` (embedded HTTP audio streaming server).
   - Implement `WlanUpnpClient.kt` (SOAP AVTransport & RenderingControl client).
   - Implement `WlanSpeakerManager.kt` (discovery, state coordination, background polling).
3. **Player & ViewModel Binding**:
   - Connect `WlanSpeakerManager` to `AudioPlayerController` and `NaviromViewModel`.
   - Implement synchronized play/pause, seek, volume, and track progression.
4. **UI Components**:
   - Create `WlanSpeakerCastSheet.kt` (modal for discovery, device selection, volume, stream info).
   - Update `MiniPlayerBar.kt`, `FullPlayerModal.kt`, `SidebarDrawer.kt`, and `ServerSettingsScreen.kt`.
5. **Compilation, Verification & Testing**:
   - Run compilation and verify complete error-free build.
