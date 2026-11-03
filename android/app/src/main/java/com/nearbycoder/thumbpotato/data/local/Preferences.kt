package com.nearbycoder.thumbpotato.data.local

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.util.Locale

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class Appearance(val raw: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromRaw(raw: String?): Appearance = entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

@Immutable
data class Settings(
    val myPlatformIds: Set<String> = emptySet(),
    val region: String = "US",
    val rawgKey: String = "",
    val remindMinutesBefore: Int = 15,
    val addSessionsToCalendar: Boolean = false,
    val sessionReminders: Boolean = true,
    val releaseAlerts: Boolean = true,
    val appearance: Appearance = Appearance.SYSTEM,
    val hasOnboarded: Boolean = false,
) {
    val effectiveRawgKey: String get() = rawgKey.trim()
    val hasRawg: Boolean get() = effectiveRawgKey.isNotEmpty()
    val myPlatforms: List<Platform> get() = Platform.entries.filter { it.id in myPlatformIds }
    val myPlatformSet: Set<Platform> get() = myPlatforms.toSet()

    /** Platforms the home feed is built around: yours, or a sensible default set. */
    val feedPlatforms: List<Platform> get() = myPlatforms.ifEmpty { Platform.defaults }

    fun isMine(platform: Platform): Boolean = platform.id in myPlatformIds
}

/** User settings backed by DataStore Preferences. */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val myPlatforms = stringSetPreferencesKey("myPlatforms")
        val region = stringPreferencesKey("region")
        val rawgKey = stringPreferencesKey("rawgKey")
        val remindMinutesBefore = intPreferencesKey("remindMinutesBefore")
        val addSessionsToCalendar = booleanPreferencesKey("addSessionsToCalendar")
        val sessionReminders = booleanPreferencesKey("sessionReminders")
        val releaseAlerts = booleanPreferencesKey("releaseAlerts")
        val appearance = stringPreferencesKey("appearance")
        val hasOnboarded = booleanPreferencesKey("hasOnboarded")
    }

    private val defaultRegion: String = Locale.getDefault().country
        .takeIf { code -> regions.any { it.code == code } } ?: "US"

    private fun Preferences.toSettings() = Settings(
        myPlatformIds = this[Keys.myPlatforms] ?: emptySet(),
        region = this[Keys.region]?.takeIf { code -> regions.any { it.code == code } } ?: defaultRegion,
        rawgKey = this[Keys.rawgKey] ?: "",
        remindMinutesBefore = this[Keys.remindMinutesBefore] ?: 15,
        addSessionsToCalendar = this[Keys.addSessionsToCalendar] ?: false,
        sessionReminders = this[Keys.sessionReminders] ?: true,
        releaseAlerts = this[Keys.releaseAlerts] ?: true,
        appearance = Appearance.fromRaw(this[Keys.appearance]),
        hasOnboarded = this[Keys.hasOnboarded] ?: false,
    )

    val settings: StateFlow<Settings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { store.data.first().toSettings() })

    val current: Settings get() = settings.value

    suspend fun togglePlatform(platform: Platform) {
        store.edit { prefs ->
            val set = (prefs[Keys.myPlatforms] ?: emptySet()).toMutableSet()
            if (!set.remove(platform.id)) set += platform.id
            prefs[Keys.myPlatforms] = set
        }
    }

    suspend fun setRegion(code: String) = store.edit { it[Keys.region] = code }
    suspend fun setRawgKey(key: String) = store.edit { it[Keys.rawgKey] = key.trim() }
    suspend fun setRemindMinutesBefore(value: Int) = store.edit { it[Keys.remindMinutesBefore] = value }
    suspend fun setAddSessionsToCalendar(value: Boolean) = store.edit { it[Keys.addSessionsToCalendar] = value }
    suspend fun setSessionReminders(value: Boolean) = store.edit { it[Keys.sessionReminders] = value }
    suspend fun setReleaseAlerts(value: Boolean) = store.edit { it[Keys.releaseAlerts] = value }
    suspend fun setAppearance(value: Appearance) = store.edit { it[Keys.appearance] = value.raw }
    suspend fun setHasOnboarded(value: Boolean) = store.edit { it[Keys.hasOnboarded] = value }

    data class Region(val code: String, val name: String)

    companion object {
        /** Steam store regions (prices and availability). CheapShark prices are always USD. */
        val regions: List<Region> = listOf(
            Region("US", "United States"), Region("CA", "Canada"), Region("GB", "United Kingdom"),
            Region("AU", "Australia"), Region("NZ", "New Zealand"), Region("IE", "Ireland"),
            Region("DE", "Germany"), Region("FR", "France"), Region("ES", "Spain"), Region("IT", "Italy"),
            Region("NL", "Netherlands"), Region("SE", "Sweden"), Region("BR", "Brazil"), Region("MX", "Mexico"),
            Region("IN", "India"), Region("JP", "Japan"), Region("KR", "South Korea"),
        )
    }
}
