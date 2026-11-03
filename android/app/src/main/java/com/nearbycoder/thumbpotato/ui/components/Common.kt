package com.nearbycoder.thumbpotato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.ui.theme.Thumb

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Thumb.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Thumb.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = Thumb.colors.accent)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Thumb.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onDark: Boolean = false,
) {
    val colors = Thumb.colors
    val container = if (onDark) Color(0x33FFFFFF) else colors.secondaryFill
    val content = if (onDark) Color.White else colors.textPrimary
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun StatusBadge(status: BacklogStatus, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    val tint = colors.statusColor(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.secondaryFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
        Text(status.label, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
    }
}

/** Five stars in accent. Interactive at 24 dp, display at 14 dp. */
@Composable
fun StarRating(
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    onChange: ((Int) -> Unit)? = null,
) {
    val colors = Thumb.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (onChange != null) 4.dp else 1.dp)) {
        for (i in 1..5) {
            val filled = i <= rating
            val icon = if (filled) Icons.Outlined.Star else Icons.Outlined.StarBorder
            if (onChange != null) {
                IconButton(onClick = { onChange(if (rating == i) 0 else i) }, modifier = Modifier.size(size + 12.dp)) {
                    Icon(icon, contentDescription = "$i stars", tint = colors.accent, modifier = Modifier.size(size))
                }
            } else {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(size))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    headline: String,
    footnote: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Thumb.colors
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(4.dp))
            Text(headline, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(footnote, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = Thumb.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.danger)
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Retry", color = colors.accent) }
        }
    }
}

/** A grouped settings-style card. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Thumb.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Thumb.colors.hairline))
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thumb.colors
    Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accent else colors.secondaryFill)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onAccent else colors.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Critic score as a small rounded square, colored by band. */
@Composable
fun CriticBadge(score: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = Thumb.colors
    val tint = colors.scoreColor(score)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 8.dp else 6.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(if (large) 8.dp else 6.dp))
            .padding(horizontal = if (large) 8.dp else 6.dp, vertical = if (large) 4.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            score.toString(),
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

/** "Deck Verified" / "Deck Playable" pill; nothing for unknown or unsupported. */
@Composable
fun SteamDeckBadge(category: Int?, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    val label = when (category) {
        3 -> "Deck Verified"
        2 -> "Deck Playable"
        else -> return
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.secondaryFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (category == 3) colors.accent else colors.success))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
    }
}

/** Up to [max] small platform dots in brand colors. */
@Composable
fun PlatformDots(platforms: List<Platform>, modifier: Modifier = Modifier, max: Int = 3) {
    if (platforms.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        platforms.distinctBy { it.colorHex }.take(max).forEach { p ->
            Box(Modifier.size(6.dp).clip(CircleShape).background(p.brandColor()))
        }
    }
}

/** A horizontal toggle chip used for filters and pickers. */
@Composable
fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.secondaryFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.onAccent else colors.textPrimary, maxLines = 1)
    }
}

/** Keeps the status bar readable over edge-to-edge scrolling content. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .background(Thumb.colors.bg.copy(alpha = 0.96f)),
    )
}
