package com.nearbycoder.thumbpotato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.ui.theme.Thumb

/**
 * Edge-to-edge carousel: 16:9 on wide layouts, 4:5 on phones, gradient fade into bg,
 * bottom-aligned title, metadata line, platform monograms, primary + secondary actions and page dots.
 * Text never sits on box art: games without a backdrop get a blurred cover behind the sharp cover,
 * which gets its own space (a column on wide layouts, the top of the frame on phones).
 */
@Composable
fun HeroCarousel(
    games: List<Game>,
    isWide: Boolean,
    mine: Set<Platform>,
    isSaved: (Game) -> Boolean,
    onToggleSave: (Game) -> Unit,
    onDetails: (Game) -> Unit,
    modifier: Modifier = Modifier,
    topOverlay: @Composable () -> Unit = {},
) {
    val colors = Thumb.colors
    val pagerState = rememberPagerState(pageCount = { games.size })
    val ratio = if (isWide) 16f / 9f else 4f / 5f
    Box(modifier = modifier.fillMaxWidth().aspectRatio(ratio)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { games[it].id }) { page ->
            val game = games[page]
            HeroPage(
                game = game,
                isWide = isWide,
                mine = mine,
                saved = isSaved(game),
                onToggleSave = { onToggleSave(game) },
                onDetails = { onDetails(game) },
            )
        }
        // Top scrim so overlaid chrome stays readable.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(colors.bg.copy(alpha = 0.75f), Color.Transparent))),
        )
        topOverlay()
        if (games.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(games.size.coerceAtMost(10)) { index ->
                    val active = pagerState.currentPage % games.size.coerceAtMost(10) == index
                    Box(
                        Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.accent else colors.textTertiary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    game: Game,
    isWide: Boolean,
    mine: Set<Platform>,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onDetails: () -> Unit,
) {
    val colors = Thumb.colors
    // Steam hero art is ~3:1; cropped to a 4:5 phone frame it's a blurry sliver, so phones use the cover.
    val hasBackdrop = game.heroUrl != null && (isWide || game.boxArtUrl == null)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide, cover-only pages keep the text clear of the cover column on the right.
        val coverColumn = if (isWide && !hasBackdrop) (maxHeight - 56.dp) * 0.86f * 0.75f + 72.dp else 16.dp
        if (hasBackdrop) {
            Artwork(
                url = game.heroUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderIcon = null,
                bordered = false,
            )
        } else {
            Artwork(
                url = game.boxArtUrl,
                modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.9f),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderIcon = null,
                bordered = false,
            )
            Box(Modifier.fillMaxSize().background(colors.bg.copy(alpha = 0.35f)))
            if (isWide) {
                Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), contentAlignment = Alignment.CenterEnd) {
                    Cover(url = game.boxArtUrl, modifier = Modifier.fillMaxHeight(0.86f), radius = 14.dp, contentDescription = game.name)
                }
            } else {
                // Clear of the "Tonight" header above and the text block below.
                Box(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp, bottom = 236.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Cover(url = game.boxArtUrl, modifier = Modifier.fillMaxHeight(), radius = 14.dp, contentDescription = game.name)
                }
            }
        }
        // Fade into bg over the bottom 55%.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to colors.bg)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (isWide) 32.dp else 16.dp, end = coverColumn, bottom = 28.dp)
                .widthIn(max = if (isWide) 520.dp else 480.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(game.name, style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = game.metadataLine
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlatformMonogramRow(game.platforms, mine = mine, size = 22.dp)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = if (saved) "In Backlog" else "Add to Backlog",
                    onClick = onToggleSave,
                    icon = if (saved) Icons.Outlined.Check else Icons.Outlined.Add,
                )
                SecondaryButton(text = "Details", onClick = onDetails, onDark = colors.isDark)
            }
        }
    }
}
