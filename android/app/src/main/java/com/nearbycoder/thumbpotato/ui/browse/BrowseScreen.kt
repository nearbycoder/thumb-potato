package com.nearbycoder.thumbpotato.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.domain.GameFilter
import com.nearbycoder.thumbpotato.domain.GameList
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.ui.components.CoverGrid
import com.nearbycoder.thumbpotato.ui.components.EmptyState
import com.nearbycoder.thumbpotato.ui.components.ErrorBanner
import com.nearbycoder.thumbpotato.ui.components.GenreGrid
import com.nearbycoder.thumbpotato.ui.components.MoodGrid
import com.nearbycoder.thumbpotato.ui.components.PlatformTile
import com.nearbycoder.thumbpotato.ui.components.SectionHeader
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.isWideLayout
import com.nearbycoder.thumbpotato.ui.navigation.screenInsets
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val vm: BrowseViewModel = viewModel { BrowseViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val isWide = isWideLayout()
    val colors = Thumb.colors
    val focus = LocalFocusManager.current

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { Text("Browse", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary) },
                    actions = {
                        IconButton(onClick = actions::openSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
                )
                TextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Games, series, studios", color = colors.textTertiary) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textSecondary) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    ) { padding ->
        if (state.isIdle) {
            IdleBrowse(
                suggestions = vm.suggestions,
                mine = settings.myPlatformIds,
                onSuggest = vm::setQuery,
                onPlatform = actions::openPlatform,
                onMood = actions::openMood,
                onGenre = actions::openGenre,
                isWide = isWide,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.error != null -> ErrorBanner(state.error!!, modifier = Modifier.padding(top = 16.dp), onRetry = vm::retry)
                    state.searching && state.results.isEmpty() -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                    state.results.isEmpty() && !state.searching -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        headline = "No matches",
                        footnote = "Try another spelling, or search for the series name.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> CoverGrid(
                        games = state.results,
                        isWide = isWide,
                        onOpen = { actions.openGame(it.ref.key) },
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleBrowse(
    suggestions: List<String>,
    mine: Set<String>,
    onSuggest: (String) -> Unit,
    onPlatform: (Platform) -> Unit,
    onMood: (Mood) -> Unit,
    onGenre: (Genre) -> Unit,
    isWide: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Thumb.colors
    val platforms = remember(mine) { Platform.entries.sortedBy { if (it.id in mine) 0 else 1 } }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "suggestions") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "Try searching for")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions.size, key = { suggestions[it] }) { i ->
                        SuggestionChip(
                            onClick = { onSuggest(suggestions[i]) },
                            label = { Text(suggestions[i], color = colors.textPrimary) },
                            shape = CircleShape,
                            border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = colors.secondaryFill),
                        )
                    }
                }
            }
        }
        item(key = "platforms") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Platforms")
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    // Equal columns of ~84 dp tiles; the name wraps to two lines rather than truncating.
                    val columns = (maxWidth / 84.dp).toInt().coerceIn(3, 9)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        platforms.chunked(columns).forEach { row ->
                            Row {
                                row.forEach { p -> PlatformTile(p, onClick = { onPlatform(p) }, isMine = p.id in mine, modifier = Modifier.weight(1f)) }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "moods") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "Moods")
                MoodGrid(onOpen = onMood, columns = if (isWide) 3 else 2)
            }
        }
        item(key = "genres") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "Genres")
                GenreGrid(onOpen = onGenre, columns = if (isWide) 4 else 2)
            }
        }
    }
}

/** Paged grid for a platform, mood, genre or Tonight "See All" list, with the filter row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(kind: String, arg: String, actions: NavActions) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = Thumb.colors
    val isWide = isWideLayout()
    val platform = remember(kind, arg) { if (kind == "platform") Platform.withId(arg) ?: Platform.SWITCH else null }
    val mood = remember(kind, arg) { if (kind == "mood") Mood.withId(arg) ?: Mood.COZY else null }
    val genre = remember(kind, arg) { if (kind == "genre") Genre.entries.firstOrNull { it.name == arg } ?: Genre.ACTION else null }
    val list: GameList = when {
        platform != null -> GameList.OnPlatform(platform)
        mood != null -> GameList.OfMood(mood)
        genre != null -> GameList.OfGenre(genre)
        kind == "soon" -> GameList.ComingSoon
        else -> GameList.NewReleases
    }
    val title = platform?.displayName ?: mood?.title ?: genre?.label ?: if (kind == "soon") "Coming Soon" else "New Releases"
    val onMine = if (settings.myPlatforms.isEmpty()) "the most popular platforms" else "your platforms"
    val subtitle = when {
        platform != null -> "Most played on ${platform.displayName}"
        mood != null -> mood.blurb
        genre != null -> "${genre.label} games on $onMine"
        kind == "soon" -> "Upcoming on $onMine"
        else -> "Released in the last 120 days on $onMine"
    }
    val showGenre = mood == null && genre == null
    val vm: GridViewModel = viewModel(key = "grid-$kind-$arg-${settings.myPlatformIds.sorted()}") { GridViewModel(container, list) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = actions::back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    // Small add/remove toggle instead of a banner when a platform isn't one of yours.
                    if (platform != null) {
                        val isMine = settings.isMine(platform)
                        IconButton(onClick = { scope.launch { container.settings.togglePlatform(platform); container.catalog.reset() } }) {
                            Icon(
                                if (isMine) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircleOutline,
                                contentDescription = if (isMine) "Remove from my platforms" else "Add to my platforms",
                                tint = if (isMine) colors.accent else colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val header: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    FilterRow(filter = state.filter, onChange = vm::setFilter, showGenre = showGenre)
                }
            }
            when {
                state.loading -> Column(Modifier.fillMaxSize()) {
                    header(Modifier.padding(16.dp))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
                }
                state.error != null -> Column(Modifier.fillMaxSize()) {
                    header(Modifier.padding(16.dp))
                    ErrorBanner(state.error!!, onRetry = vm::load)
                }
                state.games.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    header(Modifier.padding(16.dp))
                    if (state.filter.isDefault) {
                        EmptyState(icon = Icons.Outlined.SearchOff, headline = "Nothing to show", footnote = "Nothing here right now.")
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            headline = "No matches",
                            footnote = "No games match these filters.",
                            actionLabel = "Reset",
                            onAction = { vm.setFilter(GameFilter()) },
                        )
                    }
                }
                else -> CoverGrid(
                    games = state.games,
                    isWide = isWide,
                    onOpen = { actions.openGame(it.ref.key) },
                    subtitleFor = { g -> if (list is GameList.ComingSoon) g.releaseLabel else g.subtitle },
                    header = { item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { header(Modifier) } },
                    loadingMore = state.loadingMore,
                    onNearEnd = vm::loadMore,
                )
            }
        }
    }
}
