package com.labix.navirom.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.labix.navirom.data.model.NaviromTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalMusicFolder(
    val id: String,
    val name: String,
    val path: String,
    val displayPath: String,
    val trackCount: Int,
    val totalDurationSeconds: Int,
    val tracks: List<NaviromTrack> = emptyList()
)

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

    suspend fun getLocalAudioFolders(): List<LocalMusicFolder> = withContext(Dispatchers.IO) {
        val tracks = getLocalAudioTracks()
        groupTracksIntoFolders(tracks)
    }

    fun groupTracksIntoFolders(tracks: List<NaviromTrack>): List<LocalMusicFolder> {
        val groups = tracks.groupBy { track ->
            val fp = track.path.ifBlank { "" }
            if (fp.isNotBlank()) {
                try {
                    val parent = File(fp).parentFile
                    parent?.absolutePath ?: "Internal Storage"
                } catch (e: Exception) {
                    "Internal Storage"
                }
            } else {
                "Internal Storage"
            }
        }

        return groups.map { (folderPath, folderTracks) ->
            val folderFile = try { File(folderPath) } catch (e: Exception) { null }
            val (name, displayPath) = formatFolderNames(folderPath, folderFile)
            val durationSec = folderTracks.sumOf { it.durationSeconds }

            LocalMusicFolder(
                id = folderPath,
                name = name,
                path = folderPath,
                displayPath = displayPath,
                trackCount = folderTracks.size,
                totalDurationSeconds = durationSec,
                tracks = folderTracks.sortedBy { it.title.lowercase() }
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun formatFolderNames(folderPath: String, file: File?): Pair<String, String> {
        if (folderPath == "Internal Storage") {
            return Pair("Interner Speicher", "Internal Storage")
        }
        val emulatedPrefix = "/storage/emulated/0"
        if (folderPath.startsWith(emulatedPrefix)) {
            val relative = folderPath.removePrefix(emulatedPrefix).trim('/')
            if (relative.isBlank()) {
                return Pair("Interner Speicher", emulatedPrefix)
            }
            val folderName = if (relative.contains('/')) relative else relative
            return Pair(folderName, relative)
        }
        if (folderPath.startsWith("/storage/")) {
            val parts = folderPath.removePrefix("/storage/").trim('/').split('/')
            val folderName = parts.lastOrNull() ?: file?.name ?: "SD-Card"
            val display = "SD: " + parts.drop(1).joinToString("/")
            return Pair(folderName, if (display.endsWith(": ")) "SD-Karte" else display)
        }
        val name = file?.name?.ifBlank { "Musik-Ordner" } ?: "Musik-Ordner"
        return Pair(name, folderPath)
    }
}

