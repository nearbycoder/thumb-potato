package com.nearbycoder.thumbpotato.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class GameSource(val raw: String, val displayName: String) {
    WD("wd", "Wikidata"),
    STEAM("steam", "Steam"),
    RAWG("rawg", "RAWG");

    companion object {
        fun fromRaw(raw: String?): GameSource? = entries.firstOrNull { it.raw == raw }
    }
}

/** A stable pointer to a game in one of the sources, e.g. `wd-Q17185964` or `steam-1145360`. */
@Immutable
data class GameRef(val source: GameSource, val id: String) {
    val key: String get() = "${source.raw}-$id"

    val deepLink: String get() = "thumbpotato://game/$key"

    val webUrl: String
        get() = when (source) {
            GameSource.WD -> "https://www.wikidata.org/wiki/$id"
            GameSource.STEAM -> "https://store.steampowered.com/app/$id"
            GameSource.RAWG -> "https://rawg.io/games/$id"
        }

    companion object {
        fun parse(key: String?): GameRef? {
            if (key.isNullOrBlank()) return null
            val parts = key.split("-", limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            val source = GameSource.fromRaw(parts[0]) ?: return null
            return GameRef(source, parts[1])
        }
    }
}

/** How exactly a release date is known. Coarse dates point at the 1st of their year/month. */
enum class DatePrecision(val raw: String) {
    DAY("day"),
    MONTH("month"),
    YEAR("year");

    companion object {
        /** Absent or unknown means an exact day. */
        fun fromRaw(raw: String?): DatePrecision = entries.firstOrNull { it.raw == raw } ?: DAY
    }
}

/** Formats release dates the way every screen should: never "Jan 1, 2027" for a year-only date. */
object ReleaseDates {
    // Built per call so a locale change while the app runs is picked up.
    private val monthYear: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun label(date: LocalDate?, precision: DatePrecision): String? {
        date ?: return null
        return when (precision) {
            DatePrecision.YEAR -> date.year.toString()
            DatePrecision.MONTH -> date.format(monthYear)
            DatePrecision.DAY -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }

    /** Release Radar wording: a countdown for exact dates, "TBA 2027" / "March 2027" otherwise. */
    fun radarLabel(date: LocalDate?, precision: DatePrecision, today: LocalDate = LocalDate.now()): String {
        date ?: return "TBA"
        return when (precision) {
            DatePrecision.YEAR -> "TBA ${date.year}"
            DatePrecision.MONTH -> date.format(monthYear)
            DatePrecision.DAY -> {
                val days = ChronoUnit.DAYS.between(today, date)
                when {
                    days < 0 -> "Out now"
                    days == 0L -> "Today"
                    days == 1L -> "Tomorrow"
                    else -> "in $days days"
                }
            }
        }
    }

    /** Last day the release could fall on: the date itself, or the end of its month/year. */
    fun windowEnd(date: LocalDate, precision: DatePrecision): LocalDate = when (precision) {
        DatePrecision.DAY -> date
        DatePrecision.MONTH -> date.withDayOfMonth(date.lengthOfMonth())
        DatePrecision.YEAR -> date.withDayOfYear(date.lengthOfYear())
    }
}

@Immutable
data class Game(
    val ref: GameRef,
    val name: String,
    val summary: String = "",
    /** Wikipedia box art. */
    val coverUrl: String? = null,
    /** Steam library capsule (portrait). */
    val artUrl: String? = null,
    /** Wide backdrop (Steam library hero). */
    val heroUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val releaseDatePrecision: DatePrecision = DatePrecision.DAY,
    val year: Int? = releaseDate?.year,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    val platforms: List<Platform> = emptyList(),
    /** 0–100. */
    val metacritic: Int? = null,
    val steamAppId: Int? = null,
    val wikiTitle: String? = null,
    val popularity: Double = 0.0,
    /** Steam Deck: 1 unknown, 2 playable, 3 verified. */
    val steamDeck: Int? = null,
) {
    val id: String get() = ref.key

    /** Box art: Wikipedia cover first, Steam library capsule second. */
    val boxArtUrl: String? get() = coverUrl ?: artUrl

    val isExactDate: Boolean get() = releaseDatePrecision == DatePrecision.DAY

    val releaseLabel: String? get() = ReleaseDates.label(releaseDate, releaseDatePrecision)

    /** Not out yet: an exact date after today, or a coarse window that hasn't closed. */
    fun isUpcoming(today: LocalDate = LocalDate.now()): Boolean {
        val date = releaseDate ?: return false
        return if (isExactDate) date > today else ReleaseDates.windowEnd(date, releaseDatePrecision) >= today
    }

    /** `2023 · Action` */
    val subtitle: String
        get() = buildList {
            year?.let { add(it.toString()) }
            genres.firstOrNull()?.let { add(it) }
        }.joinToString(" · ")

    /** `2025 · Action, RPG · Metacritic 92` */
    val metadataLine: String
        get() = buildList {
            year?.let { add(it.toString()) }
            if (genres.isNotEmpty()) add(genres.take(2).joinToString(", "))
            metacritic?.let { add("Metacritic $it") }
        }.joinToString(" · ")

    /** Fills gaps in this game with whatever [other] knows. */
    fun merging(other: Game): Game = copy(
        summary = summary.ifBlank { other.summary },
        coverUrl = coverUrl ?: other.coverUrl,
        artUrl = artUrl ?: other.artUrl,
        heroUrl = heroUrl ?: other.heroUrl,
        releaseDate = releaseDate ?: other.releaseDate,
        releaseDatePrecision = if (releaseDate != null) releaseDatePrecision else other.releaseDatePrecision,
        year = year ?: other.year,
        genres = genres.ifEmpty { other.genres },
        developer = developer ?: other.developer,
        publisher = publisher ?: other.publisher,
        platforms = (platforms + other.platforms).distinct().sortedBy { it.ordinal },
        metacritic = metacritic ?: other.metacritic,
        steamAppId = steamAppId ?: other.steamAppId,
        wikiTitle = wikiTitle ?: other.wikiTitle,
        popularity = maxOf(popularity, other.popularity),
        steamDeck = steamDeck ?: other.steamDeck,
    )
}

/** A store row under Where to Play. */
@Immutable
data class StoreOffer(
    val store: Store,
    val url: String,
    /** Current price when known (Steam), e.g. `$24.99`. */
    val price: String? = null,
    val regularPrice: String? = null,
    val isMine: Boolean = false,
)

/** A PC price from CheapShark. */
@Immutable
data class Deal(
    val storeId: String,
    val storeName: String,
    val price: Double,
    val retailPrice: Double,
    val savings: Double,
    val dealId: String,
) {
    val url: String get() = "https://www.cheapshark.com/redirect?dealID=$dealId"
    val isDiscounted: Boolean get() = price < retailPrice - 0.005
    val priceLabel: String get() = money(price)
    val retailLabel: String get() = money(retailPrice)

    companion object {
        fun money(value: Double): String = if (value == 0.0) "Free" else String.format(Locale.US, "$%.2f", value)
    }
}

/** A game on sale, for the Tonight "Deals" row. */
@Immutable
data class DealGame(val game: Game, val salePrice: Double, val normalPrice: Double, val savings: Double, val dealId: String) {
    val label: String
        get() = if (savings >= 1) "${Deal.money(salePrice)} · -${savings.toInt()}%" else Deal.money(salePrice)
}

@Immutable
data class GameDetail(
    val game: Game,
    val screenshots: List<String> = emptyList(),
    val trailerUrl: String? = null,
    val stores: List<StoreOffer> = emptyList(),
    val pcDeals: List<Deal> = emptyList(),
    val similar: List<Game> = emptyList(),
    val website: String? = null,
    /** "Very Positive · 91%" */
    val steamReviews: String? = null,
    val steamPrice: String? = null,
) {
    /** Backdrop: Steam hero, else the first screenshot (null → blurred box art). */
    val backdropUrl: String? get() = game.heroUrl ?: screenshots.firstOrNull()
}

/** Strips light HTML from descriptions. */
fun String.strippingHtml(): String =
    replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()

fun <T> interleave(a: List<T>, b: List<T>): List<T> {
    val out = ArrayList<T>(a.size + b.size)
    for (i in 0 until maxOf(a.size, b.size)) {
        if (i < a.size) out += a[i]
        if (i < b.size) out += b[i]
    }
    return out
}

/** `1.5h`, `12h`, `45m` */
fun hoursLabel(hours: Double): String = when {
    hours <= 0.0 -> "0h"
    hours < 1.0 -> "${(hours * 60).toInt()}m"
    hours % 1.0 == 0.0 -> "${hours.toInt()}h"
    else -> String.format(Locale.US, "%.1fh", hours)
}

val Int.durationLabel: String
    get() {
        val h = this / 60
        val m = this % 60
        if (h == 0) return "${m}m"
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }
