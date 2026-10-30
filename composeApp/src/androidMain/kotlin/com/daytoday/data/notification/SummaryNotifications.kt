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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daytoday.R
import com.daytoday.settings.SettingsManager
import com.daytoday.ui.screen.MainActivity
import com.daytoday.usecase.LocalSummaryUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Which summary window a notification covers. */
enum class SummaryKind { DAILY, WEEKLY }

/**
 * Notification channel and posting helpers for the daily and weekly summary
 * notifications (alert times are configurable via [SummaryScheduler]; defaults
 * are 21:00 daily and Saturday 20:00). Content is composed from purely
 * on-device data by [SummaryContentBuilder].
 *
 * A skipped post always cancels the previous notification of that kind, so an
 * old "0 steps" summary can never survive into the next day. Every skip and
 * failure is logged so a missing notification is diagnosable from logcat.
 */
object SummaryNotifications {

    const val CHANNEL_ID = "daily_summaries"
    const val ID_DAILY = 4301
    const val ID_WEEKLY = 4302

    private const val TAG = "SummaryNotifications"
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
        val enabled = try {
            settingsManager.notificationsEnabled.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading notificationsEnabled failed; assuming enabled", e)
            true
        }
        if (!enabled) {
            Log.w(TAG, "$kind summary skipped: notifications disabled in-app; cancelling stale post")
            cancel(context, kind)
            return
        }
        post(context, kind, localSummaryUseCases, settingsManager)
    }

    /**
     * Builds and posts the summary notification. Skips (and cancels any stale
     * post) when the OS has not granted POST_NOTIFICATIONS, notifications are
     * disabled for the app, or the content cannot be built.
     */
    suspend fun post(
        context: Context,
        kind: SummaryKind,
        localSummaryUseCases: LocalSummaryUseCases,
        settingsManager: SettingsManager,
    ) {
        if (!canPost(context)) {
            Log.w(TAG, "$kind summary skipped: notifications disabled for the app; cancelling stale post")
            cancel(context, kind)
            return
        }

        val content = try {
            SummaryContentBuilder.build(
                kind = kind,
                localSummaryUseCases = localSummaryUseCases,
                settingsManager = settingsManager,
                cache = SummaryStatsCache(context),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Building $kind summary failed; cancelling stale post", e)
            cancel(context, kind)
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(idFor(kind), build(context, kind, content))
        } catch (e: SecurityException) {
            Log.w(TAG, "Posting $kind summary was rejected by the OS; cancelling stale post", e)
            cancel(context, kind)
        } catch (e: Exception) {
            Log.w(TAG, "Posting $kind summary failed", e)
            cancel(context, kind)
        }
    }

    /** Removes the notification for [kind], if one is showing. */
    fun cancel(context: Context, kind: SummaryKind) {
        try {
            NotificationManagerCompat.from(context).cancel(idFor(kind))
        } catch (e: Exception) {
            Log.w(TAG, "Cancelling $kind summary failed", e)
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
