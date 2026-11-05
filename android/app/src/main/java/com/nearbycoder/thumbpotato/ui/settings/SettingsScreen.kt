package com.nearbycoder.thumbpotato.ui.settings

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nearbycoder.thumbpotato.data.local.Appearance
import com.nearbycoder.thumbpotato.data.local.SettingsRepository
import com.nearbycoder.thumbpotato.reminders.Reminders
import com.nearbycoder.thumbpotato.ui.components.ChipRow
import com.nearbycoder.thumbpotato.ui.components.GroupCard
import com.nearbycoder.thumbpotato.ui.components.Hairline
import com.nearbycoder.thumbpotato.ui.components.PlatformPicker
import com.nearbycoder.thumbpotato.ui.navigation.LocalAppContainer
import com.nearbycoder.thumbpotato.ui.navigation.NavActions
import com.nearbycoder.thumbpotato.ui.navigation.screenInsets
import com.nearbycoder.thumbpotato.ui.theme.Thumb
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(actions: NavActions) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val catalogUpdated by vm.catalogUpdated.collectAsStateWithLifecycle()
    val colors = Thumb.colors
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings = state.settings

    var keyDraft by remember { mutableStateOf(settings.rawgKey) }
    // Re-read permission state when coming back from system settings.
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val canNotify = remember(permissionTick) { Reminders.hasNotificationPermission(context) }
    val canExact = remember(permissionTick) { Reminders.canScheduleExact(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(context, it) }
    }

    Scaffold(
        containerColor = colors.bg,
        contentWindowInsets = screenInsets(),
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(data, containerColor = colors.surfaceElevated, contentColor = colors.textPrimary) } },
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = actions::back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "platforms") {
                Section("My Platforms", footer = "Tonight, Moods and Shuffle are built around the platforms you pick. Store rows for them come first.") {
                    PlatformPicker(selected = settings.myPlatformIds, onToggle = vm::togglePlatform, modifier = Modifier.padding(12.dp))
                    Hairline()
                    RegionRow(current = settings.region, onSelect = vm::setRegion)
                }
            }

            item(key = "data") {
                Section(
                    "Data Sources",
                    footer = "The catalog is rebuilt daily from Wikidata, Wikipedia and Steam. A free RAWG key is optional: with one, platform pages, filters and search use RAWG's full database.",
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (settings.hasRawg) colors.success else colors.accent))
                        Column {
                            Text(
                                if (settings.hasRawg) "RAWG connected" else "Free mode: Thumb Potato catalog, Wikipedia, Steam and CheapShark",
                                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                            )
                            Text(
                                catalogUpdated?.let { "Catalog updated $it" } ?: "Checking catalog…",
                                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        label = { Text("RAWG API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(),
                    )
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.testKey(keyDraft) },
                            enabled = keyDraft.isNotBlank() && keyStatus != KeyStatus.Checking,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        ) { Text("Save & test") }
                        if (settings.rawgKey.isNotEmpty()) {
                            TextButton(onClick = { keyDraft = ""; vm.removeKey() }) { Text("Remove", color = colors.danger) }
                        }
                        Spacer(Modifier.weight(1f))
                        when (val s = keyStatus) {
                            KeyStatus.Idle -> {}
                            KeyStatus.Checking -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(18.dp))
                            KeyStatus.Ok -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                                Text("Works", color = colors.success, style = MaterialTheme.typography.labelMedium)
                            }
                            is KeyStatus.Failed -> Text(s.message, color = colors.danger, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = 180.dp))
                        }
                    }
                    Hairline()
                    LinkRow("Get a free RAWG key") { uriHandler.openUri("https://rawg.io/apidocs") }
                }
            }

            item(key = "notifications") {
                Section("Notifications") {
                    if (!canNotify) {
                        LinkRow("Allow notifications", trailing = null) {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        Hairline()
                    }
                    ToggleRow("Release-day alerts", "9 AM on release day for games you follow (exact dates only).", settings.releaseAlerts, vm::setReleaseAlerts)
                    Hairline()
                    ToggleRow("Session reminders", "Before a planned play session.", settings.sessionReminders, vm::setSessionReminders)
                    Hairline()
                    ReminderRow(current = settings.remindMinutesBefore, onSelect = vm::setRemindMinutes)
                    Hairline()
                    ToggleRow("Add new sessions to Calendar", "Pre-fills the calendar switch when planning.", settings.addSessionsToCalendar, vm::setAddToCalendar)
                    if (!canExact) {
                        Hairline()
                        LinkRow("Allow exact reminder times", trailing = null) {
                            Reminders.exactAlarmSettingsIntent(context)?.let { runCatching { context.startActivity(it) } }
                        }
                    }
                }
            }

            item(key = "backup") {
                Section(
                    "Export / Import",
                    footer = "A JSON file with your backlog, ratings, notes, progress and sessions. Import it in Thumb Potato on another device, including the Apple app; newer changes win.",
                ) {
                    Text(
                        plural(state.gameCount, "game") + " · " + plural(state.sessionCount, "session"),
                        style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(16.dp),
                    )
                    Hairline()
                    LinkRow("Export to file", trailing = null) { exportLauncher.launch("thumb-potato-backup.json") }
                    Hairline()
                    LinkRow("Share backup", trailing = null) {
                        scope.launch {
                            runCatching {
                                val file = vm.exportToCache(context)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share backup"))
                            }
                        }
                    }
                    Hairline()
                    LinkRow("Import from file", trailing = null) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
            }

            item(key = "appearance") {
                Section("Appearance") {
                    ChipRow(
                        options = Appearance.entries.map { it.label },
                        selected = settings.appearance.ordinal,
                        onSelect = { vm.setAppearance(Appearance.entries[it]) },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item(key = "about") {
                Section("About") {
                    LinkRow("Thumb Potato on GitHub") { uriHandler.openUri("https://github.com/nearbycoder/thumb-potato") }
                    Hairline()
                    LinkRow("Website") { uriHandler.openUri("https://nearbycoder.github.io/thumb-potato/") }
                    Hairline()
                    Row(Modifier.padding(16.dp)) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("1.0", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                    Hairline()
                    Text(
                        "Box art and descriptions: Wikipedia (CC BY-SA for text). Game facts: Wikidata (CC0). Store data and art: Steam. " +
                            "PC prices: CheapShark. Optional catalog: RAWG.\n\n" +
                            "Thumb Potato isn't affiliated with Nintendo, Sony, Microsoft, Valve or any store; platform and store names appear as text and monograms, not logos.\n\n" +
                            "Spud, the potato thumbstick on the icon, says hello.",
                        style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
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

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable () -> Unit) {
    val colors = Thumb.colors
    Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
        GroupCard { Column { content() } }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Thumb.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun LinkRow(title: String, trailing: ImageVector? = Icons.AutoMirrored.Outlined.OpenInNew, onClick: () -> Unit) {
    val colors = Thumb.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(trailing ?: Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RegionRow(current: String, onSelect: (String) -> Unit) {
    val colors = Thumb.colors
    var open by remember { mutableStateOf(false) }
    val name = SettingsRepository.regions.firstOrNull { it.code == current }?.name ?: current
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Region", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Text("Steam prices and availability", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceElevated) {
            SettingsRepository.regions.forEach { region ->
                DropdownMenuItem(
                    text = { Text("${region.name} (${region.code})", color = if (region.code == current) colors.accent else colors.textPrimary) },
                    onClick = { onSelect(region.code); open = false },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(current: Int, onSelect: (Int) -> Unit) {
    val colors = Thumb.colors
    var open by remember { mutableStateOf(false) }
    val options = listOf(0 to "At start time", 5 to "5 minutes before", 15 to "15 minutes before", 30 to "30 minutes before", 60 to "1 hour before")
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Remind me", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(options.firstOrNull { it.first == current }?.second ?: "$current min before", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceElevated) {
            options.forEach { (minutes, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = if (minutes == current) colors.accent else colors.textPrimary) },
                    onClick = { onSelect(minutes); open = false },
                )
            }
        }
    }
}

private fun plural(count: Int, noun: String): String = "$count $noun" + if (count == 1) "" else "s"
