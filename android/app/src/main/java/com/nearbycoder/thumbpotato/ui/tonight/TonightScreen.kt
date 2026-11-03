package com.nearbycoder.thumbpotato.ui.tonight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.ReleaseDates
import com.nearbycoder.thumbpotato.ui.components.CoverBadge
import com.nearbycoder.thumbpotato.ui.components.CoverCard
import com.nearbycoder.thumbpotato.ui.components.CoverRow
import com.nearbycoder.thumbpotato.ui.components.CoverRowPlaceholder
import com.nearbycoder.thumbpotato.ui.components.ErrorBanner
import com.nearbycoder.thumbpotato.ui.components.HeroCarousel
import com.nearbycoder.thumbpotato.ui.components.MoodGrid
import com.nearbycoder.thumbpotato.ui.components.SectionHeader
import com.nearbycoder.thumbpotato.ui.components.StatusBarScrim
import com.nearbycoder.thumbpotato.ui.navigation.BacklogRoute
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.edgeToEdgeInsets
import com.nearbycoder.thumbpotato.ui.navigation.isWideLayout
import com.nearbycoder.thumbpotato.ui.theme.Thumb

@Composable
fun TonightScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val vm: TonightViewModel = viewModel { TonightViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val isWide = isWideLayout()
    val colors = Thumb.colors
    val coverWidth = if (isWide) 150.dp else 120.dp
    val savedKeys = remember(saved) { saved.map { it.key }.toSet() }
    val playing = remember(saved) {
        saved.filter { it.backlogStatus == BacklogStatus.PLAYING }.sortedByDescending { it.updatedAt }.take(12)
    }
    val open: (com.nearbycoder.thumbpotato.domain.Game) -> Unit = { actions.openGame(it.ref.key) }

    val listState = rememberLazyListState()
    // The hero sits under the status bar; once it scrolls away, a scrim keeps the clock readable.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
    Scaffold(containerColor = colors.bg, contentWindowInsets = edgeToEdgeInsets()) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "hero") {
                if (state.hero.isNotEmpty()) {
                    HeroCarousel(
                        // Wide heroes show backdrop art, so lead with games that have it (stable: still by popularity).
                        games = if (isWide) state.hero.sortedBy { if (it.heroUrl != null) 0 else 1 } else state.hero,
                        isWide = isWide,
                        mine = state.mine,
                        isSaved = { it.ref.key in savedKeys },
                        onToggleSave = vm::toggleSave,
                        onDetails = open,
                        topOverlay = { TonightHeader(onSettings = actions::openSettings) },
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        TonightHeader(onSettings = actions::openSettings)
                        if (state.loading) {
                            Spacer(Modifier.height(8.dp))
                            CoverRowPlaceholder(title = "New Releases", coverWidth = coverWidth)
                        }
                    }
                }
            }

            state.error?.let { error ->
                item(key = "error") { ErrorBanner(message = error, onRetry = vm::load) }
            }

            if (playing.isNotEmpty()) {
                item(key = "playing") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = "Continue Playing", actionLabel = "See All", onAction = { actions.selectTab(BacklogRoute) })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(playing, key = { it.key }) { item ->
                                CoverCard(
                                    game = item.asGame,
                                    width = coverWidth,
                                    onClick = { actions.openGame(item.key) },
                                    subtitle = item.progressLabel ?: item.playingOnPlatform?.shortName ?: "Playing",
                                    badge = CoverBadge(item.backlogStatus, item.progress),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "new") {
                if (state.newReleases.isNotEmpty()) {
                    CoverRow(
                        "New Releases", state.newReleases, coverWidth, open,
                        actionLabel = "See All", onAction = actions::openNewReleases,
                        subtitleFor = { it.releaseLabel ?: it.subtitle },
                    )
                } else if (state.loading && state.hero.isNotEmpty()) {
                    CoverRowPlaceholder("New Releases", coverWidth)
                }
            }

            item(key = "soon") {
                if (state.comingSoon.isNotEmpty()) {
                    CoverRow(
                        "Coming Soon", state.comingSoon, coverWidth, open,
                        subtitle = "Add one to your backlog to get a release-day alert.",
                        actionLabel = "See All", onAction = actions::openComingSoon,
                        subtitleFor = { ReleaseDates.label(it.releaseDate, it.releaseDatePrecision) },
                    )
                }
            }

            items(state.platformRows, key = { "platform-${it.platform.id}" }) { row ->
                CoverRow(
                    title = "Top on ${row.platform.displayName}",
                    games = row.games,
                    coverWidth = coverWidth,
                    onOpen = open,
                    actionLabel = "See All",
                    onAction = { actions.openPlatform(row.platform) },
                )
            }

            if (state.showSteam) {
                item(key = "steam") {
                    CoverRow("Top Sellers on Steam", state.steamTop, coverWidth, open)
                }
            }

            item(key = "deals") {
                val byKey = remember(state.deals) { state.deals.associateBy { it.game.id } }
                CoverRow(
                    "Deals", state.deals.map { it.game }, coverWidth, open,
                    subtitle = "PC prices from CheapShark",
                    subtitleFor = { byKey[it.id]?.label },
                )
            }

            item(key = "moods") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Moods", subtitle = "Pick a feeling, get a shortlist.")
                    MoodGrid(onOpen = actions::openMood, columns = if (isWide) 3 else 2)
                }
            }

            item(key = "attribution") {
                Text(
                    "Box art and descriptions from Wikipedia. Facts from Wikidata. Store data from Steam; PC prices from CheapShark.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        if (scrolled) StatusBarScrim()
      }
    }
}

@Composable
private fun TonightHeader(onSettings: () -> Unit) {
    val colors = Thumb.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Tonight", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.textPrimary)
        }
    }
}
