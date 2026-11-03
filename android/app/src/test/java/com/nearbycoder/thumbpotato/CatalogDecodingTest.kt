package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.remote.CatalogClient
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CatalogDecodingTest {
    private val specRecord = """
        [{"id":"Q64577191","name":"The Legend of Zelda: Tears of the Kingdom","date":"2023-05-12",
          "genres":["Action","Adventure"],"dev":"Nintendo EPD","pub":"Nintendo",
          "cover":"https://upload.wikimedia.org/x.jpg","art":"https://shared.akamai.steamstatic.com/a.jpg",
          "hero":"https://shared.akamai.steamstatic.com/h.jpg","deck":3,
          "summary":"Two-sentence intro.","wiki":"The_Legend_of_Zelda:_Tears_of_the_Kingdom",
          "steam":1145360,"metacritic":96,"pop":52,"platforms":["switch","switch2"]},
         {"id":"Q1","name":"Minimal","wiki":"Minimal","pop":1,"platforms":["pc","unknown-platform"]},
         {"id":"Q2","name":"Someday","date":"2027-01-01","datePrecision":"year","wiki":"Someday","pop":3,"platforms":["ps5"]},
         {"id":"Q3","name":"Spring","date":"2027-03-01","datePrecision":"month","wiki":"Spring","pop":2,"platforms":["ps5"],"extra":"ignored"}]
    """.trimIndent()

    @Test
    fun `decodes the SPEC record into a game`() {
        val game = CatalogClient.decode(specRecord).first().game
        assertEquals(GameSource.WD, game.ref.source)
        assertEquals("wd-Q64577191", game.ref.key)
        assertEquals("The Legend of Zelda: Tears of the Kingdom", game.name)
        assertEquals(LocalDate.of(2023, 5, 12), game.releaseDate)
        assertEquals(DatePrecision.DAY, game.releaseDatePrecision)
        assertEquals(2023, game.year)
        assertEquals(listOf("Action", "Adventure"), game.genres)
        assertEquals("Nintendo EPD", game.developer)
        assertEquals("Nintendo", game.publisher)
        assertEquals("https://upload.wikimedia.org/x.jpg", game.boxArtUrl)
        assertEquals("https://shared.akamai.steamstatic.com/h.jpg", game.heroUrl)
        assertEquals(3, game.steamDeck)
        assertEquals(1145360, game.steamAppId)
        assertEquals(96, game.metacritic)
        assertEquals(52.0, game.popularity, 0.0)
        // Platforms come back in display order regardless of file order.
        assertEquals(listOf(Platform.SWITCH2, Platform.SWITCH), game.platforms)
    }

    @Test
    fun `missing optional fields decode to defaults and unknown platforms are dropped`() {
        val game = CatalogClient.decode(specRecord)[1].game
        assertNull(game.releaseDate)
        assertNull(game.year)
        assertTrue(game.genres.isEmpty())
        assertNull(game.boxArtUrl)
        assertEquals("", game.summary)
        assertEquals(listOf(Platform.PC), game.platforms)
    }

    @Test
    fun `coarse release dates keep their precision`() {
        val records = CatalogClient.decode(specRecord)
        val year = records[2].game
        val month = records[3].game
        assertEquals(DatePrecision.YEAR, year.releaseDatePrecision)
        assertEquals("2027", year.releaseLabel)
        assertEquals(DatePrecision.MONTH, month.releaseDatePrecision)
        assertTrue(month.releaseLabel!!.contains("2027"))
        assertTrue(!month.releaseLabel!!.contains("1"))
    }

    @Test
    fun `box art falls back to the steam capsule`() {
        val json = """[{"id":"Q9","name":"A","art":"https://s/cap.jpg","wiki":"A","pop":1,"platforms":["pc"]}]"""
        assertEquals("https://s/cap.jpg", CatalogClient.decode(json).single().game.boxArtUrl)
    }

    @Test
    fun `decodes the dev copy of every published catalog file`() {
        val dir = listOf(File("../../docs/catalog/v1"), File("../docs/catalog/v1")).firstOrNull { it.isDirectory } ?: return
        val genres = Genre.entries.map { it.label }.toSet()
        for (platform in Platform.entries) {
            val file = File(dir, "${platform.id}.json")
            if (!file.exists()) continue
            val games = CatalogClient.decode(file.readText()).map { it.game }
            assertTrue("${platform.id} is empty", games.isNotEmpty())
            assertTrue("${platform.id} lists a game not on it", games.all { platform in it.platforms })
            assertTrue("${platform.id} has a genre outside the fixed list", games.all { g -> g.genres.all { it in genres } })
            assertEquals("${platform.id} has duplicate ids", games.size, games.distinctBy { it.id }.size)
        }
    }
}
