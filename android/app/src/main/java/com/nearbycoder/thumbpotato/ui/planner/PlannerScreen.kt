package com.nearbycoder.thumbpotato.ui.planner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.data.local.PlaySession
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.data.remote.DateParsing
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.ReleaseDates
import com.nearbycoder.thumbpotato.reminders.CalendarBridge
import com.nearbycoder.thumbpotato.ui.components.Cover
import com.nearbycoder.thumbpotato.ui.components.EmptyState
import com.nearbycoder.thumbpotato.ui.components.GroupCard
import com.nearbycoder.thumbpotato.ui.components.PlatformDots
import com.nearbycoder.thumbpotato.ui.components.SectionHeader
import com.nearbycoder.thumbpotato.ui.components.brandColor
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppEvents
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.screenInsets
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val events = LocalAppEvents.current
    val context = LocalContext.current
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = Thumb.colors
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(events.pendingPlanKey) {
        val key = events.pendingPlanKey ?: return@LaunchedEffect
        sheetKey = key
        showSheet = true
        events.pendingPlanKey = null
    }

    val daySessions = remember(state.sessions, state.selectedDay) {
        state.sessions.filter { DateParsing.localDate(it.startsAt) == state.selectedDay }.sortedBy { it.startsAt }
    }
    val daysWithPlans = remember(state.sessions) { state.sessions.map { DateParsing.localDate(it.startsAt) }.toSet() }
    val radar = state.radar
    val openSheet = { key: String? -> sheetKey = key; showSheet = true }

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text("Planner", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary) },
                actions = {
                    IconButton(onClick = { openSheet(null) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Plan a Session", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "week") {
                WeekStrip(
                    weekStart = state.weekStart,
                    selected = state.selectedDay,
                    hasPlans = { it in daysWithPlans },
                    onSelect = vm::selectDay,
                    onShift = vm::shiftWeek,
                )
            }
            item(key = "agenda-header") {
                SectionHeader(
                    title = state.selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    subtitle = when (daySessions.size) {
                        0 -> "Nothing planned."
                        1 -> "One play session."
                        else -> "${daySessions.size} play sessions."
                    },
                    actionLabel = "Plan a Session",
                    onAction = { openSheet(null) },
                )
            }
            if (daySessions.isEmpty()) {
                item(key = "agenda-empty") {
                    EmptyState(
                        icon = Icons.Outlined.Event,
                        headline = "A free evening",
                        footnote = "Plan a session from any game, or pick one from your backlog.",
                        actionLabel = "Plan a Session",
                        onAction = { openSheet(null) },
                    )
                }
            } else {
                items(daySessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onOpen = { actions.openGame(session.gameKey) },
                        onToggleDone = { vm.toggleDone(context, session) },
                        onDelete = { vm.delete(context, session) },
                        onCalendar = { runCatching { context.startActivity(CalendarBridge.insertIntent(session)) } },
                        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp),
                    )
                }
            }
            item(key = "radar-header") {
                SectionHeader(
                    title = "Release Radar",
                    subtitle = if (radar.isEmpty()) "Add an upcoming game to your backlog to follow its release." else "Followed games that aren't out yet.",
                )
            }
            items(radar, key = { "radar-${it.key}" }) { game ->
                RadarRow(game, onOpen = { actions.openGame(game.key) }, modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 760.dp))
            }
        }
    }

    if (showSheet) {
        PlanSessionSheet(
            saved = state.saved,
            initialKey = sheetKey,
            initialDay = state.selectedDay,
            onDismiss = { showSheet = false },
            onPlan = { session, addToCalendar ->
                if (session.hasReminder && Build.VERSION.SDK_INT >= 33) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.add(context, session)
                if (addToCalendar) runCatching { context.startActivity(CalendarBridge.insertIntent(session)) }
                vm.selectDay(DateParsing.localDate(session.startsAt))
                showSheet = false
            },
        )
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    hasPlans: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
    onShift: (Long) -> Unit,
) {
    val colors = Thumb.colors
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-7) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous week", tint = colors.textSecondary)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            (0L until 7L).map { weekStart.plusDays(it) }.forEach { day ->
                val isSelected = day == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(day) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")).take(3), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (isSelected) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                isSelected -> colors.onAccent
                                day == today -> colors.accent
                                else -> colors.textPrimary
                            },
                        )
                    }
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasPlans(day)) colors.accent else Color.Transparent))
                }
            }
        }
        IconButton(onClick = { onShift(7) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next week", tint = colors.textSecondary)
        }
    }
}

@Composable
private fun SessionCard(
    session: PlaySession,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thumb.colors
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val start = Instant.ofEpochMilli(session.startsAt).atZone(zone).toLocalTime().format(timeFmt)
    val end = Instant.ofEpochMilli(session.endsAt).atZone(zone).toLocalTime().format(timeFmt)
    GroupCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(session.coverUrl, modifier = Modifier.width(52.dp), radius = 8.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    session.gameName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (session.isDone) colors.textSecondary else colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("$start – $end", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    session.platform?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(p.brandColor()))
                            Text(p.shortName, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                    }
                    if (session.hasReminder && !session.isDone) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = "Reminder on", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = "Add to calendar",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp).clickable(onClick = onCalendar),
                    )
                    if (session.note.isNotBlank()) {
                        Text(session.note, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onToggleDone) {
                Icon(
                    if (session.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (session.isDone) "Done" else "Mark done",
                    tint = if (session.isDone) colors.success else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RadarRow(game: SavedGame, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Thumb.colors
    val date = game.release
    val exact = game.precision == DatePrecision.DAY
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(game.coverUrl, modifier = Modifier.width(44.dp), radius = 8.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(game.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlatformDots(game.platforms)
                Text(
                    (if (exact) ReleaseDates.label(date, game.precision) else "Date to be announced") ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Text(ReleaseDates.radarLabel(date, game.precision), style = MaterialTheme.typography.labelLarge, color = if (exact) colors.accent else colors.textSecondary)
    }
}
