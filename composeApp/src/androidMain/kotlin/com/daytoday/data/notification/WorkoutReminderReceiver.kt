package com.daytoday.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daytoday.R
import java.util.Calendar

class WorkoutReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Workout reminder")
            .setContentText("Time for your workout — your goals are waiting")
            .setAutoCancel(true)
            .build()

        // notify() is a silent no-op when POST_NOTIFICATIONS was denied.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "workout_reminders"
        private const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE = 4200

        /** Schedules the next daily occurrence of [hour]:[minute], replacing any previous alarm. */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = reminderPendingIntent(context)
            alarmManager.cancel(pendingIntent)

            val triggerAtMs = nextTriggerAt(hour, minute)
            val exact = runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
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

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(reminderPendingIntent(context))
        }

        private fun reminderPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WorkoutReminderReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
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
