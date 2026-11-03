package com.nearbycoder.thumbpotato.ui.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.GameDetail
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: GameDetail? = null,
)

class DetailViewModel(private val container: AppContainer, private val key: String) : ViewModel() {
    private val catalog = container.catalog
    private val library = container.library
    val ref: GameRef? = GameRef.parse(key)

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    /** The key the backlog uses: a Steam link to a catalog game resolves to the catalog's `wd-` key. */
    private val resolvedKey = MutableStateFlow(key)
    val currentKey: String get() = resolvedKey.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val saved: StateFlow<SavedGame?> = resolvedKey.flatMapLatest { library.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        val ref = ref
        if (ref == null) {
            _state.update { it.copy(loading = false, error = "That link doesn't point at a game.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Show the catalog record right away; the full detail fills in when it arrives.
            runCatching { catalog.preview(ref) }.getOrNull()?.let { preview ->
                resolvedKey.value = preview.game.ref.key
                if (_state.value.detail == null) _state.update { it.copy(detail = preview) }
            }
            runCatching { catalog.detail(ref) }
                .onSuccess { detail ->
                    resolvedKey.value = detail.game.ref.key
                    _state.update { it.copy(loading = false, detail = detail) }
                    // Keep the saved copy fresh with better art and dates.
                    library.refresh(detail.game)
                }
                // Keep a preview on screen if enrichment fails.
                .onFailure { t -> _state.update { it.copy(loading = false, error = if (it.detail == null) t.message ?: "Couldn't load this game." else null) } }
        }
    }

    private val mine: Set<Platform> get() = container.settings.current.myPlatformSet

    fun save(status: BacklogStatus = BacklogStatus.QUEUED, then: () -> Unit = {}) {
        val game = _state.value.detail?.game ?: return
        viewModelScope.launch {
            if (library.saved(game.ref.key) == null) library.save(game, status, mine) else library.setStatus(game.ref.key, status)
            then()
        }
    }

    fun ensureSaved(then: () -> Unit) {
        val game = _state.value.detail?.game ?: return
        viewModelScope.launch {
            if (library.saved(game.ref.key) == null) library.save(game, BacklogStatus.QUEUED, mine)
            then()
        }
    }

    fun remove() {
        viewModelScope.launch { library.remove(currentKey) }
    }

    private fun edit(change: (SavedGame) -> SavedGame) {
        viewModelScope.launch { library.edit(currentKey, change) }
    }

    fun setRating(rating: Int) = edit { it.copy(rating = rating.coerceIn(0, 5)) }
    fun setNotes(notes: String) = edit { it.copy(notes = notes) }
    fun setFollowing(following: Boolean) = edit { it.copy(isFollowing = following) }
    fun setOwned(owned: Boolean) = edit { it.copy(owned = owned) }
    fun setPlayingOn(platform: Platform?) = edit { it.copy(playingOn = platform?.id) }
    fun setHours(hours: Double) = edit { it.copy(hoursPlayed = hours.coerceIn(0.0, 99_999.0)) }

    fun setProgress(progress: Int) = edit { g ->
        val p = progress.coerceIn(0, 100)
        // Making progress on a game you only wanted to play means you're playing it.
        val status = if (p > 0 && g.backlogStatus == BacklogStatus.QUEUED) BacklogStatus.PLAYING.raw else g.status
        g.copy(progress = p, status = status)
    }
}
