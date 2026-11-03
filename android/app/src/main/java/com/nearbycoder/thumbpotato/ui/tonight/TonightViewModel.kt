package com.nearbycoder.thumbpotato.ui.tonight

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.DealGame
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PlatformRow(val platform: Platform, val games: List<Game>)

@Immutable
data class TonightState(
    val loading: Boolean = true,
    val error: String? = null,
    val mine: Set<Platform> = emptySet(),
    val hero: List<Game> = emptyList(),
    val newReleases: List<Game> = emptyList(),
    val comingSoon: List<Game> = emptyList(),
    val platformRows: List<PlatformRow> = emptyList(),
    val showSteam: Boolean = false,
    val steamTop: List<Game> = emptyList(),
    val deals: List<DealGame> = emptyList(),
)

class TonightViewModel(private val container: AppContainer) : ViewModel() {
    private val catalog = container.catalog
    private val library = container.library

    private val _state = MutableStateFlow(TonightState())
    val state: StateFlow<TonightState> = _state
    val saved: StateFlow<List<SavedGame>> = library.savedGames

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings
                .map { Triple(it.myPlatformIds, it.region, it.effectiveRawgKey) }
                .distinctUntilChanged()
                .collect { load() }
        }
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val settings = container.settings.current
            val feed = settings.feedPlatforms
            val showSteam = settings.myPlatforms.isEmpty() || settings.myPlatforms.any { it == Platform.PC || it == Platform.MAC }
            _state.update {
                it.copy(
                    loading = true, error = null, mine = settings.myPlatformSet, showSteam = showSteam,
                    platformRows = it.platformRows.filter { row -> row.platform in feed },
                )
            }
            var firstError: String? = null
            fun fail(t: Throwable) { if (firstError == null) firstError = t.message ?: "Something went wrong." }

            val jobs = buildList {
                add(launch { runCatching { catalog.hero() }.onSuccess { l -> _state.update { it.copy(hero = l) } }.onFailure(::fail) })
                add(launch { runCatching { catalog.newReleases() }.onSuccess { l -> _state.update { it.copy(newReleases = l) } }.onFailure(::fail) })
                add(launch { runCatching { catalog.comingSoon() }.onSuccess { l -> _state.update { it.copy(comingSoon = l) } }.onFailure(::fail) })
                feed.forEach { platform ->
                    add(launch {
                        val games = runCatching { catalog.topOn(platform) }.getOrDefault(emptyList())
                        _state.update { s ->
                            val rows = (s.platformRows.filter { it.platform != platform } + PlatformRow(platform, games))
                                .filter { it.games.isNotEmpty() }
                                .sortedBy { feed.indexOf(it.platform) }
                            s.copy(platformRows = rows)
                        }
                    })
                }
                if (showSteam) {
                    add(launch { runCatching { catalog.steamTopSellers() }.onSuccess { l -> _state.update { it.copy(steamTop = l) } } })
                }
                add(launch { runCatching { catalog.deals() }.onSuccess { l -> _state.update { it.copy(deals = l) } } })
            }
            jobs.forEach { it.join() }
            _state.update { s ->
                val nothingLoaded = s.hero.isEmpty() && s.newReleases.isEmpty() && s.platformRows.isEmpty()
                s.copy(loading = false, error = if (nothingLoaded) firstError else null)
            }
        }
    }

    /** Saving an upcoming game (e.g. from Coming Soon) follows it for release-day alerts. */
    fun toggleSave(game: Game) {
        viewModelScope.launch {
            val existing = library.saved(game.ref.key)
            if (existing == null) library.save(game, BacklogStatus.QUEUED, container.settings.current.myPlatformSet) else library.remove(game.ref.key)
        }
    }
}
