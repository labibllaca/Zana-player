package com.labix.navirom.data.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.labix.navirom.data.api.HttpClientProvider
import com.labix.navirom.data.api.NaviromSubsonicClient
import com.labix.navirom.data.local.CachedLyricsEntity
import com.labix.navirom.data.local.LyricsDao
import com.labix.navirom.data.model.NaviromTrack
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class LyricsSource(val displayNameEn: String, val displayNameSq: String) {
    EMBEDDED_FILE("Embedded in File", "Brenda skedarit"),
    NAVIDROME_SERVER("Navidrome Server", "Serveri Navidrome"),
    ONLINE_LRCLIB("Online (LrcLib Synced)", "Nga Interneti (LrcLib)"),
    NOT_FOUND("Not Found", "Nuk u gjet")
}

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val source: LyricsSource = LyricsSource.NOT_FOUND,
    val isSynced: Boolean = false,
    val plainLyrics: String = "",
    val syncedLines: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class LrcLibResponse(
    val id: Long? = null,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

class LyricsRepository(
    private val context: Context,
    private val lyricsDao: LyricsDao,
    private val subsonicClient: NaviromSubsonicClient
) {
    private val TAG = "LyricsRepository"
    private val okHttpClient = HttpClientProvider.client

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val lrcLibAdapter = moshi.adapter(LrcLibResponse::class.java)
    private val lrcLibListType = Types.newParameterizedType(List::class.java, LrcLibResponse::class.java)
    private val lrcLibListAdapter = moshi.adapter<List<LrcLibResponse>>(lrcLibListType)
    private val lyricLinesListType = Types.newParameterizedType(List::class.java, LyricLineDto::class.java)
    private val lyricLinesAdapter = moshi.adapter<List<LyricLineDto>>(lyricLinesListType)

    @JsonClass(generateAdapter = true)
    data class LyricLineDto(val timeMs: Long, val text: String)

    suspend fun getLyricsForTrack(track: NaviromTrack, localFilePath: String? = null, forceRefresh: Boolean = false): LyricsData = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            // Check Room cache
            val cached = lyricsDao.getLyricsByTrackId(track.id)
            if (cached != null) {
                val lines = parseSyncedJson(cached.syncedLyricsJson)
                val src = try {
                    LyricsSource.valueOf(cached.source)
                } catch (e: Exception) {
                    LyricsSource.ONLINE_LRCLIB
                }
                return@withContext LyricsData(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    source = src,
                    isSynced = cached.isSynced,
                    plainLyrics = cached.plainLyrics,
                    syncedLines = lines
                )
            }
        }

        // 1. STEP 1: Check embedded lyrics in local file or companion .lrc file if downloaded
        if (localFilePath != null && File(localFilePath).exists()) {
            val fileLyrics = extractLocalFileLyrics(localFilePath)
            if (fileLyrics != null) {
                saveAndCacheLyrics(track, fileLyrics)
                return@withContext fileLyrics
            }
        }

        // 2. STEP 2: Check Navidrome / Subsonic Server
        val serverLyrics = fetchLyricsFromServer(track)
        if (serverLyrics != null) {
            saveAndCacheLyrics(track, serverLyrics)
            return@withContext serverLyrics
        }

        // 3. STEP 3: Check Online (LrcLib API)
        val onlineLyrics = fetchLyricsFromOnline(track)
        if (onlineLyrics != null) {
            saveAndCacheLyrics(track, onlineLyrics)
            return@withContext onlineLyrics
        }

        // If not found anywhere
        val notFoundData = LyricsData(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            source = LyricsSource.NOT_FOUND,
            isSynced = false,
            plainLyrics = "No lyrics found for \"${track.title}\" by ${track.artist}.\n\nChecked local file, Navidrome server, and online synchronized lyrics database."
        )
        return@withContext notFoundData
    }

    private fun extractLocalFileLyrics(filePath: String): LyricsData? {
        try {
            // Check companion .lrc file
            val lrcFile = File(filePath.substringBeforeLast(".") + ".lrc")
            if (lrcFile.exists()) {
                val content = lrcFile.readText()
                val parsed = parseLrc(content)
                if (parsed.isNotEmpty() || content.isNotBlank()) {
                    return LyricsData(
                        source = LyricsSource.EMBEDDED_FILE,
                        isSynced = parsed.isNotEmpty(),
                        plainLyrics = if (parsed.isNotEmpty()) parsed.joinToString("\n") { it.text } else content,
                        syncedLines = parsed
                    )
                }
            }

            // Extract with MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            // Some devices expose METADATA_KEY_LYRICS or title tags
            val rawLyrics = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) // fallback check
            retriever.release()
            // If rawLyrics had synced format
            if (rawLyrics != null && (rawLyrics.contains("[00:") || rawLyrics.contains("[01:"))) {
                val parsed = parseLrc(rawLyrics)
                return LyricsData(
                    source = LyricsSource.EMBEDDED_FILE,
                    isSynced = parsed.isNotEmpty(),
                    plainLyrics = rawLyrics,
                    syncedLines = parsed
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to read local file lyrics: ${e.message}")
        }
        return null
    }

    private suspend fun fetchLyricsFromServer(track: NaviromTrack): LyricsData? {
        try {
            // Try OpenSubsonic getLyricsBySongId or getLyrics
            val lyricsResult = subsonicClient.getLyrics(track.artist, track.title, track.id)
            if (lyricsResult.isSuccess) {
                val text = lyricsResult.getOrNull()
                if (!text.isNullOrBlank()) {
                    val parsed = parseLrc(text)
                    return LyricsData(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        source = LyricsSource.NAVIDROME_SERVER,
                        isSynced = parsed.isNotEmpty(),
                        plainLyrics = if (parsed.isNotEmpty()) parsed.joinToString("\n") { it.text } else text,
                        syncedLines = parsed
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Subsonic lyrics error: ${e.message}")
        }
        return null
    }

    private suspend fun fetchLyricsFromOnline(track: NaviromTrack): LyricsData? {
        try {
            // Clean artist & title for optimal matching (remove feat., (live), etc.)
            val cleanTitle = cleanSearchTerm(track.title)
            val cleanArtist = cleanSearchTerm(track.artist)

            // Try exact match API first
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()
            if (urlBuilder != null) {
                urlBuilder.addQueryParameter("track_name", cleanTitle)
                urlBuilder.addQueryParameter("artist_name", cleanArtist)
                if (track.album.isNotBlank() && !track.album.contains("unknown", ignoreCase = true)) {
                    urlBuilder.addQueryParameter("album_name", cleanSearchTerm(track.album))
                }
                if (track.durationSeconds > 0) {
                    urlBuilder.addQueryParameter("duration", track.durationSeconds.toString())
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", "Navirom-Android-Music-Player/1.2")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val result: LyricsData? = response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val dto = lrcLibAdapter.fromJson(body)
                            if (dto != null && (dto.syncedLyrics != null || dto.plainLyrics != null)) {
                                val syncedLines = if (dto.syncedLyrics != null) parseLrc(dto.syncedLyrics) else emptyList()
                                LyricsData(
                                    trackId = track.id,
                                    title = track.title,
                                    artist = track.artist,
                                    source = LyricsSource.ONLINE_LRCLIB,
                                    isSynced = syncedLines.isNotEmpty(),
                                    plainLyrics = dto.plainLyrics ?: (if (syncedLines.isNotEmpty()) syncedLines.joinToString("\n") { it.text } else ""),
                                    syncedLines = syncedLines
                                )
                            } else null
                        } else null
                    } else null
                }
                if (result != null) return result
            }

            // Fallback: Search API
            val searchUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", "$cleanArtist $cleanTitle")
                ?.build()

            if (searchUrl != null) {
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Navirom-Android-Music-Player/1.2")
                    .build()
                val searchResp = okHttpClient.newCall(searchReq).execute()
                val searchResult: LyricsData? = searchResp.use { resp ->
                    if (resp.isSuccessful) {
                        val searchBody = resp.body?.string()
                        if (!searchBody.isNullOrBlank()) {
                            val list = lrcLibListAdapter.fromJson(searchBody)
                            val match = list?.firstOrNull { it.syncedLyrics != null || it.plainLyrics != null }
                            if (match != null) {
                                val syncedLines = if (match.syncedLyrics != null) parseLrc(match.syncedLyrics) else emptyList()
                                LyricsData(
                                    trackId = track.id,
                                    title = track.title,
                                    artist = track.artist,
                                    source = LyricsSource.ONLINE_LRCLIB,
                                    isSynced = syncedLines.isNotEmpty(),
                                    plainLyrics = match.plainLyrics ?: (if (syncedLines.isNotEmpty()) syncedLines.joinToString("\n") { it.text } else ""),
                                    syncedLines = syncedLines
                                )
                            } else null
                        } else null
                    } else null
                }
                if (searchResult != null) return searchResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Online lyrics fetch failed: ${e.message}")
        }
        return null
    }

    private suspend fun saveAndCacheLyrics(track: NaviromTrack, data: LyricsData) {
        try {
            val jsonDtos = data.syncedLines.map { LyricLineDto(it.timeMs, it.text) }
            val syncedJson = lyricLinesAdapter.toJson(jsonDtos)
            lyricsDao.saveLyrics(
                CachedLyricsEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    source = data.source.name,
                    isSynced = data.isSynced,
                    plainLyrics = data.plainLyrics,
                    syncedLyricsJson = syncedJson
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error caching lyrics: ${e.message}")
        }
    }

    private fun parseSyncedJson(json: String): List<LyricLine> {
        if (json.isBlank()) return emptyList()
        return try {
            val dtos = lyricLinesAdapter.fromJson(json)
            dtos?.filter { it.text.isNotBlank() }?.map { LyricLine(it.timeMs, it.text.trim()) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2}(?:\\.\\d{1,3})?)\\](.*)")

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val secondsStr = matcher.group(2) ?: "0"
                val text = matcher.group(3)?.trim() ?: ""

                if (text.isNotBlank()) {
                    val seconds = secondsStr.toDoubleOrNull() ?: 0.0
                    val totalMs = (minutes * 60 * 1000) + (seconds * 1000).toLong()
                    lines.add(LyricLine(timeMs = totalMs, text = text))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[feat\\..*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("feat\\..*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("ft\\..*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(audio\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(video\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(remaster.*?\\)", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
