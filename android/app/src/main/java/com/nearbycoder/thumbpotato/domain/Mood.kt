package com.nearbycoder.thumbpotato.domain

import com.nearbycoder.thumbpotato.domain.Genre.ACTION
import com.nearbycoder.thumbpotato.domain.Genre.ADVENTURE
import com.nearbycoder.thumbpotato.domain.Genre.FIGHTING
import com.nearbycoder.thumbpotato.domain.Genre.HORROR
import com.nearbycoder.thumbpotato.domain.Genre.METROIDVANIA
import com.nearbycoder.thumbpotato.domain.Genre.MUSIC
import com.nearbycoder.thumbpotato.domain.Genre.PARTY
import com.nearbycoder.thumbpotato.domain.Genre.PLATFORMER
import com.nearbycoder.thumbpotato.domain.Genre.PUZZLE
import com.nearbycoder.thumbpotato.domain.Genre.RACING
import com.nearbycoder.thumbpotato.domain.Genre.ROGUELIKE
import com.nearbycoder.thumbpotato.domain.Genre.RPG
import com.nearbycoder.thumbpotato.domain.Genre.SANDBOX
import com.nearbycoder.thumbpotato.domain.Genre.SHOOTER
import com.nearbycoder.thumbpotato.domain.Genre.SIMULATION
import com.nearbycoder.thumbpotato.domain.Genre.SPORTS
import com.nearbycoder.thumbpotato.domain.Genre.STRATEGY
import com.nearbycoder.thumbpotato.domain.Genre.SURVIVAL
import com.nearbycoder.thumbpotato.domain.Genre.VISUAL_NOVEL

/** Moods, each a set of genres. */
enum class Mood(
    val id: String,
    val title: String,
    val blurb: String,
    /** Duotone gradient, top-left to bottom-right (AARRGGBB). */
    val gradientFrom: Long,
    val gradientTo: Long,
    val genres: List<Genre>,
) {
    COZY("cozy", "Cozy", "Unwind with something gentle", 0xFFE09A62, 0xFF5A2E1A, listOf(SIMULATION, SANDBOX, PUZZLE, PARTY, MUSIC)),
    ADRENALINE("adrenaline", "Adrenaline", "Fast, loud and competitive", 0xFFE2473F, 0xFF3B0B0B, listOf(SHOOTER, FIGHTING, RACING, ACTION, SPORTS)),
    EPIC("epic", "Epic", "Big worlds and long stories", 0xFF5B7FE8, 0xFF121C4A, listOf(RPG, ADVENTURE, METROIDVANIA)),
    COUCH_COOP("couch-coop", "Couch Co-op", "Games for the whole couch", 0xFFF0C94A, 0xFF5C4300, listOf(PARTY, SPORTS, RACING, FIGHTING, PLATFORMER)),
    SPOOKY("spooky", "Spooky", "Horror and survival", 0xFF8A5CF5, 0xFF22104D, listOf(HORROR, SURVIVAL)),
    BRAINY("brainy", "Brainy", "Puzzles, tactics and stories to think about", 0xFF3DBB9E, 0xFF0B3A33, listOf(PUZZLE, STRATEGY, VISUAL_NOVEL));

    private val labels: Set<String> = genres.map { it.label }.toSet()

    fun matches(game: Game): Boolean = game.genres.any { it in labels }

    companion object {
        fun withId(id: String?): Mood? = entries.firstOrNull { it.id == id }
    }
}

/** "I've got…" session lengths for Shuffle, each a set of genres that suit it. */
enum class SessionLength(val label: String, val shortLabel: String, val genres: List<Genre>) {
    QUICK("Quick break", "Quick break", listOf(PUZZLE, PARTY, RACING, FIGHTING, SPORTS, MUSIC, ROGUELIKE, PLATFORMER)),
    HOUR("An hour", "An hour", listOf(ACTION, SHOOTER, METROIDVANIA, ADVENTURE, HORROR, PLATFORMER, ROGUELIKE)),
    LONG("Long session", "Long session", listOf(RPG, STRATEGY, SIMULATION, SANDBOX, SURVIVAL, VISUAL_NOVEL, ADVENTURE));

    private val labels: Set<String> = genres.map { it.label }.toSet()

    /** Games with no known genre fit any length. */
    fun fits(game: Game): Boolean = game.genres.isEmpty() || game.genres.any { it in labels }
}
