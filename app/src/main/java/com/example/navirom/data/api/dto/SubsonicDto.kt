package com.example.navirom.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubsonicRootResponse(
    @Json(name = "subsonic-response") val subsonicResponse: SubsonicResponse?
)

@JsonClass(generateAdapter = true)
data class SubsonicResponse(
    val status: String = "ok",
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicError? = null,
    val ping: Any? = null,
    val scanStatus: ScanStatusDto? = null,
    val artists: ArtistsContainerDto? = null,
    val artist: ArtistDetailDto? = null,
    val albumList2: AlbumListDto? = null,
    val album: AlbumDetailDto? = null,
    val playlists: PlaylistsContainerDto? = null,
    val playlist: PlaylistDetailDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val searchResult2: SearchResult3Dto? = null,
    val searchResult: SearchResult3Dto? = null,
    val randomSongs: SongListDto? = null,
    val genres: GenresContainerDto? = null,
    val musicFolders: MusicFoldersContainerDto? = null,
    val lyrics: LyricsDto? = null,
    val lyricsList: LyricsListDto? = null
)

@JsonClass(generateAdapter = true)
data class SubsonicError(
    val code: Int = 0,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class ScanStatusDto(
    val scanning: Boolean = false,
    val count: Long = 0L
)

@JsonClass(generateAdapter = true)
data class ArtistsContainerDto(
    val index: List<ArtistIndexDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArtistIndexDto(
    val name: String = "",
    val artist: List<ArtistItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArtistItemDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int? = 0,
    val artistImageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ArtistDetailDto(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int? = 0,
    val album: List<AlbumItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AlbumListDto(
    val album: List<AlbumItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AlbumItemDto(
    val id: String = "",
    val name: String = "",
    val title: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null
)

@JsonClass(generateAdapter = true)
data class AlbumDetailDto(
    val id: String = "",
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val song: List<SongDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SongDto(
    val id: String = "",
    val parent: String? = null,
    val title: String = "",
    val isDir: Boolean = false,
    val album: String? = null,
    val artist: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val isVideo: Boolean? = false,
    val playCount: Long? = null,
    val created: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val starred: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistsContainerDto(
    val playlist: List<PlaylistItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PlaylistItemDto(
    val id: String = "",
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean? = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val created: String? = null,
    val coverArt: String? = null
)

@JsonClass(generateAdapter = true)
data class PlaylistDetailDto(
    val id: String = "",
    val name: String = "",
    val comment: String? = null,
    val owner: String? = null,
    val public: Boolean? = false,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<SongDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SearchResult3Dto(
    val artist: List<ArtistItemDto> = emptyList(),
    val album: List<AlbumItemDto> = emptyList(),
    val song: List<SongDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SongListDto(
    val song: List<SongDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GenresContainerDto(
    val genre: List<GenreDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GenreDto(
    val value: String = "",
    val songCount: Int = 0,
    val albumCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class MusicFoldersContainerDto(
    val musicFolder: List<MusicFolderDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MusicFolderDto(
    val id: String = "",
    val name: String = ""
)

@JsonClass(generateAdapter = true)
data class LyricsDto(
    val artist: String? = null,
    val title: String? = null,
    val value: String? = null
)

@JsonClass(generateAdapter = true)
data class LyricsListDto(
    val structuredLyrics: List<StructuredLyricsDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StructuredLyricsDto(
    val lang: String? = null,
    val synced: Boolean? = false,
    val line: List<LyricsLineItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LyricsLineItemDto(
    val start: Long? = 0L,
    val value: String = ""
)
