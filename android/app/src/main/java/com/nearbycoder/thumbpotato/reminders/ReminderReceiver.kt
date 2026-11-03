package com.nearbycoder.thumbpotato.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE) ?: "Play session"
        val body = intent.getStringExtra(Reminders.EXTRA_BODY) ?: "Your play session is starting."
        val gameKey = intent.getStringExtra(Reminders.EXTRA_GAME_KEY)
        val channel = intent.getStringExtra(Reminders.EXTRA_CHANNEL) ?: Reminders.SESSIONS_CHANNEL
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: body
        Reminders.post(context, channel, title, body, gameKey, id.hashCode())
    }
}

/** Alarms don't survive a reboot or an app update; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as com.nearbycoder.thumbpotato.ThumbPotatoApp
        app.container.rescheduleAll { pending.finish() }
    }
}
