package com.labix.navirom.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.labix.navirom.data.model.NaviromTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioRepository(private val context: Context) {

    suspend fun getLocalAudioTracks(): List<NaviromTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<NaviromTrack>()
        val contentResolver = context.contentResolver
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artistRaw = cursor.getString(artistColumn)
                    val artist = if (artistRaw.isNullOrBlank() || artistRaw == "<unknown>") "Unknown Artist" else artistRaw
                    val artistId = cursor.getLong(artistIdColumn).toString()
                    val albumRaw = cursor.getString(albumColumn)
                    val album = if (albumRaw.isNullOrBlank() || albumRaw == "<unknown>") "Unknown Album" else albumRaw
                    val albumId = cursor.getLong(albumIdColumn)
                    val durationMs = cursor.getLong(durationColumn)
                    val year = cursor.getInt(yearColumn)
                    val trackNum = cursor.getInt(trackColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val size = cursor.getLong(sizeColumn)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)

                    val suffix = if (filePath.isNotBlank() && filePath.contains(".")) {
                        filePath.substringAfterLast(".").lowercase()
                    } else "mp3"

                    tracks.add(
                        NaviromTrack(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            artistId = "local_artist_$artistId",
                            album = album,
                            albumId = "local_album_$albumId",
                            durationSeconds = (durationMs / 1000).toInt(),
                            coverArtId = albumId.toString(),
                            coverArtUrl = albumArtUri.toString(),
                            streamUrl = contentUri.toString(),
                            localFilePath = contentUri.toString(),
                            path = filePath,
                            year = if (year > 0) year else null,
                            suffix = suffix,
                            trackNumber = if (trackNum > 0) trackNum else null,
                            isCached = true,
                            sizeBytes = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }
}
