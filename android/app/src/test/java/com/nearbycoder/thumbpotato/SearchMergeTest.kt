package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.SearchMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchMergeTest {
    private fun wd(id: String, name: String, steam: Int? = null, pop: Double = 0.0, year: Int? = null) =
        Game(GameRef(GameSource.WD, id), name, steamAppId = steam, popularity = pop, year = year)

    private fun steam(id: Int, name: String, year: Int? = null) =
        Game(GameRef(GameSource.STEAM, id.toString()), name, steamAppId = id, year = year)

    @Test
    fun `local first, then wikipedia and steam interleaved, without duplicates`() {
        val local = listOf(wd("Q1", "Hades", steam = 1145360, pop = 90.0))
        val wiki = listOf(
            wd("Q1", "Hades"), // same QID as the catalog record
            wd("Q2", "Hades II"),
            wd("Q3", "Hades (board game)"),
        )
        val steamHits = listOf(
            steam(1145360, "Hades"), // same app as the catalog record
            steam(1145350, "Hades II"), // same name as a Wikipedia result
            steam(999, "Hadesville"),
        )
        val merged = SearchMerge.merge(local, wiki, steamHits)
        assertEquals(listOf("wd-Q1", "wd-Q2", "wd-Q3", "steam-999"), merged.map { it.ref.key })
    }

    @Test
    fun `different years keep same-named games apart`() {
        val merged = SearchMerge.merge(listOf(wd("Q10", "Doom", year = 1993)), emptyList(), listOf(steam(379720, "DOOM", year = 2016)))
        assertEquals(listOf("wd-Q10", "steam-379720"), merged.map { it.ref.key })
        val same = SearchMerge.merge(listOf(wd("Q11", "Celeste", year = 2018)), emptyList(), listOf(steam(504230, "Celeste", year = 2018)))
        assertEquals(listOf("wd-Q11"), same.map { it.ref.key })
    }

    @Test
    fun `ranks local matches exact, prefix, word, anywhere`() {
        val games = listOf(
            wd("a", "Super Mario Odyssey", pop = 50.0),
            wd("b", "Mario Kart 8 Deluxe", pop = 80.0),
            wd("c", "Mario", pop = 1.0),
            wd("d", "Paper Mario", pop = 99.0),
            wd("e", "Zelda", pop = 100.0),
            wd("f", "Supermariolike", pop = 100.0),
        )
        assertEquals(listOf("c", "b", "d", "a", "f"), SearchMerge.rankLocal(games, "mario").map { it.ref.id })
        assertEquals(emptyList<Game>(), SearchMerge.rankLocal(games, "m"))
    }
}
