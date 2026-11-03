package com.nearbycoder.thumbpotato.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.ui.theme.Thumb

/** Overlay for backlog covers: a status dot and, while playing, a progress bar. */
data class CoverBadge(val status: BacklogStatus, val progress: Int = 0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverCard(
    game: Game,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = game.subtitle,
    badge: CoverBadge? = null,
    radius: Dp = 10.dp,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Thumb.colors
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(radius))
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier.clickable(onClick = onClick),
            ),
    ) {
        Box {
            Cover(url = game.boxArtUrl, modifier = Modifier.fillMaxWidth(), radius = radius, contentDescription = game.name)
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.bg)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colors.statusColor(badge.status)),
                )
                if (badge.status == BacklogStatus.PLAYING && badge.progress > 0) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.bg.copy(alpha = 0.7f)),
                    ) {
                        Box(Modifier.fillMaxWidth(badge.progress.coerceIn(0, 100) / 100f).height(4.dp).clip(CircleShape).background(colors.accent))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            game.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 2.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            PlatformDots(game.platforms)
        }
    }
}

/** Horizontally scrolling covers under a section header. */
@Composable
fun CoverRow(
    title: String,
    games: List<Game>,
    coverWidth: Dp,
    onOpen: (Game) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    subtitleFor: (Game) -> String? = { it.subtitle },
    badgeFor: (Game) -> CoverBadge? = { null },
) {
    if (games.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title, subtitle = subtitle, actionLabel = actionLabel, onAction = onAction)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(games, key = { it.id }) { item ->
                CoverCard(game = item, width = coverWidth, onClick = { onOpen(item) }, subtitle = subtitleFor(item), badge = badgeFor(item))
            }
        }
    }
}

/** Skeleton row shown while a section loads. */
@Composable
fun CoverRowPlaceholder(title: String, coverWidth: Dp, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title)
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                Column(Modifier.width(coverWidth)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(coverWidth * (4f / 3f))
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(coverWidth * 0.7f).height(12.dp).clip(CircleShape).background(colors.surfaceElevated))
                }
            }
        }
    }
}

/** Adaptive cover grid with equal-width columns: 104 min on phones, 156 on larger windows. */
@Composable
fun CoverGrid(
    games: List<Game>,
    isWide: Boolean,
    onOpen: (Game) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    badgeFor: (Game) -> CoverBadge? = { null },
    subtitleFor: (Game) -> String? = { it.subtitle },
    onLongPress: ((Game) -> Unit)? = null,
    header: (LazyGridScope.() -> Unit)? = null,
    loadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
) {
    val minSize = if (isWide) 156.dp else 104.dp
    val gridState = rememberLazyGridState()
    if (onNearEnd != null) {
        val nearEnd by remember(gridState) {
            derivedStateOf {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
            }
        }
        LaunchedEffect(nearEnd, games.size) { if (nearEnd) onNearEnd() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        state = gridState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(if (isWide) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        header?.invoke(this)
        items(games, key = { it.id }) { item ->
            CoverCard(
                game = item,
                width = minSize,
                onClick = { onOpen(item) },
                modifier = Modifier.fillMaxWidth(),
                subtitle = subtitleFor(item),
                badge = badgeFor(item),
                onLongClick = onLongPress?.let { cb -> { cb(item) } },
            )
        }
        if (loadingMore) {
            item(key = "loading-more", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Thumb.colors.accent, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
