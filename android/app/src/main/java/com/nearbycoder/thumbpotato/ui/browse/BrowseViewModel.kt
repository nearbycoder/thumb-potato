package com.nearbycoder.thumbpotato.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameFilter
import com.nearbycoder.thumbpotato.domain.GameList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BrowseState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Game> = emptyList(),
    val error: String? = null,
) {
    val isIdle: Boolean get() = query.trim().length < 2
}

class BrowseViewModel(container: AppContainer) : ViewModel() {
    private val catalog = container.catalog
    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state
    private val queries = MutableStateFlow("")
    private var searchJob: Job? = null

    val suggestions = listOf("Zelda", "Hades", "Elden Ring", "Stardew Valley", "Mario Kart", "Balatro", "Hollow Knight", "Baldur's Gate 3")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queries.debounce(350).distinctUntilChanged().collect { q -> search(q) }
        }
    }

    private fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null) }
            runCatching { catalog.search(q) }
                .onSuccess { list -> _state.update { it.copy(results = list, searching = false) } }
                .onFailure { t -> _state.update { it.copy(searching = false, error = t.message ?: "Search failed.") } }
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queries.value = q
    }

    fun retry() = search(_state.value.query)
}

@Immutable
data class ListState(
    val loading: Boolean = true,
    val games: List<Game> = emptyList(),
    val filter: GameFilter = GameFilter(),
    val error: String? = null,
    val loadingMore: Boolean = false,
    val exhausted: Boolean = false,
)

/** A grid that keeps loading pages as you scroll, until a page comes back empty. */
class GridViewModel(container: AppContainer, private val list: GameList) : ViewModel() {
    private val catalog = container.catalog
    private val _state = MutableStateFlow(ListState())
    val state: StateFlow<ListState> = _state
    private var nextPage = 1
    private var job: Job? = null

    init { load() }

    /** Applies a new sort/filter and reloads from the first page. */
    fun setFilter(filter: GameFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        load()
    }

    fun load() {
        job?.cancel()
        nextPage = 1
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, games = emptyList(), exhausted = false, loadingMore = false) }
            runCatching { catalog.page(list, 1, _state.value.filter) }
                .onSuccess { page ->
                    nextPage = 2
                    _state.update { it.copy(games = page.distinctBy { g -> g.id }, loading = false, exhausted = page.isEmpty()) }
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message ?: "Couldn't load games.") } }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.exhausted || job?.isActive == true) return
        val page = nextPage
        job = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching { catalog.page(list, page, current.filter) }
                .onSuccess { batch ->
                    nextPage = page + 1
                    _state.update { s ->
                        val known = s.games.mapTo(HashSet()) { it.id }
                        val fresh = batch.filter { it.id !in known }
                        // Stop on an empty page, or once pages only repeat what we have.
                        val done = batch.isEmpty() || fresh.isEmpty() || page >= 40
                        s.copy(games = s.games + fresh, loadingMore = false, exhausted = done)
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }
}
