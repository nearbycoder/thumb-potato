package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Backup
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Library
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.data.remote.Http
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupTest {
    /** The example from SPEC.md, verbatim ("..." placeholders included). */
    private val specExample = """
        {"format":"thumbpotato.backup","version":1,"exportedAt":"2026-11-03T20:00:00Z",
         "games":[{"key":"wd-Q64577191","source":"wd","externalID":"Q64577191","name":"...","summary":"...",
           "coverURL":"...","heroURL":"...","releaseDate":"2023-05-12","year":2023,"genres":["Action"],
           "platformIDs":["switch"],"playingOn":"switch","status":"playing","rating":4,"notes":"",
           "hoursPlayed":12.5,"progress":40,"owned":true,"isFollowing":false,"steamAppID":null,
           "wikiTitle":"...","metacritic":96,"addedAt":"...","updatedAt":"...","finishedAt":null}],
         "sessions":[{"id":"UUID","gameKey":"wd-Q64577191","gameName":"...","coverURL":"...","platformID":"switch",
           "startsAt":"...","durationMinutes":120,"note":"","hasReminder":true,"isDone":false,"createdAt":"..."}]}
    """.trimIndent()

    /** The same document with real timestamps and ids. */
    private val filledExample = specExample
        .replace("\"addedAt\":\"...\"", "\"addedAt\":\"2026-10-01T18:30:00Z\"")
        .replace("\"updatedAt\":\"...\"", "\"updatedAt\":\"2026-11-02T21:15:00Z\"")
        .replace("\"startsAt\":\"...\"", "\"startsAt\":\"2026-11-07T19:00:00Z\"")
        .replace("\"createdAt\":\"...\"", "\"createdAt\":\"2026-11-03T12:00:00Z\"")
        .replace("\"id\":\"UUID\"", "\"id\":\"6F9619FF-8B86-D011-B42D-00C04FC964FF\"")

    @Test
    fun `reads the SPEC example`() {
        val imported = Backup.import(specExample)
        val game = imported.games.single()
        assertEquals("wd-Q64577191", game.key)
        assertEquals("wd", game.source)
        assertEquals("Q64577191", game.externalId)
        assertEquals("2023-05-12", game.releaseDate)
        assertEquals(2023, game.year)
        assertEquals(listOf("Action"), game.genres)
        assertEquals(listOf(Platform.SWITCH), game.platforms)
        assertEquals(Platform.SWITCH, game.playingOnPlatform)
        assertEquals(BacklogStatus.PLAYING, game.backlogStatus)
        assertEquals(4, game.rating)
        assertEquals(12.5, game.hoursPlayed, 0.0)
        assertEquals(40, game.progress)
        assertTrue(game.owned)
        assertNull(game.steamAppId)
        assertEquals(96, game.metacritic)
        assertNull(game.finishedAt)
        assertEquals(DatePrecision.DAY, game.precision)
        // A session whose start time is a placeholder can't be planned, so it's skipped.
        assertTrue(imported.sessions.isEmpty())
    }

    @Test
    fun `round-trips the SPEC example`() {
        val first = Backup.import(filledExample)
        val session = first.sessions.single()
        assertEquals("6f9619ff-8b86-d011-b42d-00c04fc964ff", session.id)
        assertEquals(Instant.parse("2026-11-07T19:00:00Z").toEpochMilli(), session.startsAt)
        assertEquals(120, session.durationMinutes)
        assertEquals(Platform.SWITCH, session.platform)
        assertTrue(session.hasReminder)

        val text = Backup.export(first.games, first.sessions, now = Instant.parse("2026-11-03T20:00:00Z").toEpochMilli())
        val second = Backup.import(text)
        assertEquals(first.games, second.games)
        assertEquals(first.sessions, second.sessions)

        // The exported document uses the shared field names, with nulls spelled out.
        val root = Http.json.parseToJsonElement(text).jsonObject
        assertEquals("thumbpotato.backup", root["format"]!!.jsonPrimitive.content)
        assertEquals("2026-11-03T20:00:00Z", root["exportedAt"]!!.jsonPrimitive.content)
        val g = root["games"]!!.jsonArray.single().jsonObject
        for (field in listOf("key", "source", "externalID", "name", "summary", "coverURL", "heroURL", "releaseDate", "year", "genres",
            "platformIDs", "playingOn", "status", "rating", "notes", "hoursPlayed", "progress", "owned", "isFollowing", "steamAppID",
            "wikiTitle", "metacritic", "addedAt", "updatedAt", "finishedAt")) {
            assertTrue("missing $field", g.containsKey(field))
        }
        assertEquals("2026-10-01T18:30:00Z", g["addedAt"]!!.jsonPrimitive.content)
        val s = root["sessions"]!!.jsonArray.single().jsonObject
        for (field in listOf("id", "gameKey", "gameName", "coverURL", "platformID", "startsAt", "durationMinutes", "note", "hasReminder", "isDone", "createdAt")) {
            assertTrue("missing $field", s.containsKey(field))
        }
    }

    @Test
    fun `carries release date precision`() {
        val coarse = filledExample.replace("\"releaseDate\":\"2023-05-12\"", "\"releaseDate\":\"2027-01-01\",\"releaseDatePrecision\":\"year\"")
        val game = Backup.import(coarse).games.single()
        assertEquals(DatePrecision.YEAR, game.precision)
        val again = Backup.import(Backup.export(listOf(game), emptyList())).games.single()
        assertEquals("year", again.releaseDatePrecision)
        assertEquals("2027-01-01", again.releaseDate)
    }

    @Test
    fun `rejects other formats and tolerates junk`() {
        val error = runCatching { Backup.import("""{"format":"couchpotato.backup","titles":[]}""") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        val lenient = Backup.import("""{"games":[{"key":"nope"},{"key":"steam-620","name":"Portal 2","status":"weird","rating":9,"progress":250,"platformIDs":["pc","dreamcast"],"addedAt":1700000000}]}""")
        val g = lenient.games.single()
        assertEquals(BacklogStatus.QUEUED, g.backlogStatus)
        assertEquals(5, g.rating)
        assertEquals(100, g.progress)
        assertEquals(listOf("pc"), g.platformIds)
        assertEquals(1_700_000_000_000L, g.addedAt)
    }

    @Test
    fun `import merges by key and newer updatedAt wins`() {
        fun saved(key: String, updated: Long, notes: String) = SavedGame(key = key, source = "wd", externalId = key.removePrefix("wd-"), name = key, notes = notes, updatedAt = updated)
        val existing = mapOf("wd-Q1" to saved("wd-Q1", 200, "local"), "wd-Q2" to saved("wd-Q2", 200, "local"))
        val incoming = listOf(saved("wd-Q1", 100, "older"), saved("wd-Q2", 300, "newer"), saved("wd-Q3", 50, "new"), saved("wd-Q3", 60, "newest dup"))
        val merged = Library.mergeNewer(incoming, existing).associateBy { it.key }
        assertEquals(setOf("wd-Q2", "wd-Q3"), merged.keys)
        assertEquals("newer", merged["wd-Q2"]!!.notes)
        assertEquals("newest dup", merged["wd-Q3"]!!.notes)
    }
}
