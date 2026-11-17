package com.daytoday.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The three extra Health Connect data types read for the Progress screen. */
enum class VitalsKind { WEIGHT, SLEEP, HEART_RATE }

data class WeightReading(val time: Instant, val kg: Double)

/** A span the user was asleep (a whole session, or one asleep stage of it). */
data class SleepInterval(val start: Instant, val end: Instant)

data class HeartRateSample(val time: Instant, val bpm: Long)

data class HeartRateSummary(
    val average: Int,
    val min: Int,
    val max: Int,
    /** Estimate: mean of the lowest fifth of the day's samples. */
    val resting: Int,
)

data class DayVitals(
    val date: LocalDate,
    val weightKg: Double? = null,
    val sleepMinutes: Int? = null,
    val heartRate: HeartRateSummary? = null,
) {
    val isEmpty: Boolean get() = weightKg == null && sleepMinutes == null && heartRate == null
}

data class VitalsSnapshot(
    val today: DayVitals,
    /** Oldest first, one entry per day in the window including today. */
    val history: List<DayVitals>,
    val latestWeightKg: Double?,
    /** Latest weight minus the earliest weight in the window; null with fewer than two readings. */
    val weightChangeKg: Double?,
    val grantedKinds: Set<VitalsKind>,
)

/** Pure aggregation rules; no Health Connect types so they can be unit tested on the JVM. */
object VitalsAggregator {

    const val MIN_VALID_BPM = 20L
    const val MAX_VALID_BPM = 250L
    private const val MIN_VALID_KG = 20.0
    private const val MAX_VALID_KG = 500.0
    private const val MAX_SLEEP_SPAN_MINUTES = 24 * 60

    fun latestWeight(readings: List<WeightReading>): WeightReading? =
        readings.filter { it.kg.isFinite() && it.kg in MIN_VALID_KG..MAX_VALID_KG }.maxByOrNull { it.time }

    /** Last valid reading of each local day. */
    fun weightByDay(readings: List<WeightReading>, zone: ZoneId): Map<LocalDate, Double> =
        readings
            .filter { it.kg.isFinite() && it.kg in MIN_VALID_KG..MAX_VALID_KG }
            .sortedBy { it.time }
            .associate { it.time.atZone(zone).toLocalDate() to it.kg }

    /**
     * Minutes asleep per local day. Overlapping intervals (two apps recording the same night)
     * are merged so nothing is counted twice, and a night is attributed to the day the user
     * woke up on. Empty or inverted intervals are ignored.
     */
    fun sleepMinutesByDay(intervals: List<SleepInterval>, zone: ZoneId): Map<LocalDate, Int> {
        val sorted = intervals.filter { it.end.isAfter(it.start) }.sortedBy { it.start }
        if (sorted.isEmpty()) return emptyMap()
        val merged = mutableListOf<SleepInterval>()
        for (iv in sorted) {
            val last = merged.lastOrNull()
            if (last != null && !iv.start.isAfter(last.end)) {
                if (iv.end.isAfter(last.end)) merged[merged.lastIndex] = SleepInterval(last.start, iv.end)
            } else {
                merged += iv
            }
        }
        val totals = linkedMapOf<LocalDate, Long>()
        for (iv in merged) {
            val minutes = java.time.Duration.between(iv.start, iv.end).toMinutes()
            if (minutes <= 0) continue
            val day = iv.end.atZone(zone).toLocalDate()
            totals[day] = (totals[day] ?: 0L) + minutes
        }
        return totals.mapValues { (_, minutes) -> minutes.coerceAtMost(MAX_SLEEP_SPAN_MINUTES.toLong()).toInt() }
    }

    fun heartRateSummary(samples: List<HeartRateSample>): HeartRateSummary? {
        val bpm = samples.map { it.bpm }.filter { it in MIN_VALID_BPM..MAX_VALID_BPM }.sorted()
        if (bpm.isEmpty()) return null
        val lowest = bpm.take(maxOf(1, bpm.size / 5))
        return HeartRateSummary(
            average = bpm.average().roundToInt(),
            min = bpm.first().toInt(),
            max = bpm.last().toInt(),
            resting = lowest.average().roundToInt(),
        )
    }

    fun heartRateByDay(samples: List<HeartRateSample>, zone: ZoneId): Map<LocalDate, HeartRateSummary> =
        samples.groupBy { it.time.atZone(zone).toLocalDate() }
            .mapNotNull { (day, list) -> heartRateSummary(list)?.let { day to it } }
            .toMap()

    /**
     * Builds the [days]-day window ending on [today] (oldest first).
     */
    fun snapshot(
        today: LocalDate,
        days: Int,
        weights: List<WeightReading>,
        sleep: List<SleepInterval>,
        heartRate: List<HeartRateSample>,
        grantedKinds: Set<VitalsKind>,
        zone: ZoneId,
    ): VitalsSnapshot {
        val weightDays = weightByDay(weights, zone)
        val sleepDays = sleepMinutesByDay(sleep, zone)
        val hrDays = heartRateByDay(heartRate, zone)
        val history = (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            DayVitals(date, weightDays[date], sleepDays[date], hrDays[date])
        }
        val validWeights = weights.filter { it.kg.isFinite() && it.kg in MIN_VALID_KG..MAX_VALID_KG }.sortedBy { it.time }
        val latest = validWeights.lastOrNull()
        val change = if (validWeights.size >= 2) latest!!.kg - validWeights.first().kg else null
        return VitalsSnapshot(
            today = history.last(),
            history = history,
            latestWeightKg = latest?.kg,
            weightChangeKg = change?.let { (it * 10).roundToInt() / 10.0 },
            grantedKinds = grantedKinds,
        )
    }
}

/** "7h 05m" style duration for a sleep total. */
fun formatSleep(minutes: Int): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
