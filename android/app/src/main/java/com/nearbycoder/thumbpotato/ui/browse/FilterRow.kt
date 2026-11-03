package com.nearbycoder.thumbpotato.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nearbycoder.thumbpotato.domain.CriticScore
import com.nearbycoder.thumbpotato.domain.GameFilter
import com.nearbycoder.thumbpotato.domain.GameSort
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Released
import com.nearbycoder.thumbpotato.ui.theme.Thumb

/** Sort, released, genre and critic-score capsule menus that wrap on narrow screens, plus Reset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterRow(
    filter: GameFilter,
    onChange: (GameFilter) -> Unit,
    showGenre: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Thumb.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChipMenu(
            label = "Sort: ${filter.sort.label}",
            active = filter.sort != GameSort.POPULAR,
            options = GameSort.entries,
            selected = filter.sort,
            optionLabel = { it.label },
            onSelect = { onChange(filter.copy(sort = it)) },
        )
        FilterChipMenu(
            label = "Released: ${filter.released.label}",
            active = filter.released != Released.ANY,
            options = Released.entries,
            selected = filter.released,
            optionLabel = { it.label },
            onSelect = { onChange(filter.copy(released = it)) },
        )
        if (showGenre) {
            FilterChipMenu(
                label = "Genre: ${filter.genre?.label ?: "Any"}",
                active = filter.genre != null,
                options = listOf<Genre?>(null) + Genre.entries,
                selected = filter.genre,
                optionLabel = { it?.label ?: "Any Genre" },
                onSelect = { onChange(filter.copy(genre = it)) },
            )
        }
        FilterChipMenu(
            label = "Critic score: ${filter.score.label}",
            active = filter.score != CriticScore.ANY,
            options = CriticScore.entries,
            selected = filter.score,
            optionLabel = { it.label },
            onSelect = { onChange(filter.copy(score = it)) },
        )
        if (!filter.isDefault) {
            TextButton(
                onClick = { onChange(GameFilter()) },
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }
        }
    }
}

@Composable
private fun <T> FilterChipMenu(
    label: String,
    active: Boolean,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = Thumb.colors
    var open by remember { mutableStateOf(false) }
    val content = if (active) colors.accent else colors.textPrimary
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.secondaryFill)
                .then(if (active) Modifier.border(1.dp, colors.accent.copy(alpha = 0.45f), CircleShape) else Modifier)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.surfaceElevated,
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel(option),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) colors.accent else colors.textPrimary,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        open = false
                        if (!isSelected) onSelect(option)
                    },
                )
            }
        }
    }
}
