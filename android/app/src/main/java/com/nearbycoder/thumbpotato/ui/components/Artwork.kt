package com.nearbycoder.thumbpotato.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nearbycoder.thumbpotato.ui.theme.Thumb

/** Box art framed to a fixed 3:4 with fill cropping, a hairline border and a quiet placeholder. */
@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    contentDescription: String? = null,
) {
    Artwork(
        url = url,
        modifier = modifier.aspectRatio(3f / 4f),
        shape = RoundedCornerShape(radius),
        contentDescription = contentDescription,
    )
}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector? = Icons.Outlined.SportsEsports,
    bordered: Boolean = true,
) {
    val colors = Thumb.colors
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceElevated)
            .then(if (bordered) Modifier.border(1.dp, colors.hairline, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (placeholderIcon != null) {
            Icon(placeholderIcon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(28.dp))
        }
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
