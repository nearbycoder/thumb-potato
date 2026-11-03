package com.nearbycoder.thumbpotato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Mood
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.Store
import com.nearbycoder.thumbpotato.ui.theme.Thumb

private fun hexColor(hex: String): Color = Color(("FF$hex").toLong(16))

fun Platform.brandColor(): Color = hexColor(colorHex)
fun Store.brandColor(): Color = hexColor(colorHex)

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** A rounded-square monogram on a brand color (platforms and stores; never logos). */
@Composable
fun Monogram(text: String, color: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, dimmed: Boolean = false) {
    val colors = Thumb.colors
    val shape = RoundedCornerShape(size * 0.25f)
    val onBrand = if (color.luminanceApprox() > 0.6f) Color(0xFF111114) else Color.White
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(shape)
            .background(color)
            .border(1.dp, colors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = if (text.length >= 3) (size.value * 0.30f).sp else (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            color = onBrand,
            maxLines = 1,
        )
    }
}

@Composable
fun PlatformMonogram(platform: Platform, modifier: Modifier = Modifier, size: Dp = 36.dp, dimmed: Boolean = false) =
    Monogram(platform.monogram, platform.brandColor(), modifier, size, dimmed)

@Composable
fun StoreMonogram(store: Store, modifier: Modifier = Modifier, size: Dp = 36.dp) =
    Monogram(store.monogram, store.brandColor(), modifier, size)

/** Row of small platform monograms; [mine] are full strength, others dimmed. */
@Composable
fun PlatformMonogramRow(platforms: List<Platform>, mine: Set<Platform>, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    if (platforms.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        platforms.forEach { p -> PlatformMonogram(p, size = size, dimmed = mine.isNotEmpty() && p !in mine) }
    }
}

/** 36 dp monogram with the name beneath in Caption (Browse). */
@Composable
fun PlatformTile(platform: Platform, onClick: () -> Unit, modifier: Modifier = Modifier, isMine: Boolean = false) {
    val colors = Thumb.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            PlatformMonogram(platform, size = 44.dp)
            if (isMine) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(colors.bg).padding(2.dp)
                        .clip(CircleShape).background(colors.accent),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            platform.shortName,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Selectable platform rows (Settings, onboarding). Columns are chosen so the longest name
 * ("Nintendo Switch 2") never truncates: it drops to one column on narrow windows.
 */
@Composable
fun PlatformPicker(selected: Set<String>, onToggle: (Platform) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / 230.dp).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Platform.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p -> PlatformChoice(p, p.id in selected, { onToggle(p) }, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PlatformChoice(platform: Platform, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = 0.6f) else colors.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformMonogram(platform, size = 36.dp)
        Text(platform.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .border(1.5.dp, if (selected) colors.accent else colors.textTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

val Mood.icon: ImageVector
    get() = when (this) {
        Mood.COZY -> Icons.Outlined.LocalCafe
        Mood.ADRENALINE -> Icons.Outlined.Bolt
        Mood.EPIC -> Icons.Outlined.Castle
        Mood.COUCH_COOP -> Icons.Outlined.Group
        Mood.SPOOKY -> Icons.Outlined.DarkMode
        Mood.BRAINY -> Icons.Outlined.Extension
    }

/** 16:10 duotone tile with an outlined symbol top-left and the title bottom-left. */
@Composable
fun MoodTile(mood: Mood, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = Thumb.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(16f / 10f)
            .heightIn(max = 130.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(mood.gradientFrom), Color(mood.gradientTo))))
            .border(1.dp, colors.hairline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(mood.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.align(Alignment.TopStart).size(24.dp))
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                mood.title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
            )
            if (!compact) {
                Text(mood.blurb, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MoodGrid(onOpen: (Mood) -> Unit, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Mood.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { mood -> MoodTile(mood, onClick = { onOpen(mood) }, modifier = Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Genre buttons in equal columns. */
@Composable
fun GenreGrid(onOpen: (Genre) -> Unit, modifier: Modifier = Modifier, columns: Int = 2) {
    val colors = Thumb.colors
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Genre.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { genre ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                            .clickable { onOpen(genre) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(genre.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
