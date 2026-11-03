package com.nearbycoder.thumbpotato.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbycoder.thumbpotato.AppContainer
import com.nearbycoder.thumbpotato.data.local.Appearance
import com.nearbycoder.thumbpotato.data.local.Settings
import com.nearbycoder.thumbpotato.data.remote.RawgClient
import com.nearbycoder.thumbpotato.domain.Backup
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed interface KeyStatus {
    data object Idle : KeyStatus
    data object Checking : KeyStatus
    data object Ok : KeyStatus
    data class Failed(val message: String) : KeyStatus
}

@Immutable
data class SettingsState(
    val settings: Settings = Settings(),
    val gameCount: Int = 0,
    val sessionCount: Int = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settings

    val state: StateFlow<SettingsState> = combine(repo.settings, container.library.savedGames, container.library.playSessions) { s, games, sessions ->
        SettingsState(settings = s, gameCount = games.size, sessionCount = sessions.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState(settings = repo.current))

    private val _keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.Idle)
    val keyStatus: StateFlow<KeyStatus> = _keyStatus

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** "Nov 3, 2026" from the catalog's index.json, or null while unknown. */
    private val _catalogUpdated = MutableStateFlow<String?>(null)
    val catalogUpdated: StateFlow<String?> = _catalogUpdated

    init {
        viewModelScope.launch {
            container.catalog.index()?.updated?.let { stamp ->
                _catalogUpdated.value = runCatching {
                    Instant.parse(stamp).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                }.getOrNull()
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun togglePlatform(p: Platform) = viewModelScope.launch { repo.togglePlatform(p); container.catalog.reset() }
    fun setRegion(code: String) = viewModelScope.launch { repo.setRegion(code); container.catalog.reset() }
    fun setAppearance(v: Appearance) = viewModelScope.launch { repo.setAppearance(v) }
    fun setAddToCalendar(v: Boolean) = viewModelScope.launch { repo.setAddSessionsToCalendar(v) }
    fun setReleaseAlerts(v: Boolean) = viewModelScope.launch { repo.setReleaseAlerts(v) }

    fun setSessionReminders(v: Boolean) = viewModelScope.launch {
        repo.setSessionReminders(v)
        container.rescheduleAll()
    }

    fun setRemindMinutes(v: Int) = viewModelScope.launch {
        repo.setRemindMinutesBefore(v)
        container.rescheduleAll()
    }

    fun testKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _keyStatus.value = KeyStatus.Checking
            runCatching { RawgClient(trimmed).validate() }
                .onSuccess {
                    repo.setRawgKey(trimmed)
                    container.catalog.reset()
                    _keyStatus.value = KeyStatus.Ok
                }
                .onFailure { t -> _keyStatus.value = KeyStatus.Failed(t.message ?: "That key didn't work.") }
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            repo.setRawgKey("")
            container.catalog.reset()
            _keyStatus.value = KeyStatus.Idle
        }
    }

    private suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        Backup.export(container.library.allGames(), container.library.allSessions())
    }

    fun exportTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Couldn't open file")
                }
            }.onSuccess { _message.value = "Exported ${state.value.gameCount} games and ${state.value.sessionCount} sessions." }
                .onFailure { _message.value = "Export failed: ${it.message}" }
        }
    }

    /** Writes the backup into the cache dir for sharing; returns the file. */
    suspend fun exportToCache(context: Context): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "thumb-potato-backup.json")
        file.writeText(exportJson())
        file
    }

    fun importFrom(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Couldn't read file")
                }
                val imported = Backup.import(text)
                container.library.importGames(imported.games) to container.library.importSessions(imported.sessions)
            }.onSuccess { (games, sessions) ->
                container.rescheduleAll()
                _message.value = "Imported $games new or updated games and $sessions sessions."
            }.onFailure { _message.value = "Import failed: ${it.message}" }
        }
    }
}
