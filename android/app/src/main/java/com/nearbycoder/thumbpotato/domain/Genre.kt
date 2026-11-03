package com.nearbycoder.thumbpotato.domain

/** The fixed genre list (SPEC.md). The catalog and filters use exactly these labels. */
enum class Genre(val label: String, val rawgSlug: String?) {
    ACTION("Action", "action"),
    ADVENTURE("Adventure", "adventure"),
    RPG("RPG", "role-playing-games-rpg"),
    SHOOTER("Shooter", "shooter"),
    PLATFORMER("Platformer", "platformer"),
    PUZZLE("Puzzle", "puzzle"),
    STRATEGY("Strategy", "strategy"),
    SIMULATION("Simulation", "simulation"),
    SPORTS("Sports", "sports"),
    RACING("Racing", "racing"),
    FIGHTING("Fighting", "fighting"),
    HORROR("Horror", null),
    ROGUELIKE("Roguelike", null),
    METROIDVANIA("Metroidvania", null),
    PARTY("Party", "family"),
    MUSIC("Music", null),
    VISUAL_NOVEL("Visual Novel", null),
    SURVIVAL("Survival", null),
    SANDBOX("Sandbox", null);

    companion object {
        fun withLabel(label: String?): Genre? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }

        /** Name fragments from Steam genres/tags and RAWG genres → our list. Order matters: first match wins. */
        private val fragments: List<Pair<String, Genre>> = listOf(
            "role-playing" to RPG, "role playing" to RPG, "rpg" to RPG,
            "roguelike" to ROGUELIKE, "roguelite" to ROGUELIKE,
            "metroidvania" to METROIDVANIA,
            "platform" to PLATFORMER,
            "shooter" to SHOOTER, "fps" to SHOOTER, "shoot 'em up" to SHOOTER,
            "fighting" to FIGHTING, "beat 'em up" to FIGHTING, "hack and slash" to FIGHTING,
            "strategy" to STRATEGY, "tactic" to STRATEGY, "card" to STRATEGY, "board game" to STRATEGY,
            "simulat" to SIMULATION, "management" to SIMULATION,
            "sport" to SPORTS,
            "racing" to RACING, "driving" to RACING,
            "horror" to HORROR,
            "puzzle" to PUZZLE,
            "party" to PARTY, "family" to PARTY,
            "rhythm" to MUSIC, "music" to MUSIC,
            "visual novel" to VISUAL_NOVEL,
            "survival" to SURVIVAL,
            "sandbox" to SANDBOX, "open world" to SANDBOX,
            "adventure" to ADVENTURE,
            "action" to ACTION, "arcade" to ACTION,
        )

        /** Maps one Steam/RAWG genre name onto the fixed list; null when it has no counterpart (e.g. "Indie", "Massively Multiplayer"). */
        fun map(name: String): Genre? {
            val lower = name.trim().lowercase()
            if (lower.isEmpty() || lower.contains("massively multiplayer")) return null
            withLabel(lower)?.let { return it }
            return fragments.firstOrNull { (fragment, _) -> lower.contains(fragment) }?.second
        }

        /** Maps a list of genre names, keeping order; "Casual" becomes Puzzle only when nothing else matched. */
        fun mapAll(names: List<String>): List<String> {
            val mapped = names.mapNotNull(::map).distinct().map { it.label }
            if (mapped.isEmpty() && names.any { it.trim().equals("casual", ignoreCase = true) }) return listOf(PUZZLE.label)
            return mapped
        }
    }
}
