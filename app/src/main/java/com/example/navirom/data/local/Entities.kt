package com.example.navirom.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val durationSeconds: Int,
    val coverArtUrl: String,
    val localFilePath: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis(),
    val fileSizeBytes: Long = 0L,
    val bitRate: Int? = null,
    val format: String = "mp3",
    val year: Int? = null,
    val genre: String = ""
)

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val comment: String = "",
    val songCount: Int = 0,
    val durationSeconds: Int = 0,
    val coverArtUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "trackId"])
data class PlaylistItemEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val coverArtUrl: String,
    val streamUrl: String,
    val localFilePath: String? = null
)

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val starredAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val useTokenAuth: Boolean = true,
    val isConnected: Boolean = false,
    val isDemoMode: Boolean = false,
    val activeMusicFolderId: String? = null,
    val lastSyncTime: Long = 0L,
    val alternativeHost: String = ""
)

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverArtUrl: String = "",
    val durationSeconds: Int,
    val listenedSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val dayOfWeek: Int, // 1 = Sunday, 2 = Monday, ..., 7 = Saturday
    val hourOfDay: Int // 0..23
)

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val source: String, // "FILE", "NAVIDROME", "LRCLIB_ONLINE"
    val isSynced: Boolean,
    val plainLyrics: String,
    val syncedLyricsJson: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val artistId: String = "",
    val album: String = "Unknown Album",
    val albumId: String = "",
    val durationSeconds: Int = 0,
    val coverArtUrl: String = "",
    val streamUrl: String = "",
    val localFilePath: String? = null,
    val format: String = "mp3",
    val year: Int? = null,
    val genre: String = "",
    val bitRate: Int? = null,
    val sizeBytes: Long = 0L
)

@Entity(tableName = "recent_songs")
data class RecentSongEntity(
    @PrimaryKey val id: String,
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
    val localFilePath: String? = null,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

