package com.daytoday.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WorkoutBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REMINDERS_ENABLED, true)) {
            return
        }
        val minutes = prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
        WorkoutReminderReceiver.schedule(context, minutes / 60, minutes % 60)
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_REMINDERS_ENABLED = "workout_reminders"
        private const val KEY_REMINDER_MINUTES = "workout_reminder_minutes"
        private const val DEFAULT_REMINDER_MINUTES = 18 * 60
    }
}
