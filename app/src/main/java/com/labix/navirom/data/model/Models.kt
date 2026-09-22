package com.labix.navirom.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class NaviromTrack(
    val id: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val artistId: String = "",
    val album: String = "Unknown Album",
    val albumId: String = "",
    val durationSeconds: Int = 0,
    val coverArtId: String = "",
    val coverArtUrl: String = "",
    val streamUrl: String = "",
    val path: String = "",
    val year: Int? = null,
    val genre: String = "",
    val bitRate: Int? = null,
    val suffix: String = "mp3",
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isCached: Boolean = false,
    val localFilePath: String? = null,
    val isFavorite: Boolean = false,
    val sizeBytes: Long = 0L
) {
    val durationFormatted: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class NaviromAlbum(
    val id: String,
    val name: String,
    val artist: String = "Unknown Artist",
    val artistId: String = "",
    val coverArt: String = "",
    val coverArtUrl: String = "",
    val songCount: Int = 0,
    val durationSeconds: Int = 0,
    val year: Int? = null,
    val genre: String = ""
)

data class NaviromArtist(
    val id: String,
    val name: String,
    val coverArt: String = "",
    val coverArtUrl: String = "",
    val albumCount: Int = 0
)

data class NaviromPlaylist(
    val id: String,
    val name: String,
    val comment: String = "",
    val songCount: Int = 0,
    val durationSeconds: Int = 0,
    val isPublic: Boolean = false,
    val created: String = "",
    val coverArt: String = "",
    val isLocal: Boolean = false
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class SleepTimerOptions(
    val disableBluetooth: Boolean = false,
    val disableWifi: Boolean = false,
    val disableMobileData: Boolean = false
)

data class PlaybackState(
    val currentTrack: NaviromTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutesLeft: Int? = null,
    val sleepTimerSecondsLeft: Int? = null,
    val sleepTimerOptions: SleepTimerOptions = SleepTimerOptions(),
    val errorMessage: String? = null,
    val unplayableTrackIds: Set<String> = emptySet()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class SecondaryPlaybackState(
    val currentTrack: NaviromTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null,
    val queue: List<NaviromTrack> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

