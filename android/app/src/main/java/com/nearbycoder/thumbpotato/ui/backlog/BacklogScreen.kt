package com.nearbycoder.thumbpotato.ui.backlog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStats
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.hoursLabel
import com.nearbycoder.thumbpotato.ui.components.Cover
import com.nearbycoder.thumbpotato.ui.components.CoverBadge
import com.nearbycoder.thumbpotato.ui.components.CoverGrid
import com.nearbycoder.thumbpotato.ui.components.EmptyState
import com.nearbycoder.thumbpotato.ui.components.GroupCard
import com.nearbycoder.thumbpotato.ui.components.StatusBadge
import com.nearbycoder.thumbpotato.ui.components.ToggleChip
import com.nearbycoder.thumbpotato.ui.navigation.BrowseRoute
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppEvents
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.PlannerRoute
import com.nearbycoder.thumbpotato.ui.navigation.isWideLayout
import com.nearbycoder.thumbpotato.ui.navigation.screenInsets
import com.nearbycoder.thumbpotato.ui.theme.Thumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val events = LocalAppEvents.current
    val vm: BacklogViewModel = viewModel { BacklogViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = Thumb.colors
    val isWide = isWideLayout()
    var sortMenu by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<SavedGame?>(null) }
    val filters = state.filters
    val byKey = remember(state.all) { state.all.associateBy { it.key } }

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Backlog", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary) },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = "Sort", tint = colors.textPrimary)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = colors.surfaceElevated) {
                                BacklogSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label, color = if (sort == filters.sort) colors.accent else colors.textPrimary) },
                                        onClick = { vm.setSort(sort); sortMenu = false },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
                )
                StatusTabs(counts = state.counts, selected = filters.status, onSelect = vm::setStatus)
                Spacer(Modifier.height(4.dp))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val header: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.all.isNotEmpty()) StatsCard(state.stats)
                    if (state.availablePlatforms.size > 1 || state.availableGenres.size > 1 || filters.platform != null || filters.genre != null) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToggleChip("All platforms", selected = filters.platform == null, onClick = { vm.setPlatform(null) })
                            state.availablePlatforms.forEach { p ->
                                ToggleChip(p.shortName, selected = filters.platform == p, onClick = { vm.setPlatform(if (filters.platform == p) null else p) })
                            }
                            GenreMenu(selected = filters.genre, options = state.availableGenres, onSelect = vm::setGenre)
                        }
                    }
                }
            }
            if (state.visible.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    header()
                    EmptyState(
                        icon = Icons.Outlined.Bookmarks,
                        headline = emptyHeadline(filters.status, state.all.isEmpty()),
                        footnote = emptyFootnote(filters.status, state.all.isEmpty()),
                        actionLabel = if (state.all.isEmpty()) "Browse games" else null,
                        onAction = { actions.selectTab(BrowseRoute) },
                    )
                }
            } else {
                CoverGrid(
                    games = state.visible.map { it.asGame },
                    isWide = isWide,
                    onOpen = { actions.openGame(it.ref.key) },
                    header = { item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() } },
                    badgeFor = { g -> byKey[g.id]?.let { CoverBadge(it.backlogStatus, it.progress) } },
                    subtitleFor = { g ->
                        val saved = byKey[g.id]
                        when {
                            saved == null -> g.subtitle
                            saved.backlogStatus == BacklogStatus.PLAYING -> saved.progressLabel ?: saved.playingOnPlatform?.shortName ?: g.subtitle
                            saved.rating > 0 -> "★".repeat(saved.rating)
                            saved.isUpcoming() -> g.releaseLabel ?: g.subtitle
                            else -> g.subtitle
                        }
                    },
                    onLongPress = { g -> sheetFor = byKey[g.id] },
                )
            }
        }
    }

    sheetFor?.let { saved ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }, containerColor = colors.surfaceElevated) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(saved.coverUrl, modifier = Modifier.width(48.dp), radius = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(saved.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        StatusBadge(saved.backlogStatus)
                    }
                }
                HorizontalDivider(color = colors.hairline)
                BacklogStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status.label, color = colors.textPrimary) },
                        leadingIcon = { Canvas(Modifier.size(10.dp)) { drawCircle(colors.statusColor(status)) } },
                        trailingIcon = { if (status == saved.backlogStatus) Text("Current", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary) },
                        onClick = { vm.setGameStatus(saved.key, status); sheetFor = null },
                    )
                }
                HorizontalDivider(color = colors.hairline)
                DropdownMenuItem(
                    text = { Text("Plan a Session", color = colors.textPrimary) },
                    onClick = {
                        events.pendingPlanKey = saved.key
                        sheetFor = null
                        actions.selectTab(PlannerRoute)
                    },
                )
                DropdownMenuItem(text = { Text("Remove from Backlog", color = colors.danger) }, onClick = { vm.remove(saved.key); sheetFor = null })
            }
        }
    }
}

@Composable
private fun StatsCard(stats: BacklogStats) {
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Stat(stats.beatenThisYear.toString(), "beaten this year", Modifier.weight(1f))
            Stat(hoursLabel(stats.hoursLogged), "logged", Modifier.weight(1f))
            Stat(stats.backlogSize.toString(), "in backlog", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GenreMenu(selected: Genre?, options: List<Genre>, onSelect: (Genre?) -> Unit) {
    val colors = Thumb.colors
    var open by remember { mutableStateOf(false) }
    val active = selected != null
    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.secondaryFill)
                .clickable { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Genre: ${selected?.label ?: "Any"}", style = MaterialTheme.typography.labelMedium, color = if (active) colors.accent else colors.textPrimary, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = if (active) colors.accent else colors.textPrimary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceElevated) {
            (listOf<Genre?>(null) + options).forEach { g ->
                DropdownMenuItem(
                    text = { Text(g?.label ?: "Any genre", color = if (g == selected) colors.accent else colors.textPrimary) },
                    onClick = { onSelect(g); open = false },
                )
            }
        }
    }
}

@Composable
private fun StatusTabs(counts: Map<BacklogStatus, Int>, selected: BacklogStatus, onSelect: (BacklogStatus) -> Unit) {
    val colors = Thumb.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BacklogStatus.entries.forEach { status ->
            val isSelected = status == selected
            Column(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(status) }.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(status.label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) colors.textPrimary else colors.textSecondary, maxLines = 1)
                    Text((counts[status] ?: 0).toString(), style = MaterialTheme.typography.labelSmall, color = if (isSelected) colors.statusColor(status) else colors.textTertiary)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
                        .background(if (isSelected) colors.statusColor(status) else Color.Transparent),
                )
            }
        }
    }
}

private fun emptyHeadline(status: BacklogStatus, nothingSaved: Boolean): String = when {
    nothingSaved -> "Your backlog is empty"
    status == BacklogStatus.QUEUED -> "Nothing queued"
    status == BacklogStatus.PLAYING -> "Nothing in progress"
    status == BacklogStatus.BEATEN -> "Nothing beaten yet"
    else -> "Nothing shelved"
}

private fun emptyFootnote(status: BacklogStatus, nothingSaved: Boolean): String = when {
    nothingSaved -> "Add games from Tonight or Browse and they'll show up here."
    status == BacklogStatus.QUEUED -> "Add a game to Want to Play and it lands here."
    status == BacklogStatus.PLAYING -> "Mark a game as Playing from its page."
    status == BacklogStatus.BEATEN -> "Games you finish count toward your yearly total."
    else -> "Games you set aside are kept here, out of the way."
}
