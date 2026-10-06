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

fun formatSteps(steps: Int): String = when {
    steps >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", steps / 1_000_000.0)
    steps >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", steps / 1_000.0)
    else -> steps.toString()
}

fun formatVolume(weight: Double, reps: Int): String = "${formatNumber(weight)} x $reps"

fun formatOneRM(weight: Double, reps: Int): String {
    val oneRM = weight * (1 + reps / 30.0)
    return formatNumber(oneRM)
}
