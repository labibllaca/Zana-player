package com.labix

import com.labix.navirom.data.model.NaviromTrack
import com.labix.navirom.ui.util.TrackOrderingHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackOrderingHelperTest {

    private fun createTrack(
        id: String,
        title: String,
        path: String,
        trackNumber: Int? = null,
        discNumber: Int? = null
    ): NaviromTrack {
        return NaviromTrack(
            id = id,
            title = title,
            artist = "Die drei ???",
            album = "100 - Toteninsel",
            path = path,
            trackNumber = trackNumber,
            discNumber = discNumber
        )
    }

    @Test
    fun testAudioDramaWithSubfoldersAndConflictingMetadata() {
        // CD 1 and CD 2 both have discNumber=1 and trackNumber=1 in ID3 tags (common tagging error in Hörspiele)
        val t1 = createTrack("1", "Der Fluch", "Hörspiele/Die drei ???/100/CD 1/01 - Der Fluch.mp3", trackNumber = 1, discNumber = 1)
        val t2 = createTrack("2", "Das Rätsel", "Hörspiele/Die drei ???/100/CD 1/02 - Das Rätsel.mp3", trackNumber = 2, discNumber = 1)
        val t3 = createTrack("3", "Die Falle", "Hörspiele/Die drei ???/100/CD 2/01 - Die Falle.mp3", trackNumber = 1, discNumber = 1)
        val t4 = createTrack("4", "Das Finale", "Hörspiele/Die drei ???/100/CD 2/02 - Das Finale.mp3", trackNumber = 2, discNumber = 1)

        val unsorted = listOf(t4, t2, t3, t1)
        val sorted = TrackOrderingHelper.sortAlbumTracks(unsorted)

        assertEquals(listOf("1", "2", "3", "4"), sorted.map { it.id })
    }

    @Test
    fun testAudioDramaWithRootAndSubfolderCD2() {
        // Part 1 is in album root folder, Part 2 is in CD 2 subfolder
        val t1 = createTrack("1", "Teil A 01", "Hörspiele/TKKG/001/01.mp3", trackNumber = 1, discNumber = null)
        val t2 = createTrack("2", "Teil A 02", "Hörspiele/TKKG/001/02.mp3", trackNumber = 2, discNumber = null)
        val t3 = createTrack("3", "Teil B 01", "Hörspiele/TKKG/001/CD 2/01.mp3", trackNumber = 1, discNumber = null)
        val t4 = createTrack("4", "Teil B 02", "Hörspiele/TKKG/001/CD 2/02.mp3", trackNumber = 2, discNumber = null)

        val unsorted = listOf(t3, t1, t4, t2)
        val sorted = TrackOrderingHelper.sortAlbumTracks(unsorted)

        assertEquals(listOf("1", "2", "3", "4"), sorted.map { it.id })
    }

    @Test
    fun testAudioDramaWithGermanSubfolderNamesTeil1AndTeil2() {
        val t1 = createTrack("1", "Prolog", "Audiobooks/Sherlock/Teil 1/01 - Prolog.mp3", trackNumber = null, discNumber = null)
        val t2 = createTrack("2", "Ermittlung", "Audiobooks/Sherlock/Teil 1/02 - Ermittlung.mp3", trackNumber = null, discNumber = null)
        val t3 = createTrack("3", "Verfolgung", "Audiobooks/Sherlock/Teil 2/01 - Verfolgung.mp3", trackNumber = null, discNumber = null)
        val t4 = createTrack("4", "Auflösung", "Audiobooks/Sherlock/Teil 2/02 - Auflösung.mp3", trackNumber = null, discNumber = null)

        val unsorted = listOf(t3, t2, t4, t1)
        val sorted = TrackOrderingHelper.sortAlbumTracks(unsorted)

        assertEquals(listOf("1", "2", "3", "4"), sorted.map { it.id })
    }

    @Test
    fun testAudioDramaWithWrongMetadataTrackNumbers() {
        // ID3 tags have inverted/wrong track numbers, but filenames have clear 01, 02 prefixes
        val t1 = createTrack("1", "Track One", "Hörspiele/Point Whitmark/01 - Der Leuchtturm.mp3", trackNumber = 5, discNumber = 1)
        val t2 = createTrack("2", "Track Two", "Hörspiele/Point Whitmark/02 - Die Kammer.mp3", trackNumber = 1, discNumber = 1)

        val unsorted = listOf(t2, t1)
        val sorted = TrackOrderingHelper.sortAlbumTracks(unsorted)

        assertEquals(listOf("1", "2"), sorted.map { it.id })
    }

    @Test
    fun testNaturalOrderingNumbers() {
        val t2 = createTrack("2", "Episode 2", "Hörspiele/Serie/Teil 2/01.mp3")
        val t10 = createTrack("10", "Episode 10", "Hörspiele/Serie/Teil 10/01.mp3")

        val sorted = TrackOrderingHelper.sortAlbumTracks(listOf(t10, t2))
        assertEquals(listOf("2", "10"), sorted.map { it.id })
    }
}
