package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.SessionLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingTest {
    private fun game(vararg genres: String) = Game(GameRef(GameSource.WD, "Q1"), "G", genres = genres.toList())

    @Test
    fun `maps steam and rawg genre names onto the fixed list`() {
        assertEquals(Genre.RPG, Genre.map("RPG"))
        assertEquals(Genre.RPG, Genre.map("Role-playing (RPG)"))
        assertEquals(Genre.RPG, Genre.map("Role Playing Games"))
        assertEquals(Genre.SHOOTER, Genre.map("Shooter"))
        assertEquals(Genre.PLATFORMER, Genre.map("Platformer"))
        assertEquals(Genre.SIMULATION, Genre.map("Simulation"))
        assertEquals(Genre.SPORTS, Genre.map("Sports"))
        assertEquals(Genre.RACING, Genre.map("Racing"))
        assertEquals(Genre.STRATEGY, Genre.map("Card"))
        assertEquals(Genre.PARTY, Genre.map("Family"))
        assertEquals(Genre.ACTION, Genre.map("Action"))
        assertEquals(Genre.ADVENTURE, Genre.map("Adventure"))
        assertEquals(Genre.VISUAL_NOVEL, Genre.map("Visual Novel"))
    }

    @Test
    fun `drops names with no counterpart`() {
        assertNull(Genre.map("Massively Multiplayer"))
        assertNull(Genre.map("Indie"))
        assertNull(Genre.map("Free to Play"))
        assertNull(Genre.map("Early Access"))
        assertNull(Genre.map(""))
    }

    @Test
    fun `casual becomes puzzle only when nothing else matched`() {
        assertEquals(listOf("Puzzle"), Genre.mapAll(listOf("Casual", "Indie")))
        assertEquals(listOf("Racing"), Genre.mapAll(listOf("Casual", "Racing")))
        assertEquals(listOf("Action", "RPG"), Genre.mapAll(listOf("Action", "Indie", "RPG", "Action")))
        assertTrue(Genre.mapAll(listOf("Massively Multiplayer")).isEmpty())
    }

    @Test
    fun `moods map to their genres`() {
        assertEquals(listOf(Genre.SIMULATION, Genre.SANDBOX, Genre.PUZZLE, Genre.PARTY, Genre.MUSIC), Mood.COZY.genres)
        assertEquals(listOf(Genre.HORROR, Genre.SURVIVAL), Mood.SPOOKY.genres)
        assertTrue(Mood.EPIC.matches(game("RPG")))
        assertTrue(Mood.COUCH_COOP.matches(game("Racing", "Action")))
        assertFalse(Mood.BRAINY.matches(game("Shooter")))
        assertFalse(Mood.COZY.matches(game()))
        assertEquals(Mood.COUCH_COOP, Mood.withId("couch-coop"))
        assertEquals("Games for the whole couch", Mood.COUCH_COOP.blurb)
    }

    @Test
    fun `session lengths map to their genres`() {
        assertTrue(SessionLength.QUICK.fits(game("Puzzle")))
        assertTrue(SessionLength.HOUR.fits(game("Metroidvania")))
        assertTrue(SessionLength.LONG.fits(game("RPG")))
        assertFalse(SessionLength.QUICK.fits(game("RPG")))
        assertFalse(SessionLength.LONG.fits(game("Fighting")))
        // Unknown genres fit any length rather than vanishing from Shuffle.
        assertTrue(SessionLength.QUICK.fits(game()))
        // Adventure suits both an hour and a long session.
        assertTrue(SessionLength.HOUR.fits(game("Adventure")) && SessionLength.LONG.fits(game("Adventure")))
    }
}
