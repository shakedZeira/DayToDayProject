package com.daytoday.data.notification

import android.content.Context
import android.content.SharedPreferences
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalWeekStats
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A cached day snapshot together with the local date it describes. */
data class CachedDayStats(
    val date: LocalDate,
    val capturedAtMs: Long,
    val stats: LocalDayStats,
)

/** A cached week snapshot together with the Monday it starts on. */
data class CachedWeekStats(
    val weekStart: LocalDate,
    val capturedAtMs: Long,
    val stats: LocalWeekStats,
)

/**
 * Last-known-good summary statistics, persisted in SharedPreferences.
 *
 * A cold, alarm-started process sometimes cannot finish a Health Connect read
 * inside the notification budget, and the repository then reports zero steps.
 * This cache lets the builder keep showing the last real value for the same day
 * (or week) instead of a misleading `0`, annotated with the capture time.
 *
 * Only successful, non-zero reads are stored, so a transient failure cannot
 * overwrite a good snapshot. Snapshots are scoped by local date (day) and by
 * Monday-based week start (week), so yesterday's steps are never shown as
 * today's.
 */
class SummaryStatsCache internal constructor(
    private val store: SummaryStatsStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {

    /** Production cache backed by the `summary_stats_cache` SharedPreferences file. */
    constructor(context: Context) : this(
        SharedPreferencesStatsStore(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

    /** The cached snapshot for [date], or null when nothing was captured that day. */
    fun dayFor(date: LocalDate): CachedDayStats? = readDay()?.takeIf { it.date == date }

    /** The cached snapshot for the week starting on [weekStart], or null. */
    fun weekFor(weekStart: LocalDate): CachedWeekStats? =
        readWeek()?.takeIf { it.weekStart == weekStart }

    /** Persists [stats] as the snapshot for [date] (today by default). */
    fun saveDay(
        stats: LocalDayStats,
        date: LocalDate = LocalDate.now(),
        capturedAtMs: Long = System.currentTimeMillis(),
    ) {
        val snapshot = DaySnapshot(
            date = date.toString(),
            capturedAtMs = capturedAtMs,
            stats = stats,
        )
        store.write(KEY_DAY, json.encodeToString(DaySnapshot.serializer(), snapshot))
    }

    /** Persists [stats] as the snapshot for the week starting on [weekStart]. */
    fun saveWeek(
        stats: LocalWeekStats,
        weekStart: LocalDate,
        capturedAtMs: Long = System.currentTimeMillis(),
    ) {
        val snapshot = WeekSnapshot(
            weekStart = weekStart.toString(),
            capturedAtMs = capturedAtMs,
            stats = stats,
        )
        store.write(KEY_WEEK, json.encodeToString(WeekSnapshot.serializer(), snapshot))
    }

    private fun readDay(): CachedDayStats? = decode(KEY_DAY, DaySnapshot.serializer())?.let { snapshot ->
        val date = runCatching { LocalDate.parse(snapshot.date) }.getOrNull() ?: return null
        CachedDayStats(date = date, capturedAtMs = snapshot.capturedAtMs, stats = snapshot.stats)
    }

    private fun readWeek(): CachedWeekStats? =
        decode(KEY_WEEK, WeekSnapshot.serializer())?.let { snapshot ->
            val weekStart = runCatching { LocalDate.parse(snapshot.weekStart) }.getOrNull() ?: return null
            CachedWeekStats(
                weekStart = weekStart,
                capturedAtMs = snapshot.capturedAtMs,
                stats = snapshot.stats,
            )
        }

    private fun <T> decode(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? {
        val raw = store.read(key) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    @Serializable
    private data class DaySnapshot(
        val date: String,
        val capturedAtMs: Long,
        val stats: LocalDayStats,
    )

    @Serializable
    private data class WeekSnapshot(
        val weekStart: String,
        val capturedAtMs: Long,
        val stats: LocalWeekStats,
    )

    private companion object {
        const val PREFS_NAME = "summary_stats_cache"
        const val KEY_DAY = "day"
        const val KEY_WEEK = "week"
    }
}

/** Storage behind [SummaryStatsCache]; keeps the cache testable without Android. */
internal interface SummaryStatsStore {
    fun read(key: String): String?

    fun write(key: String, value: String)
}

private class SharedPreferencesStatsStore(
    private val prefs: SharedPreferences,
) : SummaryStatsStore {
    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
