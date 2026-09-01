package com.example.navirom.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY cachedAtTimestamp DESC")
    fun getAllCachedTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE id = :trackId LIMIT 1")
    suspend fun getCachedTrack(trackId: String): CachedTrackEntity?

    @Query("SELECT id FROM cached_tracks")
    fun getAllCachedTrackIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTrack(track: CachedTrackEntity)

    @Query("DELETE FROM cached_tracks WHERE id = :trackId")
    suspend fun deleteCachedTrack(trackId: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clearAllCachedTracks()

    @Query("SELECT SUM(fileSizeBytes) FROM cached_tracks")
    fun getTotalCacheSizeBytes(): Flow<Long?>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM local_playlists ORDER BY createdAt DESC")
    fun getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): LocalPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removePlaylistItem(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT trackId FROM favorite_tracks")
    fun getAllFavoriteTrackIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean
}

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config")
    fun getAllServers(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_config WHERE isConnected = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerConfigEntity?

    @Query("SELECT * FROM server_config WHERE isConnected = 1 LIMIT 1")
    fun getActiveServerFlow(): Flow<ServerConfigEntity?>

    @Query("UPDATE server_config SET isConnected = 0")
    suspend fun deactivateAllServers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(config: ServerConfigEntity): Long

    @Update
    suspend fun updateServer(config: ServerConfigEntity)

    @Delete
    suspend fun deleteServer(config: ServerConfigEntity)
}

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PlaybackHistoryEntity)

    @Query("SELECT SUM(listenedSeconds) FROM playback_history")
    fun getTotalListenedSeconds(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM playback_history")
    fun getTotalSessionsCount(): Flow<Int>

    @Query("SELECT * FROM playback_history WHERE timestamp >= :sinceTimestamp")
    fun getHistorySince(sinceTimestamp: Long): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsByTrackId(trackId: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(lyrics: CachedLyricsEntity)

    @Query("DELETE FROM cached_lyrics WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: String)
}

@Dao
interface PlaybackQueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    fun getQueue(): Flow<List<PlaybackQueueEntity>>

    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    suspend fun getQueueList(): List<PlaybackQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(items: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()
}

@Dao
interface RecentSongsDao {
    @Query("SELECT * FROM recent_songs ORDER BY lastPlayedAt DESC")
    fun getAllRecentSongs(): Flow<List<RecentSongEntity>>

    @Query("SELECT * FROM recent_songs ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentSongs(limit: Int): Flow<List<RecentSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSong(song: RecentSongEntity)

    @Query("DELETE FROM recent_songs WHERE id = :trackId")
    suspend fun deleteRecentSongById(trackId: String)

    @Query("DELETE FROM recent_songs")
    suspend fun clearRecentSongs()
}

