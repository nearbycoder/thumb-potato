package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.ReleaseDates
import com.nearbycoder.thumbpotato.reminders.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReleaseAlertTest {
    private val today = LocalDate.of(2026, 11, 3)

    private fun saved(key: String, date: String?, precision: String? = null, following: Boolean = true) =
        SavedGame(key = key, source = "wd", externalId = key, name = key, releaseDate = date, releaseDatePrecision = precision, isFollowing = following)

    @Test
    fun `labels never show Jan 1 for coarse dates`() {
        assertEquals("2027", ReleaseDates.label(LocalDate.of(2027, 1, 1), DatePrecision.YEAR))
        assertEquals("TBA 2027", ReleaseDates.radarLabel(LocalDate.of(2027, 1, 1), DatePrecision.YEAR, today))
        val month = ReleaseDates.label(LocalDate.of(2027, 3, 1), DatePrecision.MONTH)!!
        assertTrue(month.endsWith("2027") && !month.contains(" 1,"))
        assertEquals("in 12 days", ReleaseDates.radarLabel(today.plusDays(12), DatePrecision.DAY, today))
        assertEquals("Tomorrow", ReleaseDates.radarLabel(today.plusDays(1), DatePrecision.DAY, today))
        assertEquals("Today", ReleaseDates.radarLabel(today, DatePrecision.DAY, today))
    }

    @Test
    fun `saving an upcoming game follows it`() {
        val upcoming = Game(GameRef(GameSource.WD, "Q1"), "Soon", releaseDate = LocalDate.now().plusDays(30))
        val out = Game(GameRef(GameSource.WD, "Q2"), "Out", releaseDate = LocalDate.now().minusDays(30))
        assertTrue(SavedGame.from(upcoming).isFollowing)
        assertFalse(SavedGame.from(out).isFollowing)
        val coarse = Game(GameRef(GameSource.WD, "Q3"), "Later", releaseDate = LocalDate.of(LocalDate.now().year + 1, 1, 1), releaseDatePrecision = DatePrecision.YEAR)
        val saved = SavedGame.from(coarse)
        assertTrue(saved.isFollowing)
        assertEquals("year", saved.releaseDatePrecision)
    }

    @Test
    fun `release alerts only for followed games with exact future dates`() {
        assertTrue(saved("a", "2026-11-20").wantsReleaseAlert(today))
        assertTrue(saved("today", "2026-11-03").wantsReleaseAlert(today))
        assertFalse(saved("past", "2026-10-01").wantsReleaseAlert(today))
        assertFalse(saved("year", "2027-01-01", "year").wantsReleaseAlert(today))
        assertFalse(saved("month", "2026-12-01", "month").wantsReleaseAlert(today))
        assertFalse(saved("unfollowed", "2026-11-20", following = false).wantsReleaseAlert(today))
        assertFalse(saved("undated", null).wantsReleaseAlert(today))
    }

    @Test
    fun `alerts fire at 9 AM local and skip ones already past`() {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now().plusDays(5)
        assertEquals(day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), Reminders.releaseAlertMillis(day, zone))
        val games = listOf(
            saved("later", day.plusDays(3).toString()),
            saved("soon", day.toString()),
            saved("coarse", day.withDayOfMonth(1).toString(), "month"),
            saved("todayAfterNine", LocalDate.now().toString()),
        )
        val now = LocalDate.now().atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(listOf("soon", "later"), Reminders.releaseAlertCandidates(games, now).map { it.key })
    }
}
