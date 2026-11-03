package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameRefTest {
    @Test
    fun `keys round-trip for every source`() {
        for (ref in listOf(GameRef(GameSource.WD, "Q17185964"), GameRef(GameSource.STEAM, "1145360"), GameRef(GameSource.RAWG, "3498"))) {
            assertEquals(ref, GameRef.parse(ref.key))
        }
        assertEquals("wd-Q17185964", GameRef(GameSource.WD, "Q17185964").key)
        assertEquals("steam-1145360", GameRef(GameSource.STEAM, "1145360").key)
        assertEquals("rawg-3498", GameRef(GameSource.RAWG, "3498").key)
    }

    @Test
    fun `ids may contain dashes`() {
        assertEquals(GameRef(GameSource.RAWG, "a-b-c"), GameRef.parse("rawg-a-b-c"))
    }

    @Test
    fun `rejects malformed keys`() {
        assertNull(GameRef.parse(null))
        assertNull(GameRef.parse(""))
        assertNull(GameRef.parse("wd"))
        assertNull(GameRef.parse("wd-"))
        assertNull(GameRef.parse("tmdb-movie-603"))
    }

    @Test
    fun `deep link and web url`() {
        val ref = GameRef(GameSource.STEAM, "1145360")
        assertEquals("thumbpotato://game/steam-1145360", ref.deepLink)
        assertEquals("https://store.steampowered.com/app/1145360", ref.webUrl)
        assertEquals("https://www.wikidata.org/wiki/Q1", GameRef(GameSource.WD, "Q1").webUrl)
    }
}
