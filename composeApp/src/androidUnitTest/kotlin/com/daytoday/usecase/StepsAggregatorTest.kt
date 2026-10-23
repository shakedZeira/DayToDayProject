package com.daytoday.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StepsAggregatorTest {
    @Test
    fun periodRangeWeekWednesday() {
        val wed = LocalDate.of(2026, 10, 21)
        val range = StepsAggregator.periodRange(StepsPeriod.WEEK, wed)
        assertEquals(LocalDate.of(2026, 10, 19), range.start) // Monday
        assertEquals(LocalDate.of(2026, 10, 25), range.endInclusive) // Sunday
    }

    @Test
    fun periodRangeWeekMonday() {
        val mon = LocalDate.of(2026, 10, 19)
        val range = StepsAggregator.periodRange(StepsPeriod.WEEK, mon)
        assertEquals(mon, range.start)
        assertEquals(LocalDate.of(2026, 10, 25), range.endInclusive)
    }

    @Test
    fun periodRangeWeekSunday() {
        val sun = LocalDate.of(2026, 10, 25)
        val range = StepsAggregator.periodRange(StepsPeriod.WEEK, sun)
        assertEquals(LocalDate.of(2026, 10, 19), range.start)
        assertEquals(sun, range.endInclusive)
    }

    @Test
    fun periodRangeMonthFebNonLeap() {
        val date = LocalDate.of(2026, 2, 10)
        val range = StepsAggregator.periodRange(StepsPeriod.MONTH, date)
        assertEquals(LocalDate.of(2026, 2, 1), range.start)
        assertEquals(LocalDate.of(2026, 2, 28), range.endInclusive)
    }

    @Test
    fun periodRangeMonthFebLeap() {
        val date = LocalDate.of(2028, 2, 10)
        val range = StepsAggregator.periodRange(StepsPeriod.MONTH, date)
        assertEquals(LocalDate.of(2028, 2, 1), range.start)
        assertEquals(LocalDate.of(2028, 2, 29), range.endInclusive)
    }

    @Test
    fun periodRangeYear() {
        val date = LocalDate.of(2026, 10, 23)
        val range = StepsAggregator.periodRange(StepsPeriod.YEAR, date)
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun previousRangeAll() {
        val today = LocalDate.of(2026, 10, 21)
        val prevWeek = StepsAggregator.previousRange(StepsPeriod.WEEK, today)
        assertEquals(LocalDate.of(2026, 10, 12), prevWeek.start)
        assertEquals(LocalDate.of(2026, 10, 18), prevWeek.endInclusive)
        val prevMonth = StepsAggregator.previousRange(StepsPeriod.MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1), prevMonth.start)
        assertEquals(LocalDate.of(2026, 9, 30), prevMonth.endInclusive)
        val prevYear = StepsAggregator.previousRange(StepsPeriod.YEAR, today)
        assertEquals(LocalDate.of(2025, 1, 1), prevYear.start)
        assertEquals(LocalDate.of(2025, 12, 31), prevYear.endInclusive)
    }

    @Test
    fun buildDaysFlags() {
        val today = LocalDate.of(2026, 10, 21)
        val range = DayRange(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22))
        val map = mapOf(LocalDate.of(2026, 10, 20) to 4000L)
        val days = StepsAggregator.buildDays(range, today, map)
        val day20 = days[0]
        assertEquals(true, day20.hasData)
        assertEquals(4000L, day20.steps)
        val day21 = days[1]
        assertEquals(true, day21.isToday)
        assertEquals(false, day21.hasData)
        assertEquals(0L, day21.steps)
        val day22 = days[2]
        assertEquals(true, day22.isFuture)
        assertEquals(false, day22.hasData)
    }

    @Test
    fun dailyBucketsWeek() {
        val today = LocalDate.of(2026, 10, 21)
        val range = StepsAggregator.periodRange(StepsPeriod.WEEK, today)
        val map = mapOf(
            LocalDate.of(2026, 10, 19) to 4000L,
            LocalDate.of(2026, 10, 20) to 9000L,
            today to 3000L,
        )
        val days = StepsAggregator.buildDays(range, today, map)
        val buckets = StepsAggregator.dailyBuckets(StepsPeriod.WEEK, days, 8000L)
        assertEquals(7, buckets.size)
        assertEquals("Mon", buckets[0].label)
        assertEquals("Sun", buckets[6].label)
        // today is Wed index 2
        assertEquals(true, buckets[2].isCurrent)
        assertEquals(false, buckets[5].hasData) // future Sat? today Wed, Sat is future
    }

    @Test
    fun monthlyBuckets() {
        val today = LocalDate.of(2026, 10, 15)
        val monthTotals = mapOf(9 to 200000L) // September past
        val buckets = StepsAggregator.monthlyBuckets(today, monthTotals, 8000L)
        assertEquals(12, buckets.size)
        // current month October
        val oct = buckets[9] // index 9 is month 10? Jan is 0? month starts at 1, we iterate 1..12 so buckets[0] is Jan (month 1), buckets[9] is Oct (month 10)
        assertEquals("Oct", oct.label)
        assertEquals(true, oct.isCurrent)
        assertEquals(8000L * 15, oct.goal)
        // past September
        val sep = buckets[8]
        assertEquals("Sep", sep.label)
        assertEquals(8000L * 30, sep.goal)
        assertEquals(200000L, sep.steps)
        // future month
        val nov = buckets[10]
        assertEquals("Nov", nov.label)
        assertEquals(false, nov.hasData)
        assertEquals(0L, nov.goal)
    }

    @Test
    fun statsExample() {
        // Mon 4,000 (data), Tue 9,000 (data), Wed/today 3,000 (data), goal 8,000
        val today = LocalDate.of(2026, 10, 21)
        val range = DayRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21))
        val map = mapOf(
            LocalDate.of(2026, 10, 19) to 4000L,
            LocalDate.of(2026, 10, 20) to 9000L,
            LocalDate.of(2026, 10, 21) to 3000L,
        )
        val days = StepsAggregator.buildDays(range, today, map)
        val stats = StepsAggregator.stats(days, 8000L, today)
        assertEquals(16000L, stats.total)
        assertEquals(6500L, stats.averagePerDay) // (4000+9000)/2
        assertEquals(LocalDate.of(2026, 10, 20), stats.bestDate)
        assertEquals(9000L, stats.bestSteps)
        assertEquals(1, stats.daysGoalMet)
        assertEquals(3, stats.elapsedDays)
        assertEquals(3, stats.daysWithData)
    }

    @Test
    fun statsMondayOnly() {
        val today = LocalDate.of(2026, 10, 19)
        val range = DayRange(today, today)
        val map = mapOf(today to 5000L)
        val days = StepsAggregator.buildDays(range, today, map)
        val stats = StepsAggregator.stats(days, 8000L, today)
        assertEquals(5000L, stats.averagePerDay)
    }

    @Test
    fun statsMissingCompletedDay() {
        val today = LocalDate.of(2026, 10, 22)
        val range = DayRange(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22))
        val map = mapOf(
            LocalDate.of(2026, 10, 20) to 9000L,
            LocalDate.of(2026, 10, 22) to 3000L,
        )
        val days = StepsAggregator.buildDays(range, today, map)
        val stats = StepsAggregator.stats(days, 8000L, today)
        assertEquals(2, stats.daysWithData)
        assertEquals(1, stats.daysGoalMet) // only Tue? 20th is Tue, 9000>=8000 yes; today excluded from completed but included in goal met count? goal met counts days up to today with hasData and steps>=goal - "includes today if already met" per plan
    }

    @Test
    fun yearStats() {
        val today = LocalDate.of(2026, 10, 23)
        val monthTotals = mapOf(1 to 100000L, 2 to 200000L)
        val buckets = StepsAggregator.monthlyBuckets(today, monthTotals, 8000L)
        val stats = StepsAggregator.yearStats(buckets, 8000L, today)
        assertEquals(300000L, stats.total)
        assertNull(stats.daysGoalMet)
        assertEquals(2, stats.daysWithData)
        assertEquals(today.dayOfYear, stats.elapsedDays)
        assertEquals(LocalDate.of(2026, 2, 1), stats.bestDate)
    }

    @Test
    fun changePercent() {
        assertEquals(20, StepsAggregator.changePercent(12000, 10000))
        assertNull(StepsAggregator.changePercent(5000, 0))
        assertNull(StepsAggregator.changePercent(5000, null))
    }

    @Test
    fun percentage() {
        assertEquals(113, StepsAggregator.percentage(9000, 8000))
    }
}
