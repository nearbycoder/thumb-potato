package com.nearbycoder.thumbpotato.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.BacklogStatus
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Deal
import com.nearbycoder.thumbpotato.domain.GameDetail
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.StoreOffer
import com.nearbycoder.thumbpotato.domain.hoursLabel
import com.nearbycoder.thumbpotato.ui.components.Artwork
import com.nearbycoder.thumbpotato.ui.components.Cover
import com.nearbycoder.thumbpotato.ui.components.CoverRow
import com.nearbycoder.thumbpotato.ui.components.CriticBadge
import com.nearbycoder.thumbpotato.ui.components.ErrorBanner
import com.nearbycoder.thumbpotato.ui.components.GroupCard
import com.nearbycoder.thumbpotato.ui.components.Hairline
import com.nearbycoder.thumbpotato.ui.components.Monogram
import com.nearbycoder.thumbpotato.ui.components.PlatformMonogramRow
import com.nearbycoder.thumbpotato.ui.components.PrimaryButton
import com.nearbycoder.thumbpotato.ui.components.SecondaryButton
import com.nearbycoder.thumbpotato.ui.components.SectionHeader
import com.nearbycoder.thumbpotato.ui.components.StarRating
import com.nearbycoder.thumbpotato.ui.components.StatusBadge
import com.nearbycoder.thumbpotato.ui.components.StatusBarScrim
import com.nearbycoder.thumbpotato.ui.components.SteamDeckBadge
import com.nearbycoder.thumbpotato.ui.components.StoreMonogram
import com.nearbycoder.thumbpotato.ui.components.ToggleChip
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppEvents
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.PlannerRoute
import com.nearbycoder.thumbpotato.ui.navigation.edgeToEdgeInsets
import com.nearbycoder.thumbpotato.ui.navigation.isWideLayout
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import java.util.Locale

@Composable
fun DetailScreen(gameKey: String, actions: NavActions) {
    val container = LocalAppContainer.current
    val events = LocalAppEvents.current
    val vm: DetailViewModel = viewModel(key = "detail-$gameKey") { DetailViewModel(container, gameKey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val colors = Thumb.colors
    val isWide = isWideLayout()
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 } }

    Scaffold(containerColor = colors.bg, contentWindowInsets = edgeToEdgeInsets()) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = state.detail
            when {
                detail != null -> DetailContent(
                    listState = listState,
                    detail = detail,
                    saved = saved,
                    mine = settings.myPlatformSet,
                    isWide = isWide,
                    vm = vm,
                    actions = actions,
                    onPlan = {
                        vm.ensureSaved {
                            events.pendingPlanKey = vm.currentKey
                            actions.selectTab(PlannerRoute)
                        }
                    },
                    onOpen = { url -> runCatching { uriHandler.openUri(url) } },
                )
                state.loading -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 56.dp)) {
                    ErrorBanner(state.error ?: "Couldn't load this game.", onRetry = vm::load)
                }
            }
            if (scrolled) StatusBarScrim()
            BackButton(onClick = actions::back, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
    }
}

@Composable
private fun DetailContent(
    listState: LazyListState,
    detail: GameDetail,
    saved: SavedGame?,
    mine: Set<Platform>,
    isWide: Boolean,
    vm: DetailViewModel,
    actions: NavActions,
    onPlan: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = Thumb.colors
    val game = detail.game
    val coverWidth = if (isWide) 150.dp else 120.dp
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "header") {
            Header(detail = detail, saved = saved, mine = mine, isWide = isWide, vm = vm, onPlan = onPlan, onOpen = onOpen)
        }

        if (game.summary.isNotBlank()) {
            item(key = "summary") { Summary(game.summary) }
        }

        if (saved != null) {
            item(key = "backlog") { BacklogEditor(saved = saved, vm = vm) }
        }

        if (detail.screenshots.isNotEmpty()) {
            item(key = "screens") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Screenshots")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(detail.screenshots.take(12), key = { it }) { url ->
                            Artwork(
                                url = url,
                                modifier = Modifier.width(if (isWide) 320.dp else 240.dp).aspectRatio(16f / 9f).clickable { onOpen(url) },
                                shape = RoundedCornerShape(10.dp),
                                contentDescription = "Screenshot",
                            )
                        }
                    }
                }
            }
        }

        if (detail.stores.isNotEmpty() || detail.pcDeals.isNotEmpty()) {
            item(key = "where") { WhereToPlay(detail = detail, onOpen = onOpen) }
        }

        item(key = "facts") { Facts(detail = detail, onOpen = onOpen) }

        if (detail.similar.isNotEmpty()) {
            item(key = "similar") {
                CoverRow(title = "More Like This", games = detail.similar, coverWidth = coverWidth, onOpen = { actions.openGame(it.ref.key) })
            }
        }

        item(key = "source") {
            val source = when (game.ref.source) {
                GameSource.WD -> "Box art and description: Wikipedia (CC BY-SA for text). Facts: Wikidata."
                GameSource.STEAM -> "Data from the Steam store."
                GameSource.RAWG -> "Data from RAWG."
            }
            Text(
                source,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp).clickable { onOpen(game.wikiTitle?.let { "https://en.wikipedia.org/wiki/$it" } ?: game.ref.webUrl) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    detail: GameDetail,
    saved: SavedGame?,
    mine: Set<Platform>,
    isWide: Boolean,
    vm: DetailViewModel,
    onPlan: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = Thumb.colors
    val game = detail.game
    val backdrop = detail.backdropUrl
    var statusMenu by remember { mutableStateOf(false) }
    val coverWidth = if (isWide) 160.dp else 112.dp
    val overlap = if (isWide) 72.dp else 64.dp
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (isWide) 21f / 9f else 16f / 10f)) {
            Artwork(
                url = backdrop ?: game.boxArtUrl,
                modifier = Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(36.dp).alpha(0.8f) else Modifier),
                shape = RectangleShape,
                placeholderIcon = null,
                bordered = false,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to colors.bg.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.45f to Color.Transparent, 1f to colors.bg),
                ),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp)
                .offset(y = overlap),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cover(game.boxArtUrl, modifier = Modifier.width(coverWidth), radius = 14.dp, contentDescription = game.name)
            Column(Modifier.weight(1f).padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(game.name, style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = buildList {
                    game.year?.let { add(it.toString()) }
                    if (game.genres.isNotEmpty()) add(game.genres.take(2).joinToString(", "))
                    game.developer?.let { add(it) }
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    game.metacritic?.let { CriticBadge(it) }
                    SteamDeckBadge(game.steamDeck)
                    if (saved != null) StatusBadge(saved.backlogStatus)
                }
            }
        }
    }
    Spacer(Modifier.height(overlap + 16.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PlatformMonogramRow(game.platforms, mine = mine, size = 28.dp)
        // One primary action, then a row of secondary ones.
        // Full width on phones; a natural-width capsule beside the secondaries on wide layouts.
        val primaryWidth = if (isWide) Modifier.widthIn(min = 220.dp) else Modifier.fillMaxWidth()
        val primary: @Composable (Modifier) -> Unit = { mod ->
            Box(mod) {
                if (saved == null) {
                    PrimaryButton(
                        text = if (game.isUpcoming()) "Add to Backlog & Follow" else "Add to Backlog",
                        icon = Icons.Outlined.Add,
                        onClick = { vm.save(BacklogStatus.QUEUED) },
                        modifier = primaryWidth,
                    )
                } else {
                    PrimaryButton(text = saved.backlogStatus.label, icon = Icons.Outlined.KeyboardArrowDown, onClick = { statusMenu = true }, modifier = primaryWidth)
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }, containerColor = colors.surfaceElevated) {
                    BacklogStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.label, color = if (status == saved?.backlogStatus) colors.accent else colors.textPrimary) },
                            onClick = { vm.save(status); statusMenu = false },
                        )
                    }
                    DropdownMenuItem(text = { Text("Remove from Backlog", color = colors.danger) }, onClick = { vm.remove(); statusMenu = false })
                }
            }
        }
        val secondaries: @Composable (Modifier) -> Unit = { mod ->
            SecondaryButton(text = "Plan a Session", icon = Icons.Outlined.Event, onClick = onPlan, modifier = mod)
            detail.trailerUrl?.let { url -> SecondaryButton(text = "Trailer", icon = Icons.Outlined.PlayArrow, onClick = { onOpen(url) }, modifier = mod) }
        }
        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                primary(Modifier)
                secondaries(Modifier)
            }
        } else {
            primary(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { secondaries(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Summary(text: String) {
    val colors = Thumb.colors
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp).animateContentSize()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "Less" else "More",
                color = colors.accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WhereToPlay(detail: GameDetail, onOpen: (String) -> Unit) {
    val colors = Thumb.colors
    val yours = detail.stores.filter { it.isMine }
    val others = detail.stores.filter { !it.isMine }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Where to Play")
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (yours.isNotEmpty()) StoreGroup("Your platforms", yours, onOpen)
            if (others.isNotEmpty()) StoreGroup(if (yours.isEmpty()) "Stores" else "Other stores", others, onOpen)
            if (detail.pcDeals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PC prices", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                    GroupCard {
                        detail.pcDeals.forEachIndexed { i, deal ->
                            DealRow(deal, onClick = { onOpen(deal.url) })
                            if (i < detail.pcDeals.lastIndex) Hairline(Modifier.padding(start = 64.dp))
                        }
                    }
                    Text("Prices in USD from CheapShark.", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun StoreGroup(title: String, offers: List<StoreOffer>, onOpen: (String) -> Unit) {
    val colors = Thumb.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        GroupCard {
            offers.forEachIndexed { i, offer ->
                StoreRow(
                    monogram = { StoreMonogram(offer.store, size = 36.dp) },
                    name = offer.store.displayName,
                    caption = offer.store.platforms.joinToString(", ") { it.shortName },
                    price = offer.price,
                    regular = offer.regularPrice,
                    onClick = { onOpen(offer.url) },
                )
                if (i < offers.lastIndex) Hairline(Modifier.padding(start = 64.dp))
            }
        }
    }
}

@Composable
private fun DealRow(deal: Deal, onClick: () -> Unit) {
    StoreRow(
        monogram = { Monogram(deal.storeName.filter { it.isLetterOrDigit() }.take(2).uppercase(), Thumb.colors.surfaceElevated, size = 36.dp) },
        name = deal.storeName,
        caption = if (deal.isDiscounted) "-${deal.savings.toInt()}%" else null,
        price = deal.priceLabel,
        regular = if (deal.isDiscounted) deal.retailLabel else null,
        onClick = onClick,
    )
}

@Composable
private fun StoreRow(
    monogram: @Composable () -> Unit,
    name: String,
    caption: String?,
    price: String?,
    regular: String?,
    onClick: () -> Unit,
) {
    val colors = Thumb.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        monogram()
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!caption.isNullOrEmpty()) Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1)
        }
        if (regular != null) {
            Text(regular, style = MaterialTheme.typography.bodySmall.merge(TextStyle(textDecoration = TextDecoration.LineThrough)), color = colors.textTertiary)
        }
        if (price != null) {
            Text(price, style = MaterialTheme.typography.titleSmall, color = if (regular != null) colors.accent else colors.textPrimary)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Facts(detail: GameDetail, onOpen: (String) -> Unit) {
    val colors = Thumb.colors
    val game = detail.game
    val rows = buildList {
        game.releaseLabel?.let { add("Release date" to it) }
        game.developer?.let { add("Developer" to it) }
        game.publisher?.let { add("Publisher" to it) }
        detail.steamReviews?.let { add("Steam reviews" to it) }
        game.metacritic?.let { add("Metacritic" to it.toString()) }
    }
    if (rows.isEmpty() && detail.website == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Details")
        GroupCard(Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp)) {
            rows.forEachIndexed { i, (label, value) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.width(120.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                }
                if (i < rows.lastIndex || detail.website != null) Hairline(Modifier.padding(start = 16.dp))
            }
            detail.website?.let { url ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(url) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Official website", style = MaterialTheme.typography.bodyMedium, color = colors.accent, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BacklogEditor(saved: SavedGame, vm: DetailViewModel) {
    val colors = Thumb.colors
    var notes by remember(saved.key) { mutableStateOf(saved.notes) }
    var hoursText by remember(saved.key) { mutableStateOf(if (saved.hoursPlayed > 0) hoursText(saved.hoursPlayed) else "") }
    var progress by remember(saved.key) { mutableFloatStateOf(saved.progress.toFloat()) }
    LaunchedEffect(notes) {
        if (notes != saved.notes) {
            kotlinx.coroutines.delay(600)
            vm.setNotes(notes)
        }
    }
    LaunchedEffect(hoursText) {
        val value = hoursText.replace(',', '.').toDoubleOrNull() ?: if (hoursText.isBlank()) 0.0 else return@LaunchedEffect
        if (value != saved.hoursPlayed) {
            kotlinx.coroutines.delay(600)
            vm.setHours(value)
        }
    }
    LaunchedEffect(saved.progress) { progress = saved.progress.toFloat() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Your Backlog")
        GroupCard(modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BacklogStatus.entries.forEach { status ->
                        ToggleChip(status.label, selected = status == saved.backlogStatus, onClick = { vm.save(status) })
                    }
                }
                if (saved.platforms.isNotEmpty()) {
                    Label("Playing on")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        saved.platforms.forEach { p ->
                            val selected = saved.playingOn == p.id
                            ToggleChip(p.shortName, selected = selected, onClick = { vm.setPlayingOn(if (selected) null else p) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { v -> hoursText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Hours played") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                    )
                    SecondaryButton(text = "+1h", onClick = {
                        val next = (hoursText.replace(',', '.').toDoubleOrNull() ?: 0.0) + 1.0
                        hoursText = hoursText(next)
                    })
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Progress", Modifier.weight(1f))
                        Text("${progress.toInt()}%", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    }
                    Slider(
                        value = progress,
                        onValueChange = { progress = (it / 5f).toInt() * 5f },
                        onValueChangeFinished = { vm.setProgress(progress.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.secondaryFill),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Rating", Modifier.weight(1f))
                    StarRating(rating = saved.rating, size = 24.dp, onChange = vm::setRating)
                }
                SwitchRow("I own it", null, saved.owned, vm::setOwned)
                val release = saved.release
                val followNote = when {
                    release == null -> "Shows up in Release Radar once a date is known."
                    !saved.isUpcoming() -> "Already out."
                    saved.precision != DatePrecision.DAY -> "Release day not announced yet; alerts start once it is."
                    else -> "A notification at 9 AM on release day."
                }
                SwitchRow("Follow release", followNote, saved.isFollowing, vm::setFollowing)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Notes", color = colors.textTertiary) },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
                if (saved.hoursPlayed > 0) {
                    Text("Logged ${hoursLabel(saved.hoursPlayed)}", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            }
        }
    }
}

private fun hoursText(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format(Locale.US, "%.1f", hours)

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Thumb.colors.textSecondary, modifier = modifier)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Thumb.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Thumb.colors.accent,
    unfocusedBorderColor = Thumb.colors.hairline,
    cursorColor = Thumb.colors.accent,
    focusedTextColor = Thumb.colors.textPrimary,
    unfocusedTextColor = Thumb.colors.textPrimary,
    focusedLabelColor = Thumb.colors.accent,
    unfocusedLabelColor = Thumb.colors.textSecondary,
)
