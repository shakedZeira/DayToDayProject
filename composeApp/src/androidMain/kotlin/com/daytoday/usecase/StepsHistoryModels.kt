package com.daytoday.usecase

import java.time.LocalDate

enum class StepsPeriod(val label: String) {
    DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year")
}

data class DayRange(val start: LocalDate, val endInclusive: LocalDate) {
    init { require(!endInclusive.isBefore(start)) }
    val days: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
}

data class StepsDay(
    val date: LocalDate,
    val steps: Long,
    /** true only when the Health Connect read returned this date. false = Health Connect did not return this day or it is in the future */
    val hasData: Boolean,
    val isFuture: Boolean,
    val isToday: Boolean,
)

data class StepsBucket(
    val label: String,              // "Today", "Mon".."Sun", "1".."31", "Jan".."Dec"
    val start: LocalDate,           // day or first day of month
    val endInclusive: LocalDate,
    val steps: Long,
    /** Goal for this bucket: dailyGoal for day bars; dailyGoal * elapsed days for month bars. */
    val goal: Long,
    val isCurrent: Boolean,         // today's bar / current month's bar
    val hasData: Boolean,
)

data class StepsListRow(
    val label: String,              // "Today", "Mon 20", "Mar"
    val steps: Long,
    val goal: Long,
    val hasData: Boolean,
    val isCurrent: Boolean,
)

data class StepsStats(
    val total: Long,
    val averagePerDay: Long,
    val bestDate: LocalDate?,
    val bestSteps: Long?,
    /** null means daily data is unavailable, e.g. YEAR */
    val daysGoalMet: Int?,
    val elapsedDays: Int,
    val daysWithData: Int,
)

data class StepsHistory(
    val period: StepsPeriod,
    val range: DayRange,
    val goal: Long,                 // daily goal
    val paceTarget: Long,           // goal * elapsedDays
    val todaySteps: Long,
    val todayHasData: Boolean,
    val days: List<StepsDay>,       // daily data for DAY/WEEK/MONTH; empty for YEAR
    val buckets: List<StepsBucket>,
    val listRows: List<StepsListRow>,
    val stats: StepsStats,
    val previousTotal: Long?,
    val changePercent: Int?,
    val totalDistanceKm: Double = 0.0,
    val totalCalories: Int = 0,
    val generatedAtMs: Long = 0L,
)

sealed interface StepsLoadOutcome {
    data class Success(val history: StepsHistory) : StepsLoadOutcome
    data object PermissionMissing : StepsLoadOutcome
    data class Failure(val message: String) : StepsLoadOutcome
}
