package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.remote.Http
import com.nearbycoder.thumbpotato.data.remote.WikidataFacts
import com.nearbycoder.thumbpotato.data.remote.WikipediaClient
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WikipediaTest {
    @Test
    fun `search results are ordered by index and keyed by QID`() {
        val json = """
            {"batchcomplete":true,"query":{"pages":[
              {"pageid":1,"title":"The Legend of Zelda: Oracle of Seasons and Oracle of Ages","index":3,
               "thumbnail":{"source":"https://upload.wikimedia.org/a.png","width":446,"height":224},
               "pageprops":{"wikibase_item":"Q889739"},"extract":"Two games."},
              {"pageid":2,"title":"Hades (video game)","index":1,"pageprops":{"wikibase_item":"Q59756366"},"extract":"A roguelike."},
              {"pageid":3,"title":"No Item","index":2}]}}
        """.trimIndent()
        val games = WikipediaClient.parseSearch(json)
        assertEquals(listOf("wd-Q59756366", "wd-Q889739"), games.map { it.ref.key })
        assertEquals("Hades", games[0].name)
        assertEquals("Hades_(video_game)", games[0].wikiTitle)
        assertEquals("https://upload.wikimedia.org/a.png", games[1].coverUrl)
    }

    @Test
    fun `display names drop the disambiguator`() {
        assertEquals("Doom", WikipediaClient.displayName("Doom (2016 video game)"))
        assertEquals("Prey", WikipediaClient.displayName("Prey (2017 video game)"))
        assertEquals("Hades", WikipediaClient.displayName("Hades (video game)"))
        assertEquals("Celeste", WikipediaClient.displayName("Celeste"))
    }

    @Test
    fun `wikidata facts`() {
        val entity = Http.json.parseToJsonElement(
            """
            {"labels":{"en":{"value":"Hollow Knight: Silksong"}},
             "sitelinks":{"enwiki":{"title":"Hollow Knight: Silksong"}},
             "claims":{
               "P400":[{"mainsnak":{"datavalue":{"value":{"id":"Q19610114"}}}},{"mainsnak":{"datavalue":{"value":{"id":"Q1406"}}}},{"mainsnak":{"datavalue":{"value":{"id":"Q999"}}}}],
               "P577":[{"mainsnak":{"datavalue":{"value":{"time":"+2025-00-00T00:00:00Z","precision":9}}}},
                       {"mainsnak":{"datavalue":{"value":{"time":"+2025-09-04T00:00:00Z","precision":11}}}}],
               "P1733":[{"mainsnak":{"datavalue":{"value":"1030300"}}}],
               "P136":[{"mainsnak":{"datavalue":{"value":{"id":"Q1061510"}}}}]}}
            """.trimIndent(),
        ).jsonObject
        val facts = WikidataFacts.parse("Q61051548", entity)
        assertEquals(listOf(Platform.SWITCH, Platform.PC), facts.platforms)
        // Year-only Jan 1 sorts before Sep 4, so it wins as "earliest"; its precision travels with it.
        assertEquals(LocalDate.of(2025, 1, 1), facts.releaseDate)
        assertEquals(DatePrecision.YEAR, facts.precision)
        assertEquals(1030300, facts.steamAppId)
        val game = facts.game(mapOf("Q1061510" to "Metroidvania"))
        assertEquals("Hollow Knight: Silksong", game.name)
        assertEquals(listOf("Metroidvania"), game.genres)
        assertEquals("wd-Q61051548", game.ref.key)
    }
}
