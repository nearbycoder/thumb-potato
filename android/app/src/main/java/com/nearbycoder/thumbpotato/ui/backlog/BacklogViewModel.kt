package com.nearbycoder.thumbpotato.ui.backlog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStats
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class BacklogSort(val label: String) {
    ADDED("Date added"),
    TITLE("Title"),
    RATING("Rating"),
    RELEASE("Release date"),
}

@Immutable
data class BacklogFilters(
    val status: BacklogStatus = BacklogStatus.QUEUED,
    val platform: Platform? = null,
    val genre: Genre? = null,
    val sort: BacklogSort = BacklogSort.ADDED,
)

@Immutable
data class BacklogState(
    val all: List<SavedGame> = emptyList(),
    val filters: BacklogFilters = BacklogFilters(),
) {
    val counts: Map<BacklogStatus, Int> get() = BacklogStatus.entries.associateWith { s -> all.count { it.backlogStatus == s } }
    val stats: BacklogStats get() = BacklogStats.of(all)

    private val inStatus: List<SavedGame> get() = all.filter { it.backlogStatus == filters.status }

    /** A saved game counts as "on" a platform when you play it there, or it's out there and you haven't picked one. */
    private fun SavedGame.isOn(p: Platform): Boolean = playingOn == p.id || (playingOn == null && p.id in platformIds)

    val availablePlatforms: List<Platform> get() = Platform.entries.filter { p -> inStatus.any { it.isOn(p) } }
    val availableGenres: List<Genre> get() = Genre.entries.filter { g -> inStatus.any { g.label in it.genres } }

    val visible: List<SavedGame>
        get() {
            val filtered = inStatus.filter { g ->
                (filters.platform == null || g.isOn(filters.platform)) && (filters.genre == null || filters.genre.label in g.genres)
            }
            return when (filters.sort) {
                BacklogSort.ADDED -> filtered.sortedByDescending { it.addedAt }
                BacklogSort.TITLE -> filtered.sortedBy { it.name.lowercase() }
                BacklogSort.RATING -> filtered.sortedWith(compareByDescending<SavedGame> { it.rating }.thenByDescending { it.updatedAt })
                BacklogSort.RELEASE -> filtered.sortedWith(compareByDescending<SavedGame> { it.release ?: LocalDate.MIN }.thenBy { it.name })
            }
        }
}

class BacklogViewModel(private val container: AppContainer) : ViewModel() {
    private val filters = MutableStateFlow(BacklogFilters())

    val state: StateFlow<BacklogState> = combine(container.library.savedGames, filters) { games, f -> BacklogState(games, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BacklogState(all = container.library.savedGames.value))

    fun setStatus(status: BacklogStatus) = filters.update { it.copy(status = status, platform = null, genre = null) }
    fun setPlatform(platform: Platform?) = filters.update { it.copy(platform = platform) }
    fun setGenre(genre: Genre?) = filters.update { it.copy(genre = genre) }
    fun setSort(sort: BacklogSort) = filters.update { it.copy(sort = sort) }

    fun setGameStatus(key: String, status: BacklogStatus) {
        viewModelScope.launch { container.library.setStatus(key, status) }
    }

    fun remove(key: String) {
        viewModelScope.launch { container.library.remove(key) }
    }
}
