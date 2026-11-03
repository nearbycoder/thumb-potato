package com.nearbycoder.thumbpotato.domain

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/** How a browse grid is ordered. */
enum class GameSort(val label: String) {
    POPULAR("Popular"),
    NEWEST("Newest"),
    TOP_RATED("Top rated"),
    TITLE("A–Z"),
}

/** Release windows for browse grids. */
enum class Released(val label: String) {
    ANY("Any"),
    UPCOMING("Upcoming"),
    THIS_YEAR("This year"),
    LAST5("Last 5 years"),
    Y2020S("2020s"),
    Y2010S("2010s"),
    Y2000S("2000s"),
    BEFORE2000("Before 2000");

    /** Inclusive (from, to) years for the year-based windows; null means unbounded. */
    fun bounds(currentYear: Int): Pair<Int?, Int?> = when (this) {
        ANY, UPCOMING -> null to null
        THIS_YEAR -> currentYear to currentYear
        LAST5 -> (currentYear - 4) to currentYear
        Y2020S -> 2020 to 2029
        Y2010S -> 2010 to 2019
        Y2000S -> 2000 to 2009
        BEFORE2000 -> null to 1999
    }

    fun contains(game: Game, today: LocalDate): Boolean {
        if (this == ANY) return true
        if (this == UPCOMING) return game.isUpcoming(today)
        val year = game.year ?: return false
        val (from, to) = bounds(today.year)
        return (from == null || year >= from) && (to == null || year <= to)
    }
}

/** Critic-score floors (Metacritic, 0–100). */
enum class CriticScore(val label: String, val min: Int?) {
    ANY("Any", null),
    S70("70+", 70),
    S80("80+", 80),
    S90("90+", 90),
}

/** Sort + filters for a browse grid. Shared semantics with the Apple app. */
@Immutable
data class GameFilter(
    val sort: GameSort = GameSort.POPULAR,
    val released: Released = Released.ANY,
    val genre: Genre? = null,
    val score: CriticScore = CriticScore.ANY,
) {
    val isDefault: Boolean
        get() = sort == GameSort.POPULAR && released == Released.ANY && genre == null && score == CriticScore.ANY

    /** Stable string for memo keys, e.g. `POPULAR|ANY|-|ANY`. */
    val key: String get() = "${sort.name}|${released.name}|${genre?.name ?: "-"}|${score.name}"

    fun matches(game: Game, today: LocalDate = LocalDate.now()): Boolean {
        if (!released.contains(game, today)) return false
        genre?.let { g -> if (g.label !in game.genres) return false }
        score.min?.let { min -> if ((game.metacritic ?: 0) < min) return false }
        return true
    }

    /** Keeps the games that match, in this filter's order. */
    fun apply(list: List<Game>, today: LocalDate = LocalDate.now()): List<Game> = sorted(list.filter { matches(it, today) }, today)

    /**
     * Orders games by [sort] without filtering. POPULAR keeps the incoming order (catalogs
     * arrive by popularity); NEWEST drops anything not out yet, unless the filter asks for upcoming.
     */
    fun sorted(list: List<Game>, today: LocalDate = LocalDate.now()): List<Game> = when (sort) {
        GameSort.POPULAR -> list
        GameSort.NEWEST -> list
            .filter { released == Released.UPCOMING || !it.isUpcoming(today) }
            .sortedWith(compareByDescending<Game, LocalDate?>(nullsFirst()) { it.releaseDate })
        GameSort.TOP_RATED -> list.sortedWith(compareByDescending<Game, Int?>(nullsFirst()) { it.metacritic })
        GameSort.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** RAWG `/games` params for this filter (ordering, dates, genre, metacritic). */
    fun rawgParams(today: LocalDate = LocalDate.now()): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        out["ordering"] = when (sort) {
            GameSort.POPULAR -> "-added"
            GameSort.NEWEST -> "-released"
            GameSort.TOP_RATED -> "-metacritic"
            GameSort.TITLE -> "name"
        }
        var from: LocalDate? = null
        var to: LocalDate? = null
        if (released == Released.UPCOMING) {
            from = today.plusDays(1)
            to = today.plusYears(5)
        } else {
            val (fromYear, toYear) = released.bounds(today.year)
            fromYear?.let { from = LocalDate.of(it, 1, 1) }
            toYear?.let { to = LocalDate.of(it, 12, 31) }
            if (released == Released.BEFORE2000) from = LocalDate.of(1970, 1, 1)
            if (sort == GameSort.NEWEST) {
                from = from ?: LocalDate.of(1970, 1, 1)
                to = to?.let { minOf(it, today) } ?: today
            }
        }
        if (from != null && to != null) out["dates"] = "$from,$to"
        genre?.rawgSlug?.let { out["genres"] = it }
        score.min?.let { out["metacritic"] = "$it,100" }
        return out
    }
}
