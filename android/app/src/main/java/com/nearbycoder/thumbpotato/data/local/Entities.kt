package com.nearbycoder.thumbpotato.data.local

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.ReleaseDates
import java.time.LocalDate
import java.util.UUID

/** A game in your backlog. */
@Immutable
@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey val key: String,
    val source: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    val name: String,
    val summary: String = "",
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "hero_url") val heroUrl: String? = null,
    /** ISO day, e.g. `2023-05-12`. */
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    /** `year` or `month` for coarse dates; null for an exact day. */
    @ColumnInfo(name = "release_date_precision") val releaseDatePrecision: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "genres_csv") val genresCsv: String = "",
    @ColumnInfo(name = "platform_ids_csv") val platformIdsCsv: String = "",
    @ColumnInfo(name = "playing_on") val playingOn: String? = null,
    val status: String = BacklogStatus.QUEUED.raw,
    /** 0–5 stars. */
    val rating: Int = 0,
    val notes: String = "",
    @ColumnInfo(name = "hours_played") val hoursPlayed: Double = 0.0,
    /** 0–100. */
    val progress: Int = 0,
    val owned: Boolean = false,
    @ColumnInfo(name = "is_following") val isFollowing: Boolean = false,
    @ColumnInfo(name = "steam_app_id") val steamAppId: Int? = null,
    @ColumnInfo(name = "wiki_title") val wikiTitle: String? = null,
    val metacritic: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
) {
    val ref: GameRef get() = GameRef(GameSource.fromRaw(source) ?: GameSource.WD, externalId)
    val backlogStatus: BacklogStatus get() = BacklogStatus.fromRaw(status)
    val genres: List<String> get() = genresCsv.split(",").filter { it.isNotBlank() }
    val platformIds: List<String> get() = platformIdsCsv.split(",").filter { it.isNotBlank() }
    val platforms: List<Platform> get() = platformIds.mapNotNull { Platform.withId(it) }
    val playingOnPlatform: Platform? get() = Platform.withId(playingOn)
    val release: LocalDate? get() = releaseDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    val precision: DatePrecision get() = DatePrecision.fromRaw(releaseDatePrecision)

    /** `Playing · 40% · 12h` style progress caption, or null when there's nothing to show. */
    val progressLabel: String?
        get() {
            val parts = buildList {
                if (progress > 0) add("$progress%")
                if (hoursPlayed > 0) add(com.nearbycoder.thumbpotato.domain.hoursLabel(hoursPlayed))
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }

    /** A lightweight game value so saved games can reuse catalog views. */
    val asGame: Game
        get() = Game(
            ref = ref, name = name, summary = summary, coverUrl = coverUrl, heroUrl = heroUrl,
            releaseDate = release, releaseDatePrecision = precision, year = year ?: release?.year,
            genres = genres, platforms = platforms, metacritic = metacritic, steamAppId = steamAppId, wikiTitle = wikiTitle,
        )

    fun isUpcoming(today: LocalDate = LocalDate.now()): Boolean = asGame.isUpcoming(today)

    /** True when a release-day alert makes sense: followed, exact date, not out yet. */
    fun wantsReleaseAlert(today: LocalDate = LocalDate.now()): Boolean {
        val date = release ?: return false
        return isFollowing && precision == DatePrecision.DAY && date >= today
    }

    fun updated(from: Game): SavedGame = copy(
        name = from.name,
        summary = from.summary.ifEmpty { summary },
        coverUrl = from.boxArtUrl ?: coverUrl,
        heroUrl = from.heroUrl ?: heroUrl,
        releaseDate = from.releaseDate?.toString() ?: releaseDate,
        releaseDatePrecision = if (from.releaseDate != null) from.releaseDatePrecision.storedRaw else releaseDatePrecision,
        year = from.year ?: year,
        genresCsv = if (from.genres.isNotEmpty()) from.genres.joinToString(",") else genresCsv,
        platformIdsCsv = if (from.platforms.isNotEmpty()) from.platforms.joinToString(",") { it.id } else platformIdsCsv,
        steamAppId = from.steamAppId ?: steamAppId,
        wikiTitle = from.wikiTitle ?: wikiTitle,
        metacritic = from.metacritic ?: metacritic,
    )

    fun withStatus(newStatus: BacklogStatus, now: Long = System.currentTimeMillis()): SavedGame = copy(
        status = newStatus.raw,
        updatedAt = now,
        finishedAt = if (newStatus == BacklogStatus.BEATEN) (finishedAt ?: now) else finishedAt,
        progress = if (newStatus == BacklogStatus.BEATEN) maxOf(progress, 100) else progress,
    )

    companion object {
        fun from(game: Game, status: BacklogStatus = BacklogStatus.QUEUED, mine: Set<Platform> = emptySet()): SavedGame {
            val now = System.currentTimeMillis()
            return SavedGame(
                key = game.ref.key,
                source = game.ref.source.raw,
                externalId = game.ref.id,
                name = game.name,
                summary = game.summary,
                coverUrl = game.boxArtUrl,
                heroUrl = game.heroUrl,
                releaseDate = game.releaseDate?.toString(),
                releaseDatePrecision = game.releaseDatePrecision.storedRaw,
                year = game.year,
                genresCsv = game.genres.joinToString(","),
                platformIdsCsv = game.platforms.joinToString(",") { it.id },
                playingOn = game.platforms.firstOrNull { it in mine }?.id ?: game.platforms.singleOrNull()?.id,
                status = status.raw,
                addedAt = now,
                updatedAt = now,
                finishedAt = if (status == BacklogStatus.BEATEN) now else null,
                progress = if (status == BacklogStatus.BEATEN) 100 else 0,
                // Saving an unreleased game follows it for release-day alerts.
                isFollowing = game.isUpcoming(),
                steamAppId = game.steamAppId,
                wikiTitle = game.wikiTitle,
                metacritic = game.metacritic,
            )
        }
    }
}

/** Null for exact days, so stored rows and backups only carry the field when it means something. */
val DatePrecision.storedRaw: String? get() = if (this == DatePrecision.DAY) null else raw

/** A planned play session in the Planner. */
@Immutable
@Entity(tableName = "play_sessions")
data class PlaySession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "game_key") val gameKey: String,
    @ColumnInfo(name = "game_name") val gameName: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "platform_id") val platformId: String? = null,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 60,
    val note: String = "",
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: String? = null,
    @ColumnInfo(name = "has_reminder") val hasReminder: Boolean = true,
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    val endsAt: Long get() = startsAt + durationMinutes * 60_000L
    val ref: GameRef? get() = GameRef.parse(gameKey)
    val platform: Platform? get() = Platform.withId(platformId)
}

/** Sorting helper for Release Radar: coarse dates sort at the end of their window. */
val SavedGame.radarSortDate: LocalDate
    get() = release?.let { ReleaseDates.windowEnd(it, precision) } ?: LocalDate.MAX
