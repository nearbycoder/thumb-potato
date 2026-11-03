package com.nearbycoder.thumbpotato.ui.shuffle

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.SessionLength
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShufflePool(val label: String) { BACKLOG("Backlog"), DISCOVER("Discover") }

@Immutable
data class ShuffleState(
    val pool: ShufflePool = ShufflePool.BACKLOG,
    val mood: Mood? = null,
    val length: SessionLength? = null,
    val platform: Platform? = null,
    val platformOptions: List<Platform> = emptyList(),
    val loading: Boolean = false,
    val candidates: List<Game> = emptyList(),
    val error: String? = null,
    val spinning: Boolean = false,
    val winner: Game? = null,
    val spinId: Int = 0,
)

/** Pure pool filter, shared by both pools. */
fun shuffleFilter(games: List<Game>, mood: Mood?, length: SessionLength?, platform: Platform?): List<Game> =
    games.filter { g ->
        g.boxArtUrl != null &&
            (mood == null || mood.matches(g)) &&
            (length == null || length.fits(g)) &&
            (platform == null || platform in g.platforms)
    }

class ShuffleViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ShuffleState())
    val state: StateFlow<ShuffleState> = _state
    private var job: Job? = null

    init { refresh() }

    fun setPool(pool: ShufflePool) { _state.update { it.copy(pool = pool, winner = null) }; refresh() }
    fun setMood(mood: Mood?) { _state.update { it.copy(mood = mood, winner = null) }; refresh() }
    fun setLength(length: SessionLength?) { _state.update { it.copy(length = length, winner = null) }; refresh() }
    fun setPlatform(platform: Platform?) { _state.update { it.copy(platform = platform, winner = null) }; refresh() }

    private fun backlogGame(saved: SavedGame): Game = saved.asGame.let { g ->
        // In the backlog, the platform you play on is the one that counts.
        saved.playingOnPlatform?.let { g.copy(platforms = listOf(it)) } ?: g
    }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settings.current
            val options = settings.myPlatforms.ifEmpty { Platform.entries }
            val s = _state.value.let { if (it.platform != null && it.platform !in options) it.copy(platform = null) else it }
            _state.update { it.copy(loading = true, error = null, platformOptions = options, platform = s.platform) }
            val result = runCatching {
                val base = when (s.pool) {
                    ShufflePool.BACKLOG -> container.library.savedGames.value
                        .filter { it.backlogStatus == BacklogStatus.QUEUED || it.backlogStatus == BacklogStatus.PLAYING }
                        .filter { !it.isUpcoming() }
                        .map(::backlogGame)
                    ShufflePool.DISCOVER -> container.catalog.discover(s.platform?.let { listOf(it) } ?: settings.feedPlatforms)
                }
                shuffleFilter(base, s.mood, s.length, s.platform)
            }
            result
                .onSuccess { list -> _state.update { it.copy(loading = false, candidates = list.shuffled()) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message ?: "Couldn't build a pool.") } }
        }
    }

    fun spin() {
        val s = _state.value
        if (s.candidates.isEmpty() || s.spinning) return
        _state.update { it.copy(spinning = true, winner = null, spinId = it.spinId + 1, candidates = it.candidates.shuffled()) }
    }

    fun land(winner: Game) {
        _state.update { it.copy(spinning = false, winner = winner) }
    }

    /** "Play now": marks the game Playing. */
    fun playNow(game: Game, done: () -> Unit) {
        viewModelScope.launch {
            container.library.save(game, BacklogStatus.PLAYING, container.settings.current.myPlatformSet)
            done()
        }
    }

    fun ensureSaved(game: Game, done: () -> Unit) {
        viewModelScope.launch {
            if (container.library.saved(game.ref.key) == null) container.library.save(game, BacklogStatus.QUEUED, container.settings.current.myPlatformSet)
            done()
        }
    }
}
