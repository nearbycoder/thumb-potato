package com.nearbycoder.thumbpotato.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.nearbycoder.thumbpotato.R
import com.nearbycoder.thumbpotato.data.local.PlaySession
import com.nearbycoder.thumbpotato.data.local.SavedGame
import com.nearbycoder.thumbpotato.domain.GameRef
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Local notifications via AlarmManager: play-session reminders and release-day alerts. */
object Reminders {
    const val SESSIONS_CHANNEL = "play_sessions"
    const val RELEASES_CHANNEL = "release_alerts"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_GAME_KEY = "gameKey"
    const val EXTRA_ID = "id"
    const val EXTRA_CHANNEL = "channel"

    /** Release-day alerts fire at 9 AM local on the release date. */
    val RELEASE_ALERT_TIME: LocalTime = LocalTime.of(9, 0)
    private const val MAX_RELEASE_ALERTS = 60
    private const val PREFS = "reminders"
    private const val SCHEDULED_RELEASES = "scheduledReleases"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SESSIONS_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(SESSIONS_CHANNEL, "Play sessions", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders before a planned play session"
                },
            )
        }
        if (manager.getNotificationChannel(RELEASES_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(RELEASES_CHANNEL, "Release alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Release-day alerts for games you follow"
                },
            )
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    }

    /** Intent that opens the system page for granting exact-alarm access (Android 12+). */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else null

    // Sessions

    fun schedule(context: Context, session: PlaySession, minutesBefore: Int, enabled: Boolean = true) {
        cancel(context, session)
        if (!enabled || !session.hasReminder || session.isDone) return
        val fireAt = session.startsAt - minutesBefore * 60_000L
        if (fireAt <= System.currentTimeMillis()) return
        val body = if (minutesBefore > 0) "${session.gameName} starts in $minutesBefore minutes." else "Time to play ${session.gameName}."
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TITLE, "Play session")
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_GAME_KEY, session.gameKey)
            putExtra(EXTRA_ID, session.id)
            putExtra(EXTRA_CHANNEL, SESSIONS_CHANNEL)
        }
        setAlarm(context, requestCode(session.id), fireAt, intent)
    }

    fun cancel(context: Context, session: PlaySession) = cancelAlarm(context, requestCode(session.id))

    // Release-day alerts

    /** When a release-day alert for [date] fires, in epoch millis. */
    fun releaseAlertMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(RELEASE_ALERT_TIME).atZone(zone).toInstant().toEpochMilli()

    /** Games that should have a release-day alert now: followed, exact date, alert time still ahead. */
    fun releaseAlertCandidates(games: List<SavedGame>, now: Long = System.currentTimeMillis()): List<SavedGame> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return games.filter { it.wantsReleaseAlert(today) && releaseAlertMillis(it.release!!) > now }
            .sortedBy { it.release }
            .take(MAX_RELEASE_ALERTS)
    }

    /**
     * Makes the scheduled release alerts match [games]: schedules the ones that should fire and
     * cancels any left over from games that were unfollowed, removed, re-dated or made coarse.
     */
    fun syncReleaseAlerts(context: Context, games: List<SavedGame>, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(SCHEDULED_RELEASES, emptySet()) ?: emptySet()
        val wanted = if (enabled) releaseAlertCandidates(games) else emptyList()
        val wantedKeys = wanted.map { it.key }.toSet()
        (previous - wantedKeys).forEach { cancelAlarm(context, releaseRequestCode(it)) }
        for (game in wanted) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_TITLE, "Out today")
                putExtra(EXTRA_BODY, "${game.name} is out today.")
                putExtra(EXTRA_GAME_KEY, game.key)
                putExtra(EXTRA_ID, "release-${game.key}")
                putExtra(EXTRA_CHANNEL, RELEASES_CHANNEL)
            }
            setAlarm(context, releaseRequestCode(game.key), releaseAlertMillis(game.release!!), intent, exact = false)
        }
        prefs.edit { putStringSet(SCHEDULED_RELEASES, wantedKeys) }
    }

    private fun requestCode(id: String): Int = id.hashCode()
    private fun releaseRequestCode(key: String): Int = "release-$key".hashCode()

    private fun setAlarm(context: Context, code: Int, fireAt: Long, intent: Intent, exact: Boolean = true) {
        val pending = PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (exact && canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            // Inexact alarms still land within a few minutes; fine for a 9 AM release alert.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    private fun cancelAlarm(context: Context, code: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
    }

    fun post(context: Context, channel: String, title: String, body: String, gameKey: String?, id: Int) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val link = gameKey?.let { GameRef.parse(it)?.deepLink } ?: "thumbpotato://planner"
        val open = Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4F8A00.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (channel == SESSIONS_CHANNEL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call; nothing to show.
        }
    }
}

/** Adds play sessions to the system calendar through the standard insert intent. */
object CalendarBridge {
    fun insertIntent(session: PlaySession): Intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, session.startsAt)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, session.endsAt)
            .putExtra(CalendarContract.Events.TITLE, "Play ${session.gameName}")
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                listOf(session.note, session.platform?.displayName?.let { "On $it" } ?: "", "Planned with Thumb Potato", session.ref?.deepLink ?: "")
                    .filter { it.isNotBlank() }.joinToString("\n\n"),
            )
}
