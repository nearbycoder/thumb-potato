package com.nearbycoder.thumbpotato.domain

import com.nearbycoder.thumbpotato.data.local.PlaySession
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.data.remote.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Portable JSON for the backlog and sessions (`thumbpotato.backup`). This is the bridge
 * between the Android and Apple apps: ISO-8601 timestamps, stable game keys, plain fields.
 */
object Backup {
    const val FORMAT = "thumbpotato.backup"
    const val VERSION = 1

    @Serializable
    data class File(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exportedAt: String,
        val games: List<GameEntry>,
        val sessions: List<SessionEntry>,
    )

    @Serializable
    data class GameEntry(
        val key: String,
        val source: String,
        val externalID: String,
        val name: String,
        val summary: String = "",
        val coverURL: String? = null,
        val heroURL: String? = null,
        val releaseDate: String? = null,
        val releaseDatePrecision: String? = null,
        val year: Int? = null,
        val genres: List<String> = emptyList(),
        val platformIDs: List<String> = emptyList(),
        val playingOn: String? = null,
        val status: String,
        val rating: Int = 0,
        val notes: String = "",
        val hoursPlayed: Double = 0.0,
        val progress: Int = 0,
        val owned: Boolean = false,
        val isFollowing: Boolean = false,
        val steamAppID: Int? = null,
        val wikiTitle: String? = null,
        val metacritic: Int? = null,
        val addedAt: String,
        val updatedAt: String,
        val finishedAt: String? = null,
    )

    @Serializable
    data class SessionEntry(
        val id: String,
        val gameKey: String,
        val gameName: String,
        val coverURL: String? = null,
        val platformID: String? = null,
        val startsAt: String,
        val durationMinutes: Int,
        val note: String = "",
        val hasReminder: Boolean = true,
        val isDone: Boolean = false,
        val createdAt: String,
    )

    // Nulls are written out (as in SPEC.md's example) so both apps see every field.
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

    private fun iso(millis: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

    fun export(games: List<SavedGame>, sessions: List<PlaySession>, now: Long = System.currentTimeMillis()): String {
        val file = File(
            exportedAt = iso(now),
            games = games.map { g ->
                GameEntry(
                    key = g.key, source = g.source, externalID = g.externalId, name = g.name, summary = g.summary,
                    coverURL = g.coverUrl, heroURL = g.heroUrl, releaseDate = g.releaseDate?.take(10),
                    releaseDatePrecision = g.releaseDatePrecision, year = g.year, genres = g.genres,
                    platformIDs = g.platformIds, playingOn = g.playingOn, status = g.status, rating = g.rating,
                    notes = g.notes, hoursPlayed = g.hoursPlayed, progress = g.progress, owned = g.owned,
                    isFollowing = g.isFollowing, steamAppID = g.steamAppId, wikiTitle = g.wikiTitle,
                    metacritic = g.metacritic, addedAt = iso(g.addedAt), updatedAt = iso(g.updatedAt),
                    finishedAt = g.finishedAt?.let(::iso),
                )
            },
            sessions = sessions.map { s ->
                SessionEntry(
                    id = s.id, gameKey = s.gameKey, gameName = s.gameName, coverURL = s.coverUrl, platformID = s.platformId,
                    startsAt = iso(s.startsAt), durationMinutes = s.durationMinutes, note = s.note,
                    hasReminder = s.hasReminder, isDone = s.isDone, createdAt = iso(s.createdAt),
                )
            },
        )
        return json.encodeToString(File.serializer(), file)
    }

    data class Imported(val games: List<SavedGame>, val sessions: List<PlaySession>)

    /** Lenient import: tolerates missing fields, nulls, and epoch or ISO timestamps. */
    fun import(text: String): Imported {
        val root = Http.json.parseToJsonElement(text).jsonObject
        val format = root.str("format")
        require(format == null || format == FORMAT) { "This isn't a Thumb Potato backup." }
        val games = root["games"]?.jsonArray?.mapNotNull { el -> (el as? JsonObject)?.let(::parseGame) } ?: emptyList()
        val sessions = root["sessions"]?.jsonArray?.mapNotNull { el -> (el as? JsonObject)?.let(::parseSession) } ?: emptyList()
        return Imported(games, sessions)
    }

    private fun JsonObject.str(vararg names: String): String? =
        names.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.takeIf { p -> p.isString || p.contentOrNull != null }?.contentOrNull }

    private fun JsonObject.int(vararg names: String): Int? =
        names.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.let { p -> p.intOrNull ?: p.doubleOrNull?.toInt() } }

    private fun JsonObject.double(vararg names: String): Double? =
        names.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.doubleOrNull }

    private fun JsonObject.bool(vararg names: String): Boolean? =
        names.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.booleanOrNull }

    private fun JsonObject.millis(vararg names: String): Long? =
        names.firstNotNullOfOrNull { name ->
            val p = this[name] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
            if (p.isString) {
                p.contentOrNull?.let { s -> runCatching { Instant.parse(s).toEpochMilli() }.getOrNull() }
            } else {
                p.longOrNull?.let { if (it < 100_000_000_000L) it * 1000 else it }
                    ?: p.doubleOrNull?.let { if (it < 100_000_000_000.0) (it * 1000).toLong() else it.toLong() }
            }
        }

    private fun JsonObject.strings(name: String): List<String> =
        this[name]?.let { el -> runCatching { el.jsonArray.map { it.jsonPrimitive.content } }.getOrNull() } ?: emptyList()

    private fun parseGame(o: JsonObject): SavedGame? {
        val key = o.str("key") ?: return null
        val ref = GameRef.parse(key) ?: return null
        val now = System.currentTimeMillis()
        val precision = o.str("releaseDatePrecision")?.let(DatePrecision::fromRaw)
        return SavedGame(
            key = key,
            source = o.str("source") ?: ref.source.raw,
            externalId = o.str("externalID", "externalId") ?: ref.id,
            name = o.str("name") ?: "Untitled",
            summary = o.str("summary") ?: "",
            coverUrl = o.str("coverURL", "coverUrl"),
            heroUrl = o.str("heroURL", "heroUrl"),
            releaseDate = o.str("releaseDate")?.take(10)?.takeIf { com.nearbycoder.thumbpotato.data.remote.DateParsing.date(it) != null },
            releaseDatePrecision = precision?.takeIf { it != DatePrecision.DAY }?.raw,
            year = o.int("year"),
            genresCsv = o.strings("genres").joinToString(","),
            platformIdsCsv = o.strings("platformIDs").filter { Platform.withId(it) != null }.joinToString(","),
            playingOn = o.str("playingOn")?.takeIf { Platform.withId(it) != null },
            status = BacklogStatus.fromRaw(o.str("status")).raw,
            rating = (o.int("rating") ?: 0).coerceIn(0, 5),
            notes = o.str("notes") ?: "",
            hoursPlayed = (o.double("hoursPlayed") ?: 0.0).coerceAtLeast(0.0),
            progress = (o.int("progress") ?: 0).coerceIn(0, 100),
            owned = o.bool("owned") ?: false,
            isFollowing = o.bool("isFollowing") ?: false,
            steamAppId = o.int("steamAppID", "steamAppId"),
            wikiTitle = o.str("wikiTitle"),
            metacritic = o.int("metacritic"),
            addedAt = o.millis("addedAt") ?: now,
            updatedAt = o.millis("updatedAt") ?: now,
            finishedAt = o.millis("finishedAt"),
        )
    }

    private fun parseSession(o: JsonObject): PlaySession? {
        val gameKey = o.str("gameKey") ?: return null
        val startsAt = o.millis("startsAt") ?: return null
        return PlaySession(
            id = o.str("id")?.lowercase() ?: UUID.randomUUID().toString(),
            gameKey = gameKey,
            gameName = o.str("gameName") ?: "Play session",
            coverUrl = o.str("coverURL", "coverUrl"),
            platformId = o.str("platformID", "platformId")?.takeIf { Platform.withId(it) != null },
            startsAt = startsAt,
            durationMinutes = o.int("durationMinutes") ?: 60,
            note = o.str("note") ?: "",
            hasReminder = o.bool("hasReminder") ?: true,
            isDone = o.bool("isDone") ?: false,
            createdAt = o.millis("createdAt") ?: System.currentTimeMillis(),
        )
    }
}
