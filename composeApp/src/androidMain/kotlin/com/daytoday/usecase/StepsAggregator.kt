package com.daytoday.usecase

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

object StepsAggregator {
    fun periodRange(period: StepsPeriod, today: LocalDate): DayRange = when (period) {
        StepsPeriod.DAY -> DayRange(today, today)
        StepsPeriod.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DayRange(start, start.plusDays(6))
        }
        StepsPeriod.MONTH -> DayRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
        StepsPeriod.YEAR -> DayRange(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
    }

    fun previousRange(period: StepsPeriod, today: LocalDate): DayRange = when (period) {
        StepsPeriod.DAY -> {
            val yesterday = today.minusDays(1)
            DayRange(yesterday, yesterday)
        }
        StepsPeriod.WEEK -> {
            val currentStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val prevStart = currentStart.minusDays(7)
            DayRange(prevStart, prevStart.plusDays(6))
        }
        StepsPeriod.MONTH -> {
            val currentStart = today.withDayOfMonth(1)
            val prevStart = currentStart.minusMonths(1)
            DayRange(prevStart, prevStart.with(TemporalAdjusters.lastDayOfMonth()))
        }
        StepsPeriod.YEAR -> {
            DayRange(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
        }
    }

    fun buildDays(range: DayRange, today: LocalDate, stepsByDate: Map<LocalDate, Long>): List<StepsDay> {
        val days = mutableListOf<StepsDay>()
        var date = range.start
        while (!date.isAfter(range.endInclusive)) {
            val future = date.isAfter(today)
            val hasData = !future && stepsByDate.containsKey(date)
            days.add(
                StepsDay(
                    date = date,
                    steps = if (hasData) stepsByDate.getValue(date) else 0L,
                    hasData = hasData,
                    isFuture = future,
                    isToday = date == today,
                )
            )
            date = date.plusDays(1)
        }
        return days
    }

    fun dailyBuckets(period: StepsPeriod, days: List<StepsDay>, dailyGoal: Long): List<StepsBucket> {
        return when (period) {
            StepsPeriod.DAY -> {
                val todayDay = days.firstOrNull { it.isToday } ?: days.firstOrNull()
                if (todayDay != null) {
                    listOf(
                        StepsBucket(
                            label = "Today",
                            start = todayDay.date,
                            endInclusive = todayDay.date,
                            steps = todayDay.steps,
                            goal = dailyGoal,
                            isCurrent = todayDay.isToday,
                            hasData = todayDay.hasData,
                        )
                    )
                } else emptyList()
            }
            StepsPeriod.WEEK -> {
                days.map { day ->
                    StepsBucket(
                        label = day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                        start = day.date,
                        endInclusive = day.date,
                        steps = if (day.hasData) day.steps else 0L,
                        goal = dailyGoal,
                        isCurrent = day.isToday,
                        hasData = day.hasData,
                    )
                }
            }
            StepsPeriod.MONTH -> {
                days.map { day ->
                    StepsBucket(
                        label = day.date.dayOfMonth.toString(),
                        start = day.date,
                        endInclusive = day.date,
                        steps = if (day.hasData) day.steps else 0L,
                        goal = dailyGoal,
                        isCurrent = day.isToday,
                        hasData = day.hasData,
                    )
                }
            }
            StepsPeriod.YEAR -> emptyList()
        }
    }

    fun monthlyBuckets(today: LocalDate, monthTotals: Map<Int, Long>, dailyGoal: Long): List<StepsBucket> {
        val buckets = mutableListOf<StepsBucket>()
        for (month in 1..12) {
            val monthStart = LocalDate.of(today.year, month, 1)
            val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth())
            val isFuture = monthStart.isAfter(today)
            val isCurrent = month == today.monthValue
            val elapsedInMonth = when {
                isFuture -> 0
                isCurrent -> today.dayOfMonth
                else -> monthEnd.dayOfMonth
            }
            buckets.add(
                StepsBucket(
                    label = monthStart.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)),
                    start = monthStart,
                    endInclusive = monthEnd,
                    steps = if (isFuture) 0L else (monthTotals[month] ?: 0L),
                    goal = dailyGoal * elapsedInMonth.toLong(),
                    isCurrent = isCurrent,
                    hasData = !isFuture && monthTotals.containsKey(month),
                )
            )
        }
        return buckets
    }

    fun stats(days: List<StepsDay>, dailyGoal: Long, today: LocalDate): StepsStats {
        val elapsedDays = days.count { !it.isFuture }
        val daysWithData = days.count { !it.isFuture && it.hasData }
        val total = days.filter { it.hasData }.sumOf { it.steps }
        val completed = days.filter { !it.isFuture && !it.isToday && it.hasData }
        val todaySteps = days.firstOrNull { it.isToday }?.takeIf { it.hasData }?.steps ?: 0L
        val averagePerDay = if (completed.isNotEmpty()) {
            completed.sumOf { it.steps } / completed.size.toLong()
        } else {
            todaySteps
        }
        val best = days.filter { !it.isFuture && it.hasData }.maxByOrNull { it.steps }
        val bestDate = if (best != null && best.steps > 0) best.date else null
        val bestSteps = if (best != null && best.steps > 0) best.steps else null
        val daysGoalMet = days.count { !it.isFuture && it.hasData && it.steps >= dailyGoal }
        return StepsStats(
            total = total,
            averagePerDay = averagePerDay,
            bestDate = bestDate,
            bestSteps = bestSteps,
            daysGoalMet = daysGoalMet,
            elapsedDays = elapsedDays,
            daysWithData = daysWithData,
        )
    }

    fun yearStats(buckets: List<StepsBucket>, dailyGoal: Long, today: LocalDate): StepsStats {
        val total = buckets.sumOf { it.steps }
        val elapsedDays = today.dayOfYear
        val averagePerDay = if (elapsedDays > 0) total / elapsedDays.toLong() else 0L
        val best = buckets.filter { it.hasData && it.steps > 0 }.maxByOrNull { it.steps }
        val bestDate = best?.start
        val bestSteps = best?.steps
        val daysWithData = buckets.count { it.hasData }
        return StepsStats(
            total = total,
            averagePerDay = averagePerDay,
            bestDate = bestDate,
            bestSteps = bestSteps,
            daysGoalMet = null,
            elapsedDays = elapsedDays,
            daysWithData = daysWithData,
        )
    }

    fun dayListRows(days: List<StepsDay>, dailyGoal: Long): List<StepsListRow> {
        return days.map { day ->
            StepsListRow(
                label = if (day.isToday) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)),
                steps = day.steps,
                goal = dailyGoal,
                hasData = day.hasData,
                isCurrent = day.isToday,
            )
        }
    }

    fun monthListRows(buckets: List<StepsBucket>): List<StepsListRow> {
        return buckets.map { bucket ->
            StepsListRow(
                label = bucket.label,
                steps = bucket.steps,
                goal = bucket.goal,
                hasData = bucket.hasData,
                isCurrent = bucket.isCurrent,
            )
        }
    }

    fun changePercent(currentTotal: Long, previousTotal: Long?): Int? {
        if (previousTotal == null || previousTotal <= 0L) return null
        return ((currentTotal - previousTotal) * 100.0 / previousTotal.toDouble()).roundToInt()
    }

    fun percentage(total: Long, target: Long): Int {
        if (target <= 0L) return 0
        return ((total * 100.0 / target.toDouble())).roundToInt()
    }
}
