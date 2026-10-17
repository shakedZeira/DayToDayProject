package com.daytoday.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daytoday.R
import com.daytoday.settings.SettingsManager
import com.daytoday.ui.screen.MainActivity
import com.daytoday.usecase.LocalSummaryUseCases
import kotlinx.coroutines.flow.first

/** Which summary window a notification covers. */
enum class SummaryKind { DAILY, WEEKLY }

/**
 * Notification channel and posting helpers for the daily and weekly summary
 * notifications (alert times are configurable via [SummaryScheduler]; defaults
 * are 21:00 daily and Saturday 20:00). Content is composed from purely
 * on-device data by [SummaryContentBuilder].
 */
object SummaryNotifications {

    const val CHANNEL_ID = "daily_summaries"
    const val ID_DAILY = 4301
    const val ID_WEEKLY = 4302

    private const val PI_FLAG = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily summaries",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily and weekly progress summaries (goals, calories, workouts)"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts the summary, honoring the in-app "notifications enabled" toggle. */
    suspend fun postIfNeeded(
        context: Context,
        kind: SummaryKind,
        localSummaryUseCases: LocalSummaryUseCases,
        settingsManager: SettingsManager,
    ) {
        val enabled = runCatching { settingsManager.notificationsEnabled.first() }
            .getOrDefault(true)
        if (!enabled) return
        post(context, kind, localSummaryUseCases, settingsManager)
    }

    /**
     * Builds and posts the summary notification. Silently skips when the OS has
     * not granted POST_NOTIFICATIONS or notifications are disabled for the app.
     */
    suspend fun post(
        context: Context,
        kind: SummaryKind,
        localSummaryUseCases: LocalSummaryUseCases,
        settingsManager: SettingsManager,
    ) {
        if (!canPost(context)) return
        val content = runCatching {
            SummaryContentBuilder.build(kind, localSummaryUseCases, settingsManager)
        }.getOrNull() ?: return
        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(kind), build(context, kind, content))
        }
    }

    /** True when the OS allows this app to post notifications right now. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    fun idFor(kind: SummaryKind): Int = when (kind) {
        SummaryKind.DAILY -> ID_DAILY
        SummaryKind.WEEKLY -> ID_WEEKLY
    }

    private fun build(context: Context, kind: SummaryKind, content: SummaryContent): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, idFor(kind), launchIntent, PI_FLAG)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_summary)
            .setContentTitle(content.title)
            .setContentText(content.singleLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
