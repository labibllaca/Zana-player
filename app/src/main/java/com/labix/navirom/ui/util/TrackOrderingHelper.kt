package com.labix.navirom.ui.util

import com.labix.navirom.data.model.NaviromTrack

/**
 * Intelligent natural-order sorting helper for tracks, especially audio dramas (Hörspiele),
 * multi-disc albums, and audiobooks organized across different sub-folders (CD1, CD2, Teil 1, etc.)
 * or with inconsistent ID3 meta-tags.
 */
object TrackOrderingHelper {

    /**
     * Natural string comparator that compares numbers numerically (e.g. "CD 2" < "CD 10", "1" < "2" < "10")
     * and text case-insensitively.
     */
    val NaturalStringComparator: Comparator<String> = Comparator { strA, strB ->
        val a = strA ?: ""
        val b = strB ?: ""
        var ia = 0
        var ib = 0
        val la = a.length
        val lb = b.length

        while (ia < la && ib < lb) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                var endA = ia
                while (endA < la && a[endA].isDigit()) endA++
                var endB = ib
                while (endB < lb && b[endB].isDigit()) endB++

                val numStrA = a.substring(ia, endA)
                val numStrB = b.substring(ib, endB)

                val trimmedA = numStrA.trimStart('0')
                val trimmedB = numStrB.trimStart('0')

                if (trimmedA.length != trimmedB.length) {
                    return@Comparator trimmedA.length.compareTo(trimmedB.length)
                }
                val cmp = trimmedA.compareTo(trimmedB)
                if (cmp != 0) return@Comparator cmp

                // If values are numerically equal (e.g. "01" vs "1"), shorter (fewer leading zeros) first
                if (numStrA.length != numStrB.length) {
                    return@Comparator numStrA.length.compareTo(numStrB.length)
                }

                ia = endA
                ib = endB
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return@Comparator cmp
                ia++
                ib++
            }
        }
        la.compareTo(lb)
    }

    private val DISC_PATTERNS = listOf(
        Regex("""(?i)(?:cd|disc|disque|disk|teil|part|vol|volume|kapitel|folge)[\s._\-–]*([0-9]+)"""),
        Regex("""(?i)(?:seite|side)[\s._\-–]*([a-f0-9]+)"""),
        Regex("""^([0-9]+)$""")
    )

    private val LEADING_TRACK_NUM_PATTERN = Regex("""^(\d{1,4})[\s._\-–]+""")
    private val TRACK_KEYWORD_PATTERN = Regex("""(?i)track[\s._\-–]*(\d+)""")

    /**
     * Finds the common root directory segments among all tracks in an album that have directory paths.
     */
    fun findCommonDirectorySegments(tracks: List<NaviromTrack>): List<String> {
        val pathsWithDirs = tracks.mapNotNull { track ->
            val norm = track.path.replace('\\', '/').trim()
            if (norm.contains('/')) {
                val dir = norm.substringBeforeLast('/')
                dir.split('/').filter { it.isNotEmpty() }
            } else null
        }
        if (pathsWithDirs.isEmpty()) return emptyList()

        val first = pathsWithDirs.first()
        var commonLen = first.size
        for (i in 1 until pathsWithDirs.size) {
            val current = pathsWithDirs[i]
            commonLen = minOf(commonLen, current.size)
            for (j in 0 until commonLen) {
                if (first[j] != current[j]) {
                    commonLen = j
                    break
                }
            }
            if (commonLen == 0) break
        }
        return first.take(commonLen)
    }

    /**
     * Extracts disc/part number from path segments, title, or metadata.
     * Path segments are prioritized over conflicting metadata because audio dramas
     * are physically placed into sub-folders while ID3 tags are often default/stale.
     */
    fun extractDiscNumber(
        track: NaviromTrack,
        relSegments: List<String>
    ): Int {
        // 1. Check relative directory segments (e.g. "CD 2", "Teil 2")
        for (seg in relSegments.reversed()) {
            for (pattern in DISC_PATTERNS) {
                val match = pattern.find(seg.trim())
                if (match != null) {
                    val numStr = match.groupValues.getOrNull(1) ?: match.value
                    val num = numStr.toIntOrNull()
                    if (num != null && num > 0) return num
                    if (numStr.length == 1) {
                        val ch = numStr[0].lowercaseChar()
                        if (ch in 'a'..'f') return (ch - 'a') + 1
                    }
                }
            }
        }

        // 2. Check full path segments if relative had no match
        val normPath = track.path.replace('\\', '/')
        if (normPath.isNotBlank()) {
            val segments = normPath.split('/').filter { it.isNotBlank() }
            for (i in (segments.size - 2) downTo 0) {
                val seg = segments[i].trim()
                for (pattern in DISC_PATTERNS) {
                    val match = pattern.find(seg)
                    if (match != null) {
                        val numStr = match.groupValues.getOrNull(1) ?: match.value
                        val num = numStr.toIntOrNull()
                        if (num != null && num > 0) return num
                        if (numStr.length == 1) {
                            val ch = numStr[0].lowercaseChar()
                            if (ch in 'a'..'f') return (ch - 'a') + 1
                        }
                    }
                }
            }
        }

        // 3. Metadata discNumber if > 0
        if (track.discNumber != null && track.discNumber > 0) {
            return track.discNumber
        }

        // 4. Check track title or filename
        for (pattern in DISC_PATTERNS) {
            val match = pattern.find(track.title) ?: normPath.let { pattern.find(it.substringAfterLast('/')) }
            if (match != null) {
                val numStr = match.groupValues.getOrNull(1) ?: match.value
                val num = numStr.toIntOrNull()
                if (num != null && num > 0) return num
                if (numStr.length == 1) {
                    val ch = numStr[0].lowercaseChar()
                    if (ch in 'a'..'f') return (ch - 'a') + 1
                }
            }
        }

        return 1
    }

    /**
     * Extracts a track number using filename first (if physical files are numbered like 01 - ...),
     * then metadata trackNumber, then title.
     */
    fun extractTrackNumber(track: NaviromTrack, filename: String): Int? {
        val nameWithoutExt = if (filename.isNotBlank()) filename.substringBeforeLast('.') else ""

        // 1. Filename leading number (e.g. 01 - Track.mp3, 01.mp3)
        if (nameWithoutExt.isNotBlank()) {
            val matchLead = LEADING_TRACK_NUM_PATTERN.find(nameWithoutExt)
            if (matchLead != null) {
                val numStr = matchLead.groupValues.getOrNull(1) ?: matchLead.value
                val num = numStr.toIntOrNull()
                if (num != null && num > 0) return num
            }

            val matchKeyword = TRACK_KEYWORD_PATTERN.find(nameWithoutExt)
            if (matchKeyword != null) {
                val numStr = matchKeyword.groupValues.getOrNull(1) ?: matchKeyword.value
                val num = numStr.toIntOrNull()
                if (num != null && num > 0) return num
            }
        }

        // 2. Metadata trackNumber if valid
        if (track.trackNumber != null && track.trackNumber > 0) {
            return track.trackNumber
        }

        // 3. Title leading number
        val matchTitleLead = LEADING_TRACK_NUM_PATTERN.find(track.title)
        if (matchTitleLead != null) {
            val numStr = matchTitleLead.groupValues.getOrNull(1) ?: matchTitleLead.value
            val num = numStr.toIntOrNull()
            if (num != null && num > 0) return num
        }

        return null
    }

    /**
     * Sorts tracks for an album or audio drama (Hörspiel) respecting subfolder structures,
     * disc numbers, track numbers, and natural filename sequences.
     */
    fun sortAlbumTracks(tracks: List<NaviromTrack>): List<NaviromTrack> {
        if (tracks.size <= 1) return tracks

        val commonSegments = findCommonDirectorySegments(tracks)
        val commonLen = commonSegments.size

        data class TrackSortMeta(
            val track: NaviromTrack,
            val disc: Int,
            val relDir: String,
            val trackNum: Int,
            val filename: String,
            val title: String
        )

        val metaList = tracks.map { track ->
            val normPath = track.path.replace('\\', '/').trim()
            val filename = if (normPath.isNotBlank()) normPath.substringAfterLast('/') else track.title
            val dir = if (normPath.contains('/')) normPath.substringBeforeLast('/') else ""
            val dirSegments = if (dir.isNotEmpty()) dir.split('/').filter { it.isNotEmpty() } else emptyList()
            val relSegments = if (dirSegments.size >= commonLen) dirSegments.drop(commonLen) else dirSegments
            val relDir = relSegments.joinToString("/")

            val disc = extractDiscNumber(track, relSegments)
            val trackNum = extractTrackNumber(track, filename) ?: 0

            TrackSortMeta(
                track = track,
                disc = disc,
                relDir = relDir,
                trackNum = trackNum,
                filename = filename,
                title = track.title
            )
        }

        val sortedMeta = metaList.sortedWith { m1, m2 ->
            // 1. Disc Number (from sub-folder or disc tags)
            if (m1.disc != m2.disc) {
                return@sortedWith m1.disc.compareTo(m2.disc)
            }

            // 2. Relative Directory: root folder ("") comes first, then natural subfolder sequence
            if (m1.relDir != m2.relDir) {
                if (m1.relDir.isEmpty()) return@sortedWith -1
                if (m2.relDir.isEmpty()) return@sortedWith 1
                val cmp = NaturalStringComparator.compare(m1.relDir, m2.relDir)
                if (cmp != 0) return@sortedWith cmp
            }

            // 3. Track number within the same subfolder/disc
            if (m1.trackNum > 0 && m2.trackNum > 0 && m1.trackNum != m2.trackNum) {
                return@sortedWith m1.trackNum.compareTo(m2.trackNum)
            }

            // 4. Filename naturally
            if (m1.filename.isNotBlank() && m2.filename.isNotBlank() && m1.filename != m2.filename) {
                val cmp = NaturalStringComparator.compare(m1.filename, m2.filename)
                if (cmp != 0) return@sortedWith cmp
            }

            // 5. Fallback track number
            if (m1.trackNum != m2.trackNum) {
                return@sortedWith m1.trackNum.compareTo(m2.trackNum)
            }

            // 6. Title naturally
            val cmpTitle = NaturalStringComparator.compare(m1.title, m2.title)
            if (cmpTitle != 0) return@sortedWith cmpTitle

            // 7. Stable ID fallback
            m1.track.id.compareTo(m2.track.id)
        }

        return sortedMeta.map { it.track }
    }
}
