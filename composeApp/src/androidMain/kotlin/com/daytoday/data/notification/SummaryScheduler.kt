package com.daytoday.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import java.time.DayOfWeek
import java.time.Instant
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
 * The armed trigger time is persisted per kind. When [schedule] is called on a
 * process start and that time is still in the future, the existing alarm is kept
 * untouched (no cancel/re-create churn). If it is already in the past, the alarm
 * never fired — the process was killed or the device was off — so it is
 * rescheduled a couple of seconds from now to catch up instead of skipping the
 * whole day/week.
 *
 * The alert times are read from the "settings" SharedPreferences file — the same
 * mirror the workout reminder uses so [SummaryBootReceiver] can reschedule
 * without Hilt or DataStore. The DataStore copy in SettingsManager is the
 * in-app source of truth; SettingsViewModel mirrors each edit here after the
 * DataStore write.
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
    const val KEY_SUMMARY_DAILY_ARMED_AT = "summary_daily_armed_at"
    const val KEY_SUMMARY_WEEKLY_ARMED_AT = "summary_weekly_armed_at"

    private const val TAG = "SummaryScheduler"

    private const val RC_DAILY = 4310
    private const val RC_WEEKLY = 4311

    /** How soon an overdue (never delivered) alarm fires once detected. */
    private const val CATCH_UP_DELAY_MS = 2_000L

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

    /** Re-arms both kinds from scratch, discarding any persisted armed time. */
    fun rescheduleAll(context: Context) {
        reschedule(context, SummaryKind.DAILY)
        reschedule(context, SummaryKind.WEEKLY)
    }

    /** Cancels both alarms and forgets their armed times. */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, SummaryKind.DAILY))
        alarmManager.cancel(pendingIntent(context, SummaryKind.WEEKLY))
        prefs(context).edit()
            .remove(KEY_SUMMARY_DAILY_ARMED_AT)
            .remove(KEY_SUMMARY_WEEKLY_ARMED_AT)
            .apply()
    }

    /**
     * (Re-)arms the next occurrence of [kind] unless an alarm for it is already
     * armed for the future. An armed time in the past means the alarm was missed,
     * so a catch-up fire is scheduled shortly instead of waiting for tomorrow.
     */
    fun schedule(context: Context, kind: SummaryKind) {
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val armedKey = armedAtKeyFor(kind)
        val storedArmedAt = prefs.getLong(armedKey, 0L)
        if (storedArmedAt > now) {
            Log.d(TAG, "$kind summary already armed for $storedArmedAt; keeping it")
            return
        }

        val triggerAtMs = if (storedArmedAt > 0L) {
            Log.w(TAG, "$kind summary missed its $storedArmedAt trigger; firing catch-up")
            now + CATCH_UP_DELAY_MS
        } else {
            nextTriggerAt(kind, config(context), now)
        }
        arm(context, kind, triggerAtMs, prefs)
    }

    /** Cancels and re-creates the alarm for [kind], e.g. after a settings change. */
    fun reschedule(context: Context, kind: SummaryKind) {
        prefs(context).edit().remove(armedAtKeyFor(kind)).apply()
        schedule(context, kind)
    }

    /** Clears the persisted armed time once the alarm has actually fired. */
    fun markFired(context: Context, kind: SummaryKind) {
        prefs(context).edit().remove(armedAtKeyFor(kind)).apply()
    }

    /**
     * Stored alert times, read from the "settings" SharedPreferences mirror.
     * Missing or out-of-range values fall back to the legacy defaults.
     */
    fun config(context: Context): ScheduleConfig {
        val prefs = prefs(context)
        return scheduleConfig(
            dailyMinutes = prefs.getInt(
                KEY_SUMMARY_DAILY_MINUTES,
                DAILY_HOUR * 60 + DAILY_MINUTE,
            ),
            weeklyMinutes = prefs.getInt(
                KEY_SUMMARY_WEEKLY_MINUTES,
                WEEKLY_HOUR * 60 + WEEKLY_MINUTE,
            ),
            weeklyDay = prefs.getInt(KEY_SUMMARY_WEEKLY_DAY, WEEKLY_DAY.value),
        )
    }

    /** Coerces raw preference values into a valid [ScheduleConfig]. */
    internal fun scheduleConfig(
        dailyMinutes: Int,
        weeklyMinutes: Int,
        weeklyDay: Int,
    ): ScheduleConfig {
        val daily = dailyMinutes.coerceIn(0, 23 * 60 + 59)
        val weekly = weeklyMinutes.coerceIn(0, 23 * 60 + 59)
        return ScheduleConfig(
            dailyHour = daily / 60,
            dailyMinute = daily % 60,
            weeklyDay = DayOfWeek.of(weeklyDay.coerceIn(1, 7)),
            weeklyHour = weekly / 60,
            weeklyMinute = weekly % 60,
        )
    }

    /** Epoch millis of the next daily / weekly occurrence of [config] after [nowMillis]. */
    fun nextTriggerAt(
        kind: SummaryKind,
        config: ScheduleConfig = ScheduleConfig(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val zone = ZoneId.systemDefault()
        // Derive the date from nowMillis, not the wall clock, so the calculation
        // is deterministic when the caller supplies a fixed instant.
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
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

    private fun arm(
        context: Context,
        kind: SummaryKind,
        triggerAtMs: Long,
        prefs: SharedPreferences,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, kind)
        alarmManager.cancel(pendingIntent)

        var scheduled = runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent
            )
        }.onFailure { Log.w(TAG, "Exact $kind alarm rejected, falling back to inexact", it) }.isSuccess

        if (!scheduled) {
            // Exact alarms can be disallowed on Android 12+; fall back to an inexact one.
            scheduled = runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }.onFailure { Log.e(TAG, "Could not arm the $kind summary alarm", it) }.isSuccess
        }

        if (scheduled) {
            prefs.edit().putLong(armedAtKeyFor(kind), triggerAtMs).apply()
        } else {
            Log.e(TAG, "$kind summary alarm was not armed; will retry on the next schedule call")
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun armedAtKeyFor(kind: SummaryKind): String = when (kind) {
        SummaryKind.DAILY -> KEY_SUMMARY_DAILY_ARMED_AT
        SummaryKind.WEEKLY -> KEY_SUMMARY_WEEKLY_ARMED_AT
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
