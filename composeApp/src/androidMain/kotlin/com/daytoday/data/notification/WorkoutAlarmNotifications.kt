package com.daytoday.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.daytoday.R

object WorkoutAlarmNotifications {

    const val CHANNEL_ID = "workout_alarms"
    const val NOTIFICATION_ID = 4201

    private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings the daily workout alarm"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Workout alarm")
            .setContentText("Time to work out!")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(alarmIntent(context), true)
            .setContentIntent(alarmIntent(context))
            .addAction(0, "Snooze", snoozeIntent(context))
            .addAction(0, "Dismiss", dismissIntent(context))
            .build()
    }

    fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            10,
            Intent(context, WorkoutAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PI_FLAGS
        )

    private fun alarmIntent(context: Context): PendingIntent = alarmPendingIntent(context)

    private fun snoozeIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            11,
            Intent(context, AlarmRingingService::class.java).setAction(AlarmRingingService.ACTION_SNOOZE),
            PI_FLAGS
        )

    private fun dismissIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            12,
            Intent(context, AlarmRingingService::class.java).setAction(AlarmRingingService.ACTION_STOP),
            PI_FLAGS
        )
}
