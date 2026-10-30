package com.daytoday.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.NumberFormat
import java.util.Locale

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatDate(date: LocalDate): String = date.toString()

fun formatDateTime(timestamp: Long): String =
    Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toString()

fun formatNumber(number: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).format(number)

fun formatWeight(weight: Double, unit: String = "lbs"): String = "${formatNumber(weight)} $unit"

fun formatCalories(calories: Int): String =
    "${NumberFormat.getNumberInstance(Locale.getDefault()).format(calories)} cal"

/**
 * Exact step count with locale grouping ("7,639"), matching the workout
 * progress screen. Kept as a Long because Health Connect aggregates totals as
 * Long; narrowing to Int would overflow on very large counts.
 */
fun formatSteps(steps: Long, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%,d", steps)

fun formatVolume(weight: Double, reps: Int): String = "${formatNumber(weight)} x $reps"

fun formatOneRM(weight: Double, reps: Int): String {
    val oneRM = weight * (1 + reps / 30.0)
    return formatNumber(oneRM)
}

/** Distance estimate with up to two decimals, e.g. "7.26 km". */
fun formatDistanceKm(km: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.2f km", if (km.isFinite()) km else 0.0)
