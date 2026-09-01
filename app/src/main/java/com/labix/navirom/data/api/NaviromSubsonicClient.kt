package com.labix.navirom.data.api

import android.util.Log
import com.labix.navirom.data.api.dto.SubsonicRootResponse
import com.labix.navirom.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.labix.navirom.diagnostics.AppDiagnostics
import com.labix.navirom.diagnostics.DiagnosticCodes

class NaviromSubsonicClient(
    private val okHttpClient: OkHttpClient = HttpClientProvider.client
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val rootAdapter = moshi.adapter(SubsonicRootResponse::class.java)

    var serverUrl: String = ""
    var alternativeServerUrl: String = ""

    @Volatile
    var activeServerUrl: String = ""

    var username: String = ""
    var password: String = ""
    var useTokenAuth: Boolean = true
    var clientName: String = "Navirom"
    var apiVersion: String = "1.16.1"
    var activeMusicFolderId: String? = null

    fun configure(
        serverUrl: String,
        username: String,
        password: String,
        useTokenAuth: Boolean = true,
        alternativeServerUrl: String = ""
    ) {
        var cleanUrl = serverUrl.trim()
        if (cleanUrl.isNotEmpty()) {
            if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                cleanUrl = "http://$cleanUrl"
            }
            cleanUrl = cleanUrl.removeSuffix("/")
        }
        this.serverUrl = cleanUrl

        var cleanAltUrl = alternativeServerUrl.trim()
        if (cleanAltUrl.isNotEmpty()) {
            if (!cleanAltUrl.startsWith("http://", ignoreCase = true) && !cleanAltUrl.startsWith("https://", ignoreCase = true)) {
                val proto = if (cleanUrl.startsWith("https://", ignoreCase = true)) "https" else "http"
                cleanAltUrl = "$proto://$cleanAltUrl"
            }
            cleanAltUrl = cleanAltUrl.removeSuffix("/")
        }
        this.alternativeServerUrl = cleanAltUrl

        this.username = username.trim()
        this.password = password
        this.useTokenAuth = useTokenAuth

        // Default active server URL to primary if not set or invalid
        if (this.activeServerUrl.isBlank() || (this.activeServerUrl != this.serverUrl && this.activeServerUrl != this.alternativeServerUrl)) {
            this.activeServerUrl = if (this.serverUrl.isNotEmpty()) this.serverUrl else this.alternativeServerUrl
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildUrl(endpoint: String, queryParams: Map<String, String> = emptyMap()): HttpUrl? {
        val baseUrlToUse = if (activeServerUrl.isNotBlank()) activeServerUrl else serverUrl
        if (baseUrlToUse.isBlank()) return null
        if (username.isBlank()) {
            Log.w("SubsonicClient", "Cannot build URL for '$endpoint' because username is empty")
            return null
        }
        val fullEndpointUrl = "${baseUrlToUse.removeSuffix("/")}/rest/$endpoint"
        val httpUrlBuilder = fullEndpointUrl.toHttpUrlOrNull()?.newBuilder() ?: return null

        httpUrlBuilder.addQueryParameter("u", username)
        httpUrlBuilder.addQueryParameter("v", apiVersion)
        httpUrlBuilder.addQueryParameter("c", clientName)
        httpUrlBuilder.addQueryParameter("f", "json")

        if (useTokenAuth) {
            val salt = UUID.randomUUID().toString().replace("-", "").take(12)
            val token = md5(password + salt)
            httpUrlBuilder.addQueryParameter("t", token)
            httpUrlBuilder.addQueryParameter("s", salt)
        } else {
            httpUrlBuilder.addQueryParameter("p", password)
        }

        queryParams.forEach { (key, value) ->
            httpUrlBuilder.addQueryParameter(key, value)
        }

        return httpUrlBuilder.build()
    }

    
    fun resolveUrl(url: String): String {
        if (url.isBlank()) return ""
        val activePrefix = activeServerUrl
        if (activePrefix.isBlank()) return url
        
        val primary = serverUrl
        val alt = alternativeServerUrl
        
        if (primary.isNotBlank() && url.startsWith(primary) && activePrefix != primary) {
            return url.replaceFirst(primary, activePrefix)
        }
        if (alt.isNotBlank() && url.startsWith(alt) && activePrefix != alt) {
            return url.replaceFirst(alt, activePrefix)
        }
        return url
    }

    fun getStreamUrl(trackId: String): String {
        val url = buildUrl("stream.view", mapOf("id" to trackId))
        return url?.toString() ?: ""
    }

    fun getCoverArtUrl(coverArtId: String, size: Int = 500): String {
        if (coverArtId.isBlank()) return ""
        val url = buildUrl("getCoverArt.view", mapOf("id" to coverArtId, "size" to size.toString()))
        return url?.toString() ?: ""
    }

    private fun getAlternativeUrl(): String? {
        return if (activeServerUrl == serverUrl) {
            if (alternativeServerUrl.isNotBlank()) alternativeServerUrl else null
        } else {
            if (serverUrl.isNotBlank()) serverUrl else null
        }
    }

    private suspend fun <T> executeRequest(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        transform: (SubsonicRootResponse) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        var url = buildUrl(endpoint, params)
        if (url == null) {
            val altUrl = getAlternativeUrl()
            if (altUrl != null && altUrl != activeServerUrl) {
                activeServerUrl = altUrl
                url = buildUrl(endpoint, params)
            }
        }

        if (url == null) {
            return@withContext Result.failure(IllegalArgumentException("Username or server URL is not configured for $endpoint"))
        }

        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val altUrl = getAlternativeUrl()
                    if (altUrl != null && altUrl != activeServerUrl) {
                        Log.w("SubsonicClient", "HTTP ${response.code} for '$endpoint', trying alternative: $altUrl")
                        activeServerUrl = altUrl
                        val retryUrl = buildUrl(endpoint, params)
                        if (retryUrl != null) {
                            try {
                                val retryRequest = Request.Builder().url(retryUrl).build()
                                okHttpClient.newCall(retryRequest).execute().use { retryResponse ->
                                    if (retryResponse.isSuccessful) {
                                        val bodyString = retryResponse.body?.string()
                                        if (bodyString != null) {
                                            val root = rootAdapter.fromJson(bodyString)
                                            val sub = root?.subsonicResponse
                                            if (sub != null && sub.status == "ok") {
                                                return@withContext Result.success(transform(root))
                                            }
                                        }
                                    }
                                }
                            } catch (re: Exception) {
                                Log.e("SubsonicClient", "Retry on alternative URL failed during HTTP error", re)
                            }
                        }
                    }
                    val msg = "HTTP ${response.code} for endpoint '$endpoint'"
                    AppDiagnostics.logError(DiagnosticCodes.NET_HTTP_ERROR_202, "SubsonicClient", msg, contextInfo = "URL: $url")
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val bodyString = response.body?.string()
                if (bodyString == null) {
                    AppDiagnostics.logError(DiagnosticCodes.NET_HTTP_ERROR_202, "SubsonicClient", "Empty body for endpoint '$endpoint'")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                val root = rootAdapter.fromJson(bodyString)
                if (root == null) {
                    AppDiagnostics.logError(DiagnosticCodes.NET_JSON_PARSE_203, "SubsonicClient", "Failed to parse JSON for endpoint '$endpoint'")
                    return@withContext Result.failure(Exception("Failed to parse JSON"))
                }

                val subResponse = root.subsonicResponse
                if (subResponse == null) {
                    AppDiagnostics.logError(DiagnosticCodes.NET_JSON_PARSE_203, "SubsonicClient", "Missing Subsonic response object for '$endpoint'")
                    return@withContext Result.failure(Exception("Missing Subsonic response object"))
                }

                if (subResponse.status != "ok") {
                    val altUrl = getAlternativeUrl()
                    if (altUrl != null && altUrl != activeServerUrl) {
                        Log.w("SubsonicClient", "Subsonic status not ok for '$endpoint', trying alternative: $altUrl")
                        activeServerUrl = altUrl
                        val retryUrl = buildUrl(endpoint, params)
                        if (retryUrl != null) {
                            try {
                                val retryRequest = Request.Builder().url(retryUrl).build()
                                okHttpClient.newCall(retryRequest).execute().use { retryResponse ->
                                    if (retryResponse.isSuccessful) {
                                        val retryBody = retryResponse.body?.string()
                                        if (retryBody != null) {
                                            val retryRoot = rootAdapter.fromJson(retryBody)
                                            val retrySub = retryRoot?.subsonicResponse
                                            if (retrySub != null && retrySub.status == "ok") {
                                                return@withContext Result.success(transform(retryRoot))
                                            }
                                        }
                                    }
                                }
                            } catch (re: Exception) {
                                Log.e("SubsonicClient", "Retry on alternative URL failed after status error", re)
                            }
                        }
                    }
                    val errorMsg = subResponse.error?.message ?: "Subsonic Error Code ${subResponse.error?.code ?: -1}"
                    AppDiagnostics.logError(DiagnosticCodes.NET_HTTP_ERROR_202, "SubsonicClient", "Subsonic API Error on '$endpoint': $errorMsg", contextInfo = "Code: ${subResponse.error?.code}")
                    return@withContext Result.failure(Exception(errorMsg))
                }

                Result.success(transform(root))
            }
        } catch (e: Exception) {
            val altUrl = getAlternativeUrl()
            if (altUrl != null && altUrl != activeServerUrl) {
                Log.w("SubsonicClient", "Request failed: ${e.message}, trying alternative: $altUrl")
                activeServerUrl = altUrl
                val retryUrl = buildUrl(endpoint, params)
                if (retryUrl != null) {
                    try {
                        val retryRequest = Request.Builder().url(retryUrl).build()
                        okHttpClient.newCall(retryRequest).execute().use { retryResponse ->
                            if (retryResponse.isSuccessful) {
                                val bodyString = retryResponse.body?.string()
                                if (bodyString != null) {
                                    val root = rootAdapter.fromJson(bodyString)
                                    val sub = root?.subsonicResponse
                                    if (sub != null && sub.status == "ok") {
                                        return@withContext Result.success(transform(root))
                                    }
                                }
                            }
                        }
                    } catch (re: Exception) {
                        Log.e("SubsonicClient", "Retry on alternative URL failed during exception", re)
                    }
                }
            }
            AppDiagnostics.logError(DiagnosticCodes.NET_CONNECT_FAIL_201, "SubsonicClient", "Network request failed for endpoint '$endpoint': ${e.message}", e, contextInfo = "URL: $url")
            Result.failure(e)
        }
    }

    suspend fun ping(): Result<String> {
        return executeRequest("ping.view") { root ->
            val resp = root.subsonicResponse
            "Connected (v${resp?.version ?: "1.16.1"}, ${resp?.type ?: "Navidrome"} ${resp?.serverVersion ?: ""})"
        }
    }

    suspend fun probeServer(candidateUrl: String, timeoutMs: Long = 1500L): Boolean = withContext(Dispatchers.IO) {
        var cleanUrl = candidateUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://$cleanUrl"
        }
        cleanUrl = cleanUrl.removeSuffix("/")
        val pingUrl = "$cleanUrl/rest/ping.view?u=probe&v=1.16.1&c=Navirom&f=json".toHttpUrlOrNull() ?: return@withContext false

        try {
            val probeClient = okHttpClient.newBuilder()
                .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder().url(pingUrl).build()
            probeClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                body.contains("subsonic-response", ignoreCase = true) || (response.isSuccessful && body.contains("status"))
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getMusicFolders(): Result<List<com.labix.navirom.data.api.dto.MusicFolderDto>> {
        return executeRequest("getMusicFolders.view") { root ->
            root.subsonicResponse?.musicFolders?.musicFolder ?: emptyList()
        }
    }

    suspend fun getAlbums(type: String = "newest", size: Int = 100): Result<List<NaviromAlbum>> {
        val params = mutableMapOf("type" to type, "size" to size.toString())
        activeMusicFolderId?.let { params["musicFolderId"] = it }
        return executeRequest("getAlbumList2.view", params) { root ->
            val albums = root.subsonicResponse?.albumList2?.album ?: emptyList()
            albums.map { dto ->
                NaviromAlbum(
                    id = dto.id,
                    name = dto.name.ifBlank { dto.title ?: "Untitled Album" },
                    artist = dto.artist ?: "Unknown Artist",
                    artistId = dto.artistId ?: "",
                    coverArt = dto.coverArt ?: "",
                    coverArtUrl = getCoverArtUrl(dto.coverArt ?: dto.id),
                    songCount = dto.songCount ?: 0,
                    durationSeconds = dto.duration ?: 0,
                    year = dto.year,
                    genre = dto.genre ?: ""
                )
            }
        }
    }

    suspend fun getAlbumDetails(albumId: String): Result<Pair<NaviromAlbum, List<NaviromTrack>>> {
        return executeRequest("getAlbum.view", mapOf("id" to albumId)) { root ->
            val albumDto = root.subsonicResponse?.album ?: throw IllegalStateException("Album not found")
            val album = NaviromAlbum(
                id = albumDto.id,
                name = albumDto.name,
                artist = albumDto.artist ?: "Unknown Artist",
                artistId = albumDto.artistId ?: "",
                coverArt = albumDto.coverArt ?: "",
                coverArtUrl = getCoverArtUrl(albumDto.coverArt ?: albumDto.id),
                songCount = albumDto.songCount ?: albumDto.song.size,
                durationSeconds = albumDto.duration ?: 0,
                year = albumDto.year,
                genre = albumDto.genre ?: ""
            )

            val tracks = albumDto.song.map { songDto ->
                NaviromTrack(
                    id = songDto.id,
                    title = songDto.title.ifBlank { "Untitled Track" },
                    artist = songDto.artist ?: album.artist,
                    artistId = songDto.artistId ?: album.artistId,
                    album = songDto.album ?: album.name,
                    albumId = songDto.albumId ?: album.id,
                    durationSeconds = songDto.duration ?: 0,
                    coverArtId = songDto.coverArt ?: album.coverArt,
                    coverArtUrl = getCoverArtUrl(songDto.coverArt ?: songDto.id),
                    streamUrl = getStreamUrl(songDto.id),
                    path = songDto.path ?: "",
                    year = songDto.year ?: album.year,
                    genre = songDto.genre ?: album.genre,
                    bitRate = songDto.bitRate,
                    suffix = songDto.suffix ?: "mp3",
                    trackNumber = songDto.track,
                    isFavorite = songDto.starred != null,
                    sizeBytes = songDto.size ?: 0L
                )
            }

            Pair(album, tracks)
        }
    }

    suspend fun getArtists(): Result<List<NaviromArtist>> {
        val params = mutableMapOf<String, String>()
        activeMusicFolderId?.let { params["musicFolderId"] = it }
        return executeRequest("getArtists.view", params) { root ->
            val indices = root.subsonicResponse?.artists?.index ?: emptyList()
            val list = mutableListOf<NaviromArtist>()
            for (idx in indices) {
                for (artist in idx.artist) {
                    list.add(
                        NaviromArtist(
                            id = artist.id,
                            name = artist.name,
                            coverArt = artist.coverArt ?: "",
                            coverArtUrl = getCoverArtUrl(artist.coverArt ?: artist.id),
                            albumCount = artist.albumCount ?: 0
                        )
                    )
                }
            }
            list.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun getArtistDetails(artistId: String): Result<Pair<NaviromArtist, List<NaviromAlbum>>> {
        val params = mutableMapOf("id" to artistId)
        return executeRequest("getArtist.view", params) { root ->
            val artistDto = root.subsonicResponse?.artist ?: throw Exception("Artist details not found")
            val artist = NaviromArtist(
                id = artistDto.id,
                name = artistDto.name,
                coverArt = artistDto.coverArt ?: "",
                coverArtUrl = getCoverArtUrl(artistDto.coverArt ?: artistDto.id),
                albumCount = artistDto.albumCount ?: 0
            )
            val albums = artistDto.album.map { dto ->
                NaviromAlbum(
                    id = dto.id,
                    name = dto.name.ifBlank { dto.title ?: "" },
                    artist = dto.artist ?: artist.name,
                    artistId = dto.artistId ?: artist.id,
                    coverArt = dto.coverArt ?: "",
                    coverArtUrl = getCoverArtUrl(dto.coverArt ?: dto.id),
                    songCount = dto.songCount ?: 0,
                    year = dto.year
                )
            }
            Pair(artist, albums)
        }
    }

    suspend fun getPlaylists(): Result<List<NaviromPlaylist>> {
        return executeRequest("getPlaylists.view") { root ->
            val list = root.subsonicResponse?.playlists?.playlist ?: emptyList()
            list.map { dto ->
                NaviromPlaylist(
                    id = dto.id,
                    name = dto.name,
                    comment = dto.comment ?: "",
                    songCount = dto.songCount,
                    durationSeconds = dto.duration,
                    isPublic = dto.public ?: false,
                    created = dto.created ?: "",
                    coverArt = dto.coverArt ?: "",
                    isLocal = false
                )
            }
        }
    }

    suspend fun getPlaylistDetails(playlistId: String): Result<Pair<NaviromPlaylist, List<NaviromTrack>>> {
        return executeRequest("getPlaylist.view", mapOf("id" to playlistId)) { root ->
            val pDto = root.subsonicResponse?.playlist ?: throw IllegalStateException("Playlist not found")
            val playlist = NaviromPlaylist(
                id = pDto.id,
                name = pDto.name,
                comment = pDto.comment ?: "",
                songCount = pDto.songCount,
                durationSeconds = pDto.duration,
                isPublic = pDto.public ?: false,
                coverArt = pDto.coverArt ?: "",
                isLocal = false
            )

            val tracks = pDto.entry.map { songDto ->
                NaviromTrack(
                    id = songDto.id,
                    title = songDto.title.ifBlank { "Untitled Track" },
                    artist = songDto.artist ?: "Unknown Artist",
                    artistId = songDto.artistId ?: "",
                    album = songDto.album ?: "Unknown Album",
                    albumId = songDto.albumId ?: "",
                    durationSeconds = songDto.duration ?: 0,
                    coverArtId = songDto.coverArt ?: "",
                    coverArtUrl = getCoverArtUrl(songDto.coverArt ?: songDto.id),
                    streamUrl = getStreamUrl(songDto.id),
                    year = songDto.year,
                    genre = songDto.genre ?: "",
                    suffix = songDto.suffix ?: "mp3",
                    trackNumber = songDto.track,
                    isFavorite = songDto.starred != null,
                    sizeBytes = songDto.size ?: 0L
                )
            }

            Pair(playlist, tracks)
        }
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<String> {
        val params = mutableMapOf("name" to name)
        // Add songs if specified
        return executeRequest("createPlaylist.view", params) { root ->
            "Playlist created"
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        return executeRequest("deletePlaylist.view", mapOf("id" to playlistId)) {
            Unit
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        songIdToAdd: String? = null,
        songIndexToRemove: Int? = null
    ): Result<Unit> {
        val params = mutableMapOf("playlistId" to playlistId)
        songIdToAdd?.let { params["songIdToAdd"] = it }
        songIndexToRemove?.let { params["songIndexToRemove"] = it.toString() }
        return executeRequest("updatePlaylist.view", params) {
            Unit
        }
    }

    private fun parseSearchResult(result: com.labix.navirom.data.api.dto.SearchResult3Dto?): Triple<List<NaviromArtist>, List<NaviromAlbum>, List<NaviromTrack>> {
        val artists = (result?.artist ?: emptyList()).map {
            NaviromArtist(
                id = it.id,
                name = it.name,
                coverArt = it.coverArt ?: "",
                coverArtUrl = getCoverArtUrl(it.coverArt ?: it.id),
                albumCount = it.albumCount ?: 0
            )
        }
        val albums = (result?.album ?: emptyList()).map {
            NaviromAlbum(
                id = it.id,
                name = it.name.ifBlank { it.title ?: "Untitled" },
                artist = it.artist ?: "Unknown Artist",
                artistId = it.artistId ?: "",
                coverArt = it.coverArt ?: "",
                coverArtUrl = getCoverArtUrl(it.coverArt ?: it.id),
                songCount = it.songCount ?: 0,
                durationSeconds = it.duration ?: 0,
                year = it.year,
                genre = it.genre ?: ""
            )
        }
        val tracks = (result?.song ?: emptyList()).map {
            NaviromTrack(
                id = it.id,
                title = it.title,
                artist = it.artist ?: "Unknown Artist",
                artistId = it.artistId ?: "",
                album = it.album ?: "Unknown Album",
                albumId = it.albumId ?: "",
                durationSeconds = it.duration ?: 0,
                coverArtId = it.coverArt ?: "",
                coverArtUrl = getCoverArtUrl(it.coverArt ?: it.id),
                streamUrl = getStreamUrl(it.id),
                year = it.year,
                genre = it.genre ?: "",
                suffix = it.suffix ?: "mp3",
                isFavorite = it.starred != null
            )
        }
        return Triple(artists, albums, tracks)
    }

    suspend fun search(query: String): Result<Triple<List<NaviromArtist>, List<NaviromAlbum>, List<NaviromTrack>>> {
        val params = mutableMapOf("query" to query, "songCount" to "50", "albumCount" to "20", "artistCount" to "20")
        activeMusicFolderId?.let { params["musicFolderId"] = it }

        val res3 = executeRequest("search3.view", params) { root ->
            val sub = root.subsonicResponse
            parseSearchResult(sub?.searchResult3 ?: sub?.searchResult2 ?: sub?.searchResult)
        }

        if (res3.isSuccess) {
            val data = res3.getOrNull()
            if (data != null && (data.first.isNotEmpty() || data.second.isNotEmpty() || data.third.isNotEmpty())) {
                return res3
            }
        }

        // Fallback attempt to search2.view if search3 returned empty or errored
        val res2 = executeRequest("search2.view", params) { root ->
            val sub = root.subsonicResponse
            parseSearchResult(sub?.searchResult2 ?: sub?.searchResult3 ?: sub?.searchResult)
        }
        if (res2.isSuccess) {
            val data = res2.getOrNull()
            if (data != null && (data.first.isNotEmpty() || data.second.isNotEmpty() || data.third.isNotEmpty())) {
                return res2
            }
        }

        return res3
    }

    suspend fun getRandomTracks(size: Int = 30): Result<List<NaviromTrack>> {
        val params = mutableMapOf("size" to size.toString())
        activeMusicFolderId?.let { params["musicFolderId"] = it }
        return executeRequest("getRandomSongs.view", params) { root ->
            val songs = root.subsonicResponse?.randomSongs?.song ?: emptyList()
            songs.map { songDto ->
                NaviromTrack(
                    id = songDto.id,
                    title = songDto.title,
                    artist = songDto.artist ?: "Unknown Artist",
                    artistId = songDto.artistId ?: "",
                    album = songDto.album ?: "Unknown Album",
                    albumId = songDto.albumId ?: "",
                    durationSeconds = songDto.duration ?: 0,
                    coverArtId = songDto.coverArt ?: "",
                    coverArtUrl = getCoverArtUrl(songDto.coverArt ?: songDto.id),
                    streamUrl = getStreamUrl(songDto.id),
                    year = songDto.year,
                    genre = songDto.genre ?: "",
                    suffix = songDto.suffix ?: "mp3",
                    isFavorite = songDto.starred != null
                )
            }
        }
    }

    suspend fun getLibrarySongs(size: Int = 500): Result<List<NaviromTrack>> {
        return getRandomTracks(size = size)
    }

    suspend fun starTrack(trackId: String, star: Boolean): Result<Unit> {
        val endpoint = if (star) "star.view" else "unstar.view"
        return executeRequest(endpoint, mapOf("id" to trackId)) {
            Unit
        }
    }

    suspend fun getLyrics(artist: String, title: String, songId: String? = null): Result<String?> {
        // 1. Try getLyricsBySongId if songId is provided (OpenSubsonic)
        if (!songId.isNullOrBlank()) {
            val byIdResult = try {
                kotlinx.coroutines.withTimeout(3000L) {
                    executeRequest("getLyricsBySongId.view", mapOf("id" to songId)) { root ->
                        val structured = root.subsonicResponse?.lyricsList?.structuredLyrics?.firstOrNull()
                        if (structured != null && structured.line.isNotEmpty()) {
                            // Format into LRC text if structured lines are returned
                            structured.line.joinToString("\n") { item ->
                                val startMs = item.start ?: 0L
                                val min = startMs / 60000
                                val sec = (startMs % 60000) / 1000.0
                                "[%02d:%05.2f]%s".format(min, sec, item.value)
                            }
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (byIdResult.isSuccess && !byIdResult.getOrNull().isNullOrBlank()) {
                return byIdResult
            }
        }

        // 2. Try standard getLyrics.view
        val params = mutableMapOf("artist" to artist, "title" to title)
        return executeRequest("getLyrics.view", params) { root ->
            root.subsonicResponse?.lyrics?.value
        }
    }

    companion object {
        fun buildAlternativeUrl(defaultProtocol: String, alternativeHost: String, defaultPort: String): String {
            val trimmed = alternativeHost.trim()
            if (trimmed.isBlank()) return ""

            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                return trimmed.removeSuffix("/")
            }

            val proto = defaultProtocol.ifBlank { "http" }
            val cleanHost = trimmed.removeSuffix("/")

            return if (cleanHost.contains(":")) {
                "$proto://$cleanHost"
            } else if (defaultPort.isNotBlank() && defaultPort != "80" && defaultPort != "443") {
                "$proto://$cleanHost:$defaultPort"
            } else {
                "$proto://$cleanHost"
            }
        }
    }
}
