package com.nearbycoder.thumbpotato

import android.content.Context
import com.nearbycoder.thumbpotato.data.local.AppDatabase
import com.nearbycoder.thumbpotato.data.local.SettingsRepository
import com.nearbycoder.thumbpotato.data.remote.Http
import com.nearbycoder.thumbpotato.domain.GameCatalog
import com.nearbycoder.thumbpotato.domain.Library
import com.nearbycoder.thumbpotato.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

/** Hand-rolled dependency container; one per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(appContext, scope)
    val database = AppDatabase.build(appContext)
    val library = Library(database, scope)
    val catalog: GameCatalog

    init {
        Http.init(appContext)
        catalog = GameCatalog(settings.settings, scope, File(appContext.filesDir, "catalog"))
        // Keep release-day alarms in step with the backlog: follow, unfollow, re-date, remove.
        scope.launch {
            combine(library.savedGames, settings.settings) { games, s -> games to s.releaseAlerts }
                .distinctUntilChanged()
                .collect { (games, enabled) -> Reminders.syncReleaseAlerts(appContext, games, enabled) }
        }
    }

    /** Re-arms every reminder (after boot, an update, or a settings change). */
    fun rescheduleAll(done: () -> Unit = {}) {
        scope.launch {
            try {
                val s = settings.current
                library.allSessions().forEach { Reminders.schedule(appContext, it, s.remindMinutesBefore, s.sessionReminders) }
                Reminders.syncReleaseAlerts(appContext, library.allGames(), s.releaseAlerts)
            } finally {
                done()
            }
        }
    }
}
