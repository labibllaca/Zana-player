package com.example.navirom.data.cache

import android.content.Context
import com.example.navirom.data.local.CachedTrackDao
import com.example.navirom.data.local.CachedTrackEntity
import com.example.navirom.data.model.DownloadStatus
import com.example.navirom.data.model.NaviromTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

import com.example.navirom.data.api.HttpClientProvider

import com.example.navirom.diagnostics.AppDiagnostics
import com.example.navirom.diagnostics.DiagnosticCodes

class OfflineDownloadManager(
    private val context: Context,
    private val cachedTrackDao: CachedTrackDao,
    private val okHttpClient: OkHttpClient = HttpClientProvider.client
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadStatusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, DownloadStatus>> = _downloadStatusMap.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val cacheDir: File
        get() {
            val dir = context.getExternalFilesDir("audio_cache") ?: File(context.filesDir, "audio_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getLocalFileForTrack(trackId: String): File {
        val safeName = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir, "$safeName.mp3")
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        val file = getLocalFileForTrack(trackId)
        return file.exists() && file.length() > 0
    }

    fun downloadTrack(track: NaviromTrack) {
        if (track.streamUrl.isBlank()) return
        if (isTrackDownloaded(track.id)) {
            _downloadStatusMap.update { it + (track.id to DownloadStatus.DOWNLOADED) }
            return
        }

        scope.launch {
            _downloadStatusMap.update { it + (track.id to DownloadStatus.DOWNLOADING) }
            _downloadProgressMap.update { it + (track.id to 0.05f) }

            val targetFile = getLocalFileForTrack(track.id)
            val tempFile = File(cacheDir, "${targetFile.name}.tmp")

            try {
                val request = Request.Builder().url(track.streamUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _downloadStatusMap.update { it + (track.id to DownloadStatus.FAILED) }
                        return@launch
                    }

                    val body = response.body ?: throw IllegalStateException("Empty audio body")
                    val totalLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var downloadedBytes = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                if (totalLength > 0) {
                                    val progress = (downloadedBytes.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                    _downloadProgressMap.update { it + (track.id to progress) }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)

                    val entity = CachedTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        artistId = track.artistId,
                        album = track.album,
                        albumId = track.albumId,
                        durationSeconds = track.durationSeconds,
                        coverArtUrl = track.coverArtUrl,
                        localFilePath = targetFile.absolutePath,
                        cachedAtTimestamp = System.currentTimeMillis(),
                        fileSizeBytes = targetFile.length(),
                        bitRate = track.bitRate,
                        format = track.suffix,
                        year = track.year,
                        genre = track.genre
                    )
                    cachedTrackDao.insertCachedTrack(entity)

                    _downloadStatusMap.update { it + (track.id to DownloadStatus.DOWNLOADED) }
                    _downloadProgressMap.update { it + (track.id to 1.0f) }
                } else {
                    _downloadStatusMap.update { it + (track.id to DownloadStatus.FAILED) }
                }
            } catch (e: Exception) {
                AppDiagnostics.logError(
                    errorCode = DiagnosticCodes.CACHE_DOWNLOAD_FAIL_501,
                    tag = "OfflineDownload",
                    message = "Failed to download track '${track.title}': ${e.message}",
                    throwable = e,
                    contextInfo = "TrackID: ${track.id}, URL: ${track.streamUrl}"
                )
                tempFile.delete()
                _downloadStatusMap.update { it + (track.id to DownloadStatus.FAILED) }
            }
        }
    }

    fun downloadTracks(tracks: List<NaviromTrack>) {
        tracks.forEach { downloadTrack(it) }
    }

    suspend fun deleteCachedTrack(trackId: String) = withContext(Dispatchers.IO) {
        val file = getLocalFileForTrack(trackId)
        if (file.exists()) {
            file.delete()
        }
        cachedTrackDao.deleteCachedTrack(trackId)
        _downloadStatusMap.update { it + (trackId to DownloadStatus.NOT_DOWNLOADED) }
        _downloadProgressMap.update { it - trackId }
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        cachedTrackDao.clearAllCachedTracks()
        _downloadStatusMap.value = emptyMap()
        _downloadProgressMap.value = emptyMap()
    }
}
