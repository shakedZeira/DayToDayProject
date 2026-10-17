package com.daytoday.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Calendar-aligned scheduling for the summary notifications, following the same
 * AlarmManager approach as the existing workout alarm.
 *
 * Daily   — every day at the configured time (default 21:00) local time.
 * Weekly  — every configured day (default Saturday) at the configured time
 *           (default 20:00) local time.
 *
 * Both are one-shot exact alarms ([AlarmManager.setExactAndAllowWhileIdle], with
 * an inexact fallback when exact alarms are disallowed on Android 12+). The
 * receiving [SummaryReceiver] re-arms the next occurrence as soon as one fires,
 * [SummaryBootReceiver] re-arms after a reboot or app update, and the app
 * re-arms on every process start — so the schedule survives reboots and
 * process death.
 *
 * The alert times are read from the "settings" SharedPreferences file — the same
 * mirror the workout reminder uses so [SummaryBootReceiver] can reschedule
 * without Hilt or DataStore. The DataStore copy in SettingsManager is the
 * in-app source of truth; SettingsViewModel mirrors each edit here.
 */
object SummaryScheduler {

    const val DAILY_HOUR = 21
    const val DAILY_MINUTE = 0

    val WEEKLY_DAY = DayOfWeek.SATURDAY
    const val WEEKLY_HOUR = 20
    const val WEEKLY_MINUTE = 0

    const val PREFS_NAME = "settings"
    const val KEY_SUMMARY_DAILY_MINUTES = "summary_daily_minutes"
    const val KEY_SUMMARY_WEEKLY_MINUTES = "summary_weekly_minutes"
    const val KEY_SUMMARY_WEEKLY_DAY = "summary_weekly_day"

    private const val RC_DAILY = 4310
    private const val RC_WEEKLY = 4311

    /** User-configurable alert times; defaults match the legacy fixed schedule. */
    data class ScheduleConfig(
        val dailyHour: Int = DAILY_HOUR,
        val dailyMinute: Int = DAILY_MINUTE,
        val weeklyDay: DayOfWeek = WEEKLY_DAY,
        val weeklyHour: Int = WEEKLY_HOUR,
        val weeklyMinute: Int = WEEKLY_MINUTE,
    )

    fun scheduleAll(context: Context) {
        schedule(context, SummaryKind.DAILY)
        schedule(context, SummaryKind.WEEKLY)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, SummaryKind.DAILY))
        alarmManager.cancel(pendingIntent(context, SummaryKind.WEEKLY))
    }

    /** (Re-)arms the next occurrence of [kind], replacing any previous alarm. */
    fun schedule(context: Context, kind: SummaryKind) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, kind)
        alarmManager.cancel(pendingIntent)

        val triggerAtMs = nextTriggerAt(kind, config(context))
        val exact = runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        }
        if (exact.isFailure) {
            // Exact alarms can be disallowed on Android 12+; fall back to an inexact one.
            runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }

    /**
     * Stored alert times, read from the "settings" SharedPreferences mirror.
     * Missing or out-of-range values fall back to the legacy defaults.
     */
    fun config(context: Context): ScheduleConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dailyMinutes = prefs
            .getInt(KEY_SUMMARY_DAILY_MINUTES, DAILY_HOUR * 60 + DAILY_MINUTE)
            .coerceIn(0, 23 * 60 + 59)
        val weeklyMinutes = prefs
            .getInt(KEY_SUMMARY_WEEKLY_MINUTES, WEEKLY_HOUR * 60 + WEEKLY_MINUTE)
            .coerceIn(0, 23 * 60 + 59)
        val weeklyDay = prefs.getInt(KEY_SUMMARY_WEEKLY_DAY, WEEKLY_DAY.value)
        return ScheduleConfig(
            dailyHour = dailyMinutes / 60,
            dailyMinute = dailyMinutes % 60,
            weeklyDay = DayOfWeek.of(weeklyDay.coerceIn(1, 7)),
            weeklyHour = weeklyMinutes / 60,
            weeklyMinute = weeklyMinutes % 60,
        )
    }

    /** Epoch millis of the next daily / weekly occurrence of [config] after [nowMillis]. */
    fun nextTriggerAt(
        kind: SummaryKind,
        config: ScheduleConfig = ScheduleConfig(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (kind) {
            SummaryKind.DAILY -> {
                val todayTrigger = today
                    .atTime(config.dailyHour, config.dailyMinute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (todayTrigger > nowMillis) {
                    todayTrigger
                } else {
                    today.plusDays(1)
                        .atTime(config.dailyHour, config.dailyMinute)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
            }
            SummaryKind.WEEKLY -> {
                val nextDay = today.with(TemporalAdjusters.nextOrSame(config.weeklyDay))
                val dayTrigger = nextDay
                    .atTime(config.weeklyHour, config.weeklyMinute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (dayTrigger > nowMillis) {
                    dayTrigger
                } else {
                    nextDay.plusWeeks(1)
                        .atTime(config.weeklyHour, config.weeklyMinute)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
            }
        }
    }

    private fun pendingIntent(context: Context, kind: SummaryKind): PendingIntent {
        val intent = Intent(context, SummaryReceiver::class.java).apply {
            action = SummaryReceiver.actionFor(kind)
            putExtra(SummaryReceiver.EXTRA_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            if (kind == SummaryKind.DAILY) RC_DAILY else RC_WEEKLY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
