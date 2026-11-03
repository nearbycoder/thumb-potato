package com.nearbycoder.thumbpotato.ui.shuffle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.SessionLength
import com.nearbycoder.thumbpotato.ui.components.Artwork
import com.nearbycoder.thumbpotato.ui.components.Cover
import com.nearbycoder.thumbpotato.ui.components.EmptyState
import com.nearbycoder.thumbpotato.ui.components.ErrorBanner
import com.nearbycoder.thumbpotato.ui.components.PlatformMonogramRow
import com.nearbycoder.thumbpotato.ui.components.PrimaryButton
import com.nearbycoder.thumbpotato.ui.components.SecondaryButton
import com.nearbycoder.thumbpotato.ui.components.ToggleChip
import com.nearbycoder.thumbpotato.ui.navigation.BrowseRoute
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppEvents
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.PlannerRoute
import com.nearbycoder.thumbpotato.ui.navigation.screenInsets
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShuffleScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val events = LocalAppEvents.current
    val vm: ShuffleViewModel = viewModel { ShuffleViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = Thumb.colors

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text("Shuffle", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Filters(state = state, vm = vm)
            // The deck takes whatever height is left and scales to it.
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val deckHeight = maxHeight
                val deckWidth = maxWidth
                when {
                    state.loading -> CircularProgressIndicator(color = colors.accent)
                    state.error != null -> ErrorBanner(state.error!!, onRetry = vm::refresh)
                    state.candidates.isEmpty() -> Box(Modifier.verticalScroll(rememberScrollState())) {
                        EmptyState(
                            icon = Icons.Outlined.Shuffle,
                            headline = if (state.pool == ShufflePool.BACKLOG) "Nothing to shuffle" else "No matches",
                            footnote = if (state.pool == ShufflePool.BACKLOG) {
                                "Add a few games to Want to Play, loosen the filters, or switch the pool to Discover."
                            } else "Try a different mood, session length or platform.",
                            actionLabel = if (state.pool == ShufflePool.BACKLOG) "Browse games" else null,
                            onAction = { actions.selectTab(BrowseRoute) },
                        )
                    }
                    state.winner != null && !state.spinning -> {
                        val winner = state.winner!!
                        WinnerCard(
                            game = winner,
                            availableHeight = deckHeight,
                            onPlayNow = { vm.playNow(winner) { actions.openGame(winner.ref.key) } },
                            onPlan = { vm.ensureSaved(winner) { events.pendingPlanKey = winner.ref.key; actions.selectTab(PlannerRoute) } },
                            onAgain = vm::spin,
                        )
                    }
                    else -> {
                        // Reserve room for the caption and button; covers are 3:4 plus the stack's peek.
                        val coverWidth = minOf(deckWidth * 0.62f, (deckHeight - 132.dp) * 0.75f, 260.dp).coerceAtLeast(72.dp)
                        CoverStack(
                            candidates = state.candidates,
                            spinning = state.spinning,
                            spinId = state.spinId,
                            onLand = vm::land,
                            onSpin = vm::spin,
                            coverWidth = coverWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(state: ShuffleState, vm: ShuffleViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterLine("Pool") {
            ShufflePool.entries.forEach { p -> ToggleChip(p.label, selected = state.pool == p, onClick = { vm.setPool(p) }) }
        }
        FilterLine("Mood") {
            ToggleChip("Any", selected = state.mood == null, onClick = { vm.setMood(null) })
            Mood.entries.forEach { m -> ToggleChip(m.title, selected = state.mood == m, onClick = { vm.setMood(if (state.mood == m) null else m) }) }
        }
        FilterLine("Time") {
            ToggleChip("Any", selected = state.length == null, onClick = { vm.setLength(null) })
            SessionLength.entries.forEach { l -> ToggleChip(l.label, selected = state.length == l, onClick = { vm.setLength(if (state.length == l) null else l) }) }
        }
        FilterLine("Platform") {
            ToggleChip("Any", selected = state.platform == null, onClick = { vm.setPlatform(null) })
            state.platformOptions.forEach { p -> ToggleChip(p.shortName, selected = state.platform == p, onClick = { vm.setPlatform(if (state.platform == p) null else p) }) }
        }
    }
}

@Composable
private fun FilterLine(label: String, content: @Composable () -> Unit) {
    val colors = Thumb.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(start = 16.dp).width(68.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** A stack of covers that cycles quickly and decelerates to a stop. */
@Composable
private fun CoverStack(
    candidates: List<Game>,
    spinning: Boolean,
    spinId: Int,
    onLand: (Game) -> Unit,
    onSpin: () -> Unit,
    coverWidth: Dp,
) {
    val colors = Thumb.colors
    val haptics = LocalHapticFeedback.current
    var index by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(spinId, spinning) {
        if (!spinning) return@LaunchedEffect
        scale.snapTo(0.96f)
        val total = (candidates.size * 2 + 10).coerceIn(14, 34)
        var tick = 0
        while (tick < total) {
            index = (index + 1) % candidates.size
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            // Fast at first, then decelerate.
            val t = tick.toFloat() / total
            delay((55 + 420 * t * t * t).toLong())
            tick++
        }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scale.animateTo(1.04f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        scale.animateTo(1f, tween(180))
        onLand(candidates[index % candidates.size])
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val coverHeight = coverWidth * (4f / 3f)
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.height(coverHeight + 36.dp)) {
            for (depth in 3 downTo 1) {
                val i = (index + depth) % candidates.size
                // Each card behind is a little smaller and peeks out below the one in front.
                val shrink = depth * 0.05f
                Box(Modifier.offset(y = coverHeight * shrink / 2 + (depth * 11).dp).scale(1f - shrink).alpha(0.7f - depth * 0.15f)) {
                    Cover(candidates[i].boxArtUrl, modifier = Modifier.width(coverWidth), radius = 14.dp)
                }
            }
            Box(Modifier.scale(scale.value)) {
                Cover(candidates[index % candidates.size].boxArtUrl, modifier = Modifier.width(coverWidth), radius = 14.dp)
            }
        }
        Text(
            if (spinning) "Shuffling…" else "${candidates.size} games in the pool",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        PrimaryButton(text = if (spinning) "Shuffling" else "Shuffle", icon = Icons.Outlined.Shuffle, onClick = onSpin, enabled = !spinning)
    }
}

@Composable
private fun WinnerCard(
    game: Game,
    availableHeight: Dp,
    onPlayNow: () -> Unit,
    onPlan: () -> Unit,
    onAgain: () -> Unit,
) {
    val colors = Thumb.colors
    val shape = RoundedCornerShape(20.dp)
    // Title, metadata and two button rows take ~250 dp; the cover gets the rest.
    val coverWidth = ((availableHeight - 270.dp) * 0.75f).coerceIn(72.dp, 200.dp)
    Box(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape),
    ) {
        Artwork(
            url = game.heroUrl ?: game.boxArtUrl,
            modifier = Modifier.matchParentSize().blur(28.dp).alpha(0.55f),
            shape = RectangleShape,
            placeholderIcon = null,
            bordered = false,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.bg.copy(alpha = 0.85f)))))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your pick", style = MaterialTheme.typography.labelMedium, color = colors.accent)
            Cover(game.boxArtUrl, modifier = Modifier.width(coverWidth), radius = 14.dp)
            Text(game.name, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (game.metadataLine.isNotEmpty()) {
                Text(game.metadataLine, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlatformMonogramRow(game.platforms, mine = emptySet(), size = 22.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(text = "Play now", icon = Icons.Outlined.PlayArrow, onClick = onPlayNow)
                SecondaryButton(text = "Plan a session", icon = Icons.Outlined.Event, onClick = onPlan)
            }
            SecondaryButton(text = "Shuffle again", icon = Icons.Outlined.Shuffle, onClick = onAgain)
        }
    }
}
