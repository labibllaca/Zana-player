package com.example.navirom.data.local

import com.example.navirom.data.model.NaviromTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecentSongsRepository(private val recentSongsDao: RecentSongsDao) {

    fun getRecentlyPlayed(limit: Int): Flow<List<NaviromTrack>> {
        return recentSongsDao.getRecentSongs(limit).map { entities ->
            entities.map { it.toTrack() }
        }
    }

    suspend fun addSong(track: NaviromTrack) {
        recentSongsDao.insertRecentSong(track.toRecentSongEntity())
    }

    suspend fun removeSong(trackId: String) {
        recentSongsDao.deleteRecentSongById(trackId)
    }

    suspend fun clearAll() {
        recentSongsDao.clearRecentSongs()
    }
}

// Extension converters between RecentSongEntity and NaviromTrack
fun RecentSongEntity.toTrack(): NaviromTrack {
    return NaviromTrack(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        coverArtUrl = coverArtUrl,
        streamUrl = streamUrl,
        path = path,
        year = year,
        genre = genre,
        bitRate = bitRate,
        suffix = suffix,
        localFilePath = localFilePath
    )
}

fun NaviromTrack.toRecentSongEntity(timestamp: Long = System.currentTimeMillis()): RecentSongEntity {
    return RecentSongEntity(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationSeconds = durationSeconds,
        coverArtId = coverArtId,
        coverArtUrl = coverArtUrl,
        streamUrl = streamUrl,
        path = path,
        year = year,
        genre = genre,
        bitRate = bitRate,
        suffix = suffix,
        localFilePath = localFilePath,
        lastPlayedAt = timestamp
    )
}
