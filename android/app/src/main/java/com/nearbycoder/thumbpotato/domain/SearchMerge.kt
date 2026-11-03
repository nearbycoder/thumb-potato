package com.nearbycoder.thumbpotato.domain

/** Ranking and de-duplication for search, shared by every source. */
object SearchMerge {
    fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Catalog games whose name contains [query]: exact, then prefix, then word-start, then anywhere; ties by popularity. */
    fun rankLocal(games: Collection<Game>, query: String, limit: Int = 30): List<Game> {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val compact = normalize(needle)
        return games.mapNotNull { game ->
            val name = game.name.lowercase()
            val rank = when {
                name == needle || normalize(name) == compact -> 0
                name.startsWith(needle) -> 1
                name.split(' ', ':', '-').any { it.startsWith(needle) } -> 2
                name.contains(needle) || (compact.length >= 3 && normalize(name).contains(compact)) -> 3
                else -> return@mapNotNull null
            }
            rank to game
        }.sortedWith(compareBy<Pair<Int, Game>> { it.first }.thenByDescending { it.second.popularity })
            .map { it.second }
            .take(limit)
    }

    /**
     * Local catalog matches first, then Wikipedia and Steam interleaved. A result is dropped when
     * its key or Steam app ID was already seen; Steam results are also dropped when the same name
     * (and year, when both know it) already appeared from another source.
     */
    fun merge(local: List<Game>, wikipedia: List<Game>, steam: List<Game>): List<Game> {
        val out = ArrayList<Game>()
        val keys = HashSet<String>()
        val steamIds = HashSet<Int>()
        fun add(game: Game, byName: Boolean) {
            if (game.ref.key in keys) return
            if (game.steamAppId != null && game.steamAppId in steamIds) return
            if (byName) {
                val name = normalize(game.name)
                val clash = out.any { other ->
                    normalize(other.name) == name && (game.year == null || other.year == null || game.year == other.year)
                }
                if (clash) return
            }
            out += game
            keys += game.ref.key
            game.steamAppId?.let { steamIds += it }
        }
        local.forEach { add(it, byName = false) }
        for (i in 0 until maxOf(wikipedia.size, steam.size)) {
            wikipedia.getOrNull(i)?.let { add(it, byName = false) }
            steam.getOrNull(i)?.let { add(it, byName = true) }
        }
        return out
    }
}
