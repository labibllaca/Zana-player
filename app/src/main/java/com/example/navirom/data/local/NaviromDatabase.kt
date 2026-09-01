package com.example.navirom.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTrackEntity::class,
        LocalPlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteTrackEntity::class,
        ServerConfigEntity::class,
        PlaybackHistoryEntity::class,
        CachedLyricsEntity::class,
        PlaybackQueueEntity::class,
        RecentSongEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NaviromDatabase : RoomDatabase() {
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun playbackQueueDao(): PlaybackQueueDao
    abstract fun recentSongsDao(): RecentSongsDao

    companion object {
        @Volatile
        private var INSTANCE: NaviromDatabase? = null

        fun getDatabase(context: Context): NaviromDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NaviromDatabase::class.java,
                    "navirom_music.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
