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

data class SubFolderEntry(
    val name: String,
    val path: String,
    val totalTracksCount: Int,
    val directTracksCount: Int,
    val subfoldersCount: Int
)

data class FolderViewContent(
    val currentPath: String,
    val currentName: String,
    val parentPath: String?,
    val breadcrumbs: List<Pair<String, String>>, // (Label, AbsolutePath)
    val subfolders: List<SubFolderEntry>,
    val directTracks: List<NaviromTrack>,
    val totalTracksInTree: List<NaviromTrack>
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
                    val rawTitle = cursor.getString(titleColumn)
                    val artistRaw = cursor.getString(artistColumn)
                    val artistId = cursor.getLong(artistIdColumn).toString()
                    val durationMs = cursor.getLong(durationColumn)
                    val year = cursor.getInt(yearColumn)
                    val trackNum = cursor.getInt(trackColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val size = cursor.getLong(sizeColumn)
                    val albumId = cursor.getLong(albumIdColumn)

                    val file = try { if (filePath.isNotBlank()) File(filePath) else null } catch (e: Exception) { null }
                    val fileNameWithoutExt = file?.nameWithoutExtension ?: ""
                    val folderName = file?.parentFile?.name ?: "Musik"

                    val title = when {
                        !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" && !rawTitle.startsWith("track_", ignoreCase = true) -> rawTitle
                        fileNameWithoutExt.isNotBlank() -> fileNameWithoutExt
                        else -> "Track $id"
                    }

                    val artist = when {
                        !artistRaw.isNullOrBlank() && artistRaw != "<unknown>" && artistRaw != "Unknown Artist" -> artistRaw
                        else -> folderName
                    }

                    // For local audio files, group/display strictly based on folder
                    val album = folderName

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
                            artistId = "local_folder_${folderName.hashCode()}",
                            album = album,
                            albumId = "local_folder_${folderName.hashCode()}",
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

        // Direct scan fallback for the public Music directory
        try {
            val scannedPaths = tracks.map { it.path }.filter { it.isNotBlank() }.toSet()
            val musicDir = File(getDefaultMusicDirectoryPath())
            if (musicDir.exists() && musicDir.canRead()) {
                val audioExtensions = setOf("mp3", "flac", "m4a", "wav", "aac", "ogg", "opus", "wma", "alac", "aiff")
                musicDir.walkTopDown()
                    .maxDepth(6)
                    .filter { file ->
                        file.isFile && file.length() > 0 && audioExtensions.contains(file.extension.lowercase())
                    }
                    .forEach { audioFile ->
                        if (!scannedPaths.contains(audioFile.absolutePath)) {
                            val suffix = audioFile.extension.lowercase()
                            val folderName = audioFile.parentFile?.name ?: "Music"
                            tracks.add(
                                NaviromTrack(
                                    id = "local_file_${audioFile.absolutePath.hashCode()}",
                                    title = audioFile.nameWithoutExtension,
                                    artist = folderName,
                                    artistId = "local_folder_${folderName.hashCode()}",
                                    album = folderName,
                                    albumId = "local_folder_${folderName.hashCode()}",
                                    durationSeconds = 0,
                                    coverArtId = "",
                                    coverArtUrl = "",
                                    streamUrl = Uri.fromFile(audioFile).toString(),
                                    localFilePath = Uri.fromFile(audioFile).toString(),
                                    path = audioFile.absolutePath,
                                    suffix = suffix,
                                    isCached = true,
                                    sizeBytes = audioFile.length()
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }

    fun getDefaultMusicDirectoryPath(): String {
        return try {
            val pub = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            if (pub != null && pub.exists()) {
                pub.absolutePath
            } else {
                "/storage/emulated/0/Music"
            }
        } catch (_: Exception) {
            "/storage/emulated/0/Music"
        }
    }

    fun getFolderViewContent(currentPath: String, allTracks: List<NaviromTrack>): FolderViewContent {
        val normPath = currentPath.trimEnd('/')
        val curFile = File(normPath)
        val defaultMusic = getDefaultMusicDirectoryPath().trimEnd('/')

        val currentName = when {
            normPath == defaultMusic -> "Music"
            normPath == "/storage/emulated/0" -> "Internal Storage"
            normPath.endsWith("/Music") -> "Music"
            else -> curFile.name.ifBlank { "Music" }
        }

        val parentPath = if (normPath == defaultMusic) {
            null
        } else {
            curFile.parentFile?.absolutePath
        }

        // Breadcrumbs: hierarchical path items
        val breadcrumbs = mutableListOf<Pair<String, String>>()
        if (normPath.startsWith(defaultMusic)) {
            breadcrumbs.add(Pair("Music", defaultMusic))
            val rel = normPath.removePrefix(defaultMusic).trimStart('/')
            if (rel.isNotBlank()) {
                val parts = rel.split('/').filter { it.isNotBlank() }
                var accum = defaultMusic
                for (part in parts) {
                    accum = "$accum/$part"
                    breadcrumbs.add(Pair(part, accum))
                }
            }
        } else {
            val emulatedPrefix = "/storage/emulated/0"
            if (normPath.startsWith(emulatedPrefix)) {
                breadcrumbs.add(Pair("Storage", emulatedPrefix))
                val rel = normPath.removePrefix(emulatedPrefix).trimStart('/')
                if (rel.isNotBlank()) {
                    val parts = rel.split('/').filter { it.isNotBlank() }
                    var accum = emulatedPrefix
                    for (part in parts) {
                        accum = "$accum/$part"
                        breadcrumbs.add(Pair(part, accum))
                    }
                }
            } else {
                val parts = normPath.trim('/').split('/').filter { it.isNotBlank() }
                var accum = ""
                for (part in parts) {
                    accum = "$accum/$part"
                    breadcrumbs.add(Pair(part, accum))
                }
            }
        }

        // Direct tracks in this folder
        val directTracks = allTracks.filter { track ->
            val tp = track.path.trimEnd('/')
            if (tp.isBlank()) false else {
                val trackParent = try { File(tp).parentFile?.absolutePath?.trimEnd('/') } catch (_: Exception) { null }
                trackParent == normPath
            }
        }.sortedWith(compareBy({ it.trackNumber ?: 999 }, { it.title.lowercase() }))

        // All tracks recursively under normPath
        val totalTracksInTree = allTracks.filter { track ->
            val tp = track.path.trimEnd('/')
            if (tp.isBlank()) false else {
                tp.startsWith("$normPath/") || tp == normPath ||
                    (try { File(tp).parentFile?.absolutePath?.trimEnd('/') == normPath } catch (_: Exception) { false })
            }
        }.sortedBy { it.title.lowercase() }

        // Subfolders under this directory
        val subfolderPaths = mutableSetOf<String>()
        allTracks.forEach { track ->
            val tp = track.path.trimEnd('/')
            if (tp.startsWith("$normPath/")) {
                val rel = tp.removePrefix("$normPath/").trimStart('/')
                if (rel.contains('/')) {
                    val subName = rel.substringBefore('/')
                    subfolderPaths.add("$normPath/$subName")
                }
            }
        }

        if (curFile.exists() && curFile.isDirectory) {
            try {
                curFile.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                    subfolderPaths.add(sub.absolutePath.trimEnd('/'))
                }
            } catch (_: Exception) {}
        }

        val subfolderEntries = subfolderPaths.map { subPath ->
            val subNorm = subPath.trimEnd('/')
            val subName = File(subNorm).name
            val subDirect = allTracks.count {
                val p = it.path.trimEnd('/')
                p.isNotBlank() && try { File(p).parentFile?.absolutePath?.trimEnd('/') == subNorm } catch (_: Exception) { false }
            }
            val subTotal = allTracks.count {
                val p = it.path.trimEnd('/')
                p.isNotBlank() && (p.startsWith("$subNorm/") || p == subNorm)
            }
            val subSubCount = try { File(subNorm).listFiles()?.count { it.isDirectory } ?: 0 } catch (_: Exception) { 0 }
            SubFolderEntry(
                name = subName,
                path = subNorm,
                totalTracksCount = subTotal,
                directTracksCount = subDirect,
                subfoldersCount = subSubCount
            )
        }.sortedBy { it.name.lowercase() }

        return FolderViewContent(
            currentPath = normPath,
            currentName = currentName,
            parentPath = parentPath,
            breadcrumbs = breadcrumbs,
            subfolders = subfolderEntries,
            directTracks = directTracks,
            totalTracksInTree = totalTracksInTree
        )
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

