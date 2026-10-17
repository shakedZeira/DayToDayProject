package com.daytoday.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/**
 * Fires the daily workout alarm: posts a full-screen alarm notification and starts
 * [AlarmRingingService], which rings and vibrates until the user snoozes or dismisses.
 */
class WorkoutAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)

        WorkoutAlarmNotifications.ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(WorkoutAlarmNotifications.NOTIFICATION_ID, WorkoutAlarmNotifications.build(context))
        }
        runCatching {
            context.startForegroundService(Intent(context, AlarmRingingService::class.java))
        }

        // Re-arm tomorrow's alarm as soon as the daily one fires (exact alarms are one-shot).
        if (!isSnooze && hour in 0..23 && minute in 0..59) {
            schedule(context, hour, minute)
        }
    }

    companion object {
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_SNOOZE = "snooze"

        private const val RC_DAILY = 4200
        private const val RC_SNOOZE = 4202
        const val SNOOZE_MINUTES = 10

        /** Schedules the next daily occurrence of [hour]:[minute], replacing any previous alarm. */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = pendingIntent(context, RC_DAILY, false, hour, minute)
            alarmManager.cancel(pendingIntent)

            val triggerAtMs = nextTriggerAt(hour, minute)
            val exact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            if (exact.isFailure) {
                // Exact alarms can be disallowed on Android 12+; fall back to an inexact daily alarm.
                runCatching {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            }
        }

        /** Re-arms the alarm [SNOOZE_MINUTES] from now, as a one-shot that does not affect the daily schedule. */
        fun snooze(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = pendingIntent(context, RC_SNOOZE, true, -1, -1)
            alarmManager.cancel(pendingIntent)

            val triggerAtMs = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            val exact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            if (exact.isFailure) {
                runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent) }
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context, RC_DAILY, false, -1, -1))
            alarmManager.cancel(pendingIntent(context, RC_SNOOZE, true, -1, -1))
        }

        private fun pendingIntent(
            context: Context,
            requestCode: Int,
            snooze: Boolean,
            hour: Int,
            minute: Int
        ): PendingIntent {
            val intent = Intent(context, WorkoutAlarmReceiver::class.java).apply {
                putExtra(EXTRA_HOUR, hour)
                putExtra(EXTRA_MINUTE, minute)
                putExtra(EXTRA_SNOOZE, snooze)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun nextTriggerAt(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val next = now.clone() as Calendar
            next.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            next.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            next.set(Calendar.SECOND, 0)
            next.set(Calendar.MILLISECOND, 0)
            if (next.timeInMillis <= now.timeInMillis) {
                next.add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis
        }
    }
}
