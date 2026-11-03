package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.domain.CriticScore
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameCatalog
import com.nearbycoder.thumbpotato.domain.GameFilter
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSort
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Released
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GameFilterTest {
    private val today = LocalDate.of(2026, 11, 3)

    private fun game(
        id: String,
        date: LocalDate? = null,
        precision: DatePrecision = DatePrecision.DAY,
        genres: List<String> = emptyList(),
        metacritic: Int? = null,
        name: String = id,
        pop: Double = 0.0,
    ) = Game(GameRef(GameSource.WD, id), name, releaseDate = date, releaseDatePrecision = precision, genres = genres, metacritic = metacritic, popularity = pop)

    private val old = game("old", LocalDate.of(1998, 11, 21), genres = listOf("Adventure"), metacritic = 99, name = "Ocarina")
    private val mid = game("mid", LocalDate.of(2015, 5, 19), genres = listOf("RPG"), metacritic = 93, name = "Witcher")
    private val recent = game("recent", LocalDate.of(2026, 9, 1), genres = listOf("Action", "RPG"), metacritic = 78, name = "Astro")
    private val future = game("future", LocalDate.of(2027, 2, 1), genres = listOf("Action"), name = "Zeta")
    private val tbaYear = game("tba", LocalDate.of(2027, 1, 1), DatePrecision.YEAR, name = "Beta")
    private val undated = game("undated", name = "Alpha")
    private val all = listOf(old, mid, recent, future, tbaYear, undated)

    @Test
    fun `default filter keeps everything in order`() {
        assertTrue(GameFilter().isDefault)
        assertEquals(all, GameFilter().apply(all, today))
    }

    @Test
    fun `released windows`() {
        fun ids(r: Released) = GameFilter(released = r).apply(all, today).map { it.ref.id }
        assertEquals(listOf("future", "tba"), ids(Released.UPCOMING))
        assertEquals(listOf("recent"), ids(Released.THIS_YEAR))
        assertEquals(listOf("recent", "future", "tba"), ids(Released.Y2020S))
        assertEquals(listOf("mid"), ids(Released.Y2010S))
        assertEquals(listOf("old"), ids(Released.BEFORE2000))
        assertEquals(listOf("recent"), ids(Released.LAST5))
    }

    @Test
    fun `a year-only date in the current year still counts as upcoming`() {
        val thisYear = game("ty", LocalDate.of(2026, 1, 1), DatePrecision.YEAR)
        assertTrue(thisYear.isUpcoming(today))
        val exactPast = game("ep", LocalDate.of(2026, 1, 1))
        assertFalse(exactPast.isUpcoming(today))
        val monthPast = game("mp", LocalDate.of(2026, 10, 1), DatePrecision.MONTH)
        assertFalse(monthPast.isUpcoming(today))
        val monthNow = game("mn", LocalDate.of(2026, 11, 1), DatePrecision.MONTH)
        assertTrue(monthNow.isUpcoming(today))
    }

    @Test
    fun `genre and critic score`() {
        assertEquals(listOf("mid", "recent"), GameFilter(genre = Genre.RPG).apply(all, today).map { it.ref.id })
        assertEquals(listOf("old", "mid"), GameFilter(score = CriticScore.S90).apply(all, today).map { it.ref.id })
        assertEquals(listOf("old", "mid", "recent"), GameFilter(score = CriticScore.S70).apply(all, today).map { it.ref.id })
    }

    @Test
    fun `newest drops unreleased games unless asking for upcoming`() {
        assertEquals(listOf("recent", "mid", "old", "undated").toSet(), GameFilter(sort = GameSort.NEWEST).apply(all, today).map { it.ref.id }.toSet())
        assertEquals("recent", GameFilter(sort = GameSort.NEWEST).apply(all, today).first { it.releaseDate != null }.ref.id)
        assertEquals(listOf("future", "tba"), GameFilter(sort = GameSort.NEWEST, released = Released.UPCOMING).apply(all, today).map { it.ref.id })
    }

    @Test
    fun `top rated and a-z`() {
        assertEquals(listOf("old", "mid", "recent"), GameFilter(sort = GameSort.TOP_RATED).apply(all, today).take(3).map { it.ref.id })
        assertEquals(listOf("Alpha", "Astro", "Beta", "Ocarina", "Witcher", "Zeta"), GameFilter(sort = GameSort.TITLE).apply(all, today).map { it.name })
    }

    @Test
    fun `rawg params`() {
        val p = GameFilter(sort = GameSort.TOP_RATED, released = Released.Y2010S, genre = Genre.RPG, score = CriticScore.S80).rawgParams(today)
        assertEquals("-metacritic", p["ordering"])
        assertEquals("2010-01-01,2019-12-31", p["dates"])
        assertEquals("role-playing-games-rpg", p["genres"])
        assertEquals("80,100", p["metacritic"])
        val upcoming = GameFilter(released = Released.UPCOMING).rawgParams(today)
        assertEquals("2026-11-04,2031-11-03", upcoming["dates"])
        assertEquals("1970-01-01,2026-11-03", GameFilter(sort = GameSort.NEWEST).rawgParams(today)["dates"])
    }

    @Test
    fun `home rows`() {
        val new = GameCatalog.newReleasesOf(all + game("week", LocalDate.of(2026, 10, 30)), today)
        assertEquals(listOf("week", "recent"), new.map { it.ref.id })
        // Coarse dates sort at the end of their window: "2027" comes after Feb 1, 2027.
        assertEquals(listOf("future", "tba"), GameCatalog.comingSoonOf(all, today).map { it.ref.id })
    }
}
