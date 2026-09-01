package com.example.navirom.data.stats

import com.example.navirom.data.local.PlaybackHistoryDao
import com.example.navirom.data.local.PlaybackHistoryEntity
import com.example.navirom.data.model.NaviromTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

data class DayStat(
    val dayIndex: Int, // 1 = Sun, 2 = Mon, 3 = Tue, 4 = Wed, 5 = Thu, 6 = Fri, 7 = Sat
    val nameEn: String,
    val nameSq: String,
    val shortNameEn: String,
    val shortNameSq: String,
    val totalSeconds: Long,
    val percentage: Float
)

data class HourStat(
    val hour: Int, // 0..23
    val label: String,
    val totalSeconds: Long,
    val percentage: Float
)

data class TimeSlotStat(
    val slotKey: String,
    val titleEn: String,
    val titleSq: String,
    val timeRange: String,
    val iconName: String,
    val totalSeconds: Long,
    val percentage: Float
)

data class TopArtistStat(
    val artist: String,
    val totalSeconds: Long,
    val playCount: Int
)

data class TopTrackStat(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val totalSeconds: Long,
    val playCount: Int
)

data class ListeningStatsSummary(
    val totalListeningSeconds: Long = 0L,
    val todayListeningSeconds: Long = 0L,
    val thisWeekListeningSeconds: Long = 0L,
    val totalTracksPlayed: Int = 0,
    val dayOfWeekStats: List<DayStat> = emptyList(),
    val peakDayEn: String = "N/A",
    val peakDaySq: String = "N/A",
    val peakDaySeconds: Long = 0L,
    val hourlyStats: List<HourStat> = emptyList(),
    val peakHourLabel: String = "N/A",
    val peakHourRange: String = "N/A",
    val timeSlots: List<TimeSlotStat> = emptyList(),
    val topArtists: List<TopArtistStat> = emptyList(),
    val topTracks: List<TopTrackStat> = emptyList(),
    val rawHistory: List<PlaybackHistoryEntity> = emptyList()
)

class ListeningStatsManager(
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    val statsFlow: Flow<ListeningStatsSummary> = playbackHistoryDao.getAllHistory().map { history ->
        calculateStats(history)
    }

    suspend fun clearStats() {
        playbackHistoryDao.clearHistory()
    }

    suspend fun recordPlaybackSession(
        track: NaviromTrack,
        listenedSeconds: Long
    ) {
        if (listenedSeconds < 3) return // Ignore instant skips

        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        playbackHistoryDao.insertSession(
            PlaybackHistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                coverArtUrl = track.coverArtUrl,
                durationSeconds = track.durationSeconds,
                listenedSeconds = listenedSeconds,
                timestamp = System.currentTimeMillis(),
                dayOfWeek = dayOfWeek,
                hourOfDay = hourOfDay
            )
        )
    }

    suspend fun seedInitialHistoryIfEmpty(sampleTracks: List<NaviromTrack>) {
        if (sampleTracks.isEmpty()) return
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Generate realistic distribution across last 14 days
        val randomDays = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        for (i in 0 until 28) {
            val track = sampleTracks[i % sampleTracks.size]
            val dayOffset = (i % 7)
            val dayOfWeek = randomDays[dayOffset]
            val hour = when {
                i % 4 == 0 -> 20 // 8 PM evening peak
                i % 4 == 1 -> 21 // 9 PM
                i % 4 == 2 -> 14 // 2 PM afternoon
                else -> 9        // 9 AM morning
            }
            val seconds = (90..240).random().toLong()

            playbackHistoryDao.insertSession(
                PlaybackHistoryEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    coverArtUrl = track.coverArtUrl,
                    durationSeconds = track.durationSeconds.coerceAtLeast(180),
                    listenedSeconds = seconds,
                    timestamp = now - (dayOffset * 86400000L) - (hour * 3600000L),
                    dayOfWeek = dayOfWeek,
                    hourOfDay = hour
                )
            )
        }
    }

    suspend fun clearAllHistory() {
        playbackHistoryDao.clearHistory()
    }

    private fun calculateStats(history: List<PlaybackHistoryEntity>): ListeningStatsSummary {
        if (history.isEmpty()) {
            return ListeningStatsSummary()
        }

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis

        val totalListeningSeconds = history.sumOf { it.listenedSeconds }
        val todayListeningSeconds = history.filter { it.timestamp >= todayStart }.sumOf { it.listenedSeconds }
        val thisWeekListeningSeconds = history.filter { it.timestamp >= weekStart }.sumOf { it.listenedSeconds }

        // Day of week calculation (Mon -> Sun order)
        val daysMeta = listOf(
            Triple(Calendar.MONDAY, "Monday" to "E Hënë", "Mon" to "Hën"),
            Triple(Calendar.TUESDAY, "Tuesday" to "E Martë", "Tue" to "Mar"),
            Triple(Calendar.WEDNESDAY, "Wednesday" to "E Mërkurë", "Wed" to "Mër"),
            Triple(Calendar.THURSDAY, "Thursday" to "E Enjte", "Thu" to "Enj"),
            Triple(Calendar.FRIDAY, "Friday" to "E Premte", "Fri" to "Pre"),
            Triple(Calendar.SATURDAY, "Saturday" to "E Shtunë", "Sat" to "Sht"),
            Triple(Calendar.SUNDAY, "Sunday" to "E Diel", "Sun" to "Die")
        )

        val dayGroups = history.groupBy { it.dayOfWeek }
        val maxDaySec = daysMeta.maxOfOrNull { (dayIndex, _, _) -> dayGroups[dayIndex]?.sumOf { it.listenedSeconds } ?: 0L } ?: 1L
        val maxDaySafe = if (maxDaySec > 0) maxDaySec.toFloat() else 1f

        val dayStats = daysMeta.map { (dayIndex, names, shortNames) ->
            val secs = dayGroups[dayIndex]?.sumOf { it.listenedSeconds } ?: 0L
            val pct = (secs / maxDaySafe).coerceIn(0f, 1f)
            DayStat(
                dayIndex = dayIndex,
                nameEn = names.first,
                nameSq = names.second,
                shortNameEn = shortNames.first,
                shortNameSq = shortNames.second,
                totalSeconds = secs,
                percentage = pct
            )
        }

        val peakDay = dayStats.maxByOrNull { it.totalSeconds }
        val peakDayEn = peakDay?.nameEn ?: "N/A"
        val peakDaySq = peakDay?.nameSq ?: "N/A"
        val peakDaySeconds = peakDay?.totalSeconds ?: 0L

        // Hourly distribution (0..23)
        val hourGroups = history.groupBy { it.hourOfDay }
        val maxHourSec = (0..23).maxOfOrNull { hourGroups[it]?.sumOf { s -> s.listenedSeconds } ?: 0L } ?: 1L
        val maxHourSafe = if (maxHourSec > 0) maxHourSec.toFloat() else 1f

        val hourlyStats = (0..23).map { hour ->
            val secs = hourGroups[hour]?.sumOf { it.listenedSeconds } ?: 0L
            val label = "%02d:00".format(hour)
            HourStat(
                hour = hour,
                label = label,
                totalSeconds = secs,
                percentage = (secs / maxHourSafe).coerceIn(0f, 1f)
            )
        }

        val peakHour = hourlyStats.maxByOrNull { it.totalSeconds }
        val peakHourVal = peakHour?.hour ?: 20
        val nextHourVal = (peakHourVal + 1) % 24
        val peakHourLabel = "%02d:00".format(peakHourVal)
        val peakHourRange = "%02d:00 - %02d:00".format(peakHourVal, nextHourVal)

        // Time slots
        // Morning: 06-12, Afternoon: 12-18, Evening: 18-23, Night: 23-06
        val morningSec = history.filter { it.hourOfDay in 6..11 }.sumOf { it.listenedSeconds }
        val afternoonSec = history.filter { it.hourOfDay in 12..17 }.sumOf { it.listenedSeconds }
        val eveningSec = history.filter { it.hourOfDay in 18..22 }.sumOf { it.listenedSeconds }
        val nightSec = history.filter { it.hourOfDay >= 23 || it.hourOfDay < 6 }.sumOf { it.listenedSeconds }
        val totalSlotSec = (morningSec + afternoonSec + eveningSec + nightSec).coerceAtLeast(1L).toFloat()

        val timeSlots = listOf(
            TimeSlotStat("morning", "Morning", "Mëngjes", "06:00 - 12:00", "wb_sunny", morningSec, morningSec / totalSlotSec),
            TimeSlotStat("afternoon", "Afternoon", "Pasdite", "12:00 - 18:00", "light_mode", afternoonSec, afternoonSec / totalSlotSec),
            TimeSlotStat("evening", "Evening (Peak)", "Mbrëmje (Kulmi)", "18:00 - 23:00", "nights_stay", eveningSec, eveningSec / totalSlotSec),
            TimeSlotStat("night", "Late Night", "Natë vonë", "23:00 - 06:00", "bedtime", nightSec, nightSec / totalSlotSec)
        )

        // Top Artists
        val artistGroups = history.groupBy { it.artist }
        val topArtists = artistGroups.map { (artist, sessions) ->
            TopArtistStat(
                artist = artist.ifBlank { "Unknown Artist" },
                totalSeconds = sessions.sumOf { it.listenedSeconds },
                playCount = sessions.size
            )
        }.sortedByDescending { it.totalSeconds }.take(10)

        // Top Tracks
        val trackGroups = history.groupBy { it.trackId }
        val topTracks = trackGroups.map { (trackId, sessions) ->
            val first = sessions.first()
            TopTrackStat(
                trackId = trackId,
                title = first.title,
                artist = first.artist,
                album = first.album,
                totalSeconds = sessions.sumOf { it.listenedSeconds },
                playCount = sessions.size
            )
        }.sortedByDescending { it.totalSeconds }.take(10)

        return ListeningStatsSummary(
            totalListeningSeconds = totalListeningSeconds,
            todayListeningSeconds = todayListeningSeconds,
            thisWeekListeningSeconds = thisWeekListeningSeconds,
            totalTracksPlayed = history.size,
            dayOfWeekStats = dayStats,
            peakDayEn = peakDayEn,
            peakDaySq = peakDaySq,
            peakDaySeconds = peakDaySeconds,
            hourlyStats = hourlyStats,
            peakHourLabel = peakHourLabel,
            peakHourRange = peakHourRange,
            timeSlots = timeSlots,
            topArtists = topArtists,
            topTracks = topTracks,
            rawHistory = history
        )
    }
}
