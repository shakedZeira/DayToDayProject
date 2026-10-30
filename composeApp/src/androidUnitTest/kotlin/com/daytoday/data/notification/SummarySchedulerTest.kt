package com.daytoday.data.notification

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for the summary alarm math and preference coercion.
 * The default time zone is pinned so `ZoneId.systemDefault()` is deterministic.
 */
class SummarySchedulerTest {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()
    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `daily trigger later today when now is before the alert time`() {
        val now = millis(2026, 10, 20, 20, 30)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.DAILY, nowMillis = now)

        assertEquals(millis(2026, 10, 20, 21, 0), trigger)
    }

    @Test
    fun `daily trigger rolls to tomorrow exactly at the alert time`() {
        val now = millis(2026, 10, 20, 21, 0)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.DAILY, nowMillis = now)

        assertEquals(millis(2026, 10, 21, 21, 0), trigger)
    }

    @Test
    fun `daily trigger rolls to tomorrow after the alert time`() {
        val now = millis(2026, 10, 20, 21, 0, 1)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.DAILY, nowMillis = now)

        assertEquals(millis(2026, 10, 21, 21, 0), trigger)
    }

    @Test
    fun `daily trigger honors a custom alert time`() {
        val config = SummaryScheduler.ScheduleConfig(dailyHour = 7, dailyMinute = 15)
        val now = millis(2026, 10, 20, 6, 0)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.DAILY, config, now)

        assertEquals(millis(2026, 10, 20, 7, 15), trigger)
    }

    @Test
    fun `weekly trigger is the coming Saturday when now is earlier in the week`() {
        // 2026-10-20 is a Tuesday; the default weekly slot is Saturday 20:00.
        val now = millis(2026, 10, 20, 12, 0)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.WEEKLY, nowMillis = now)

        assertEquals(millis(2026, 10, 24, 20, 0), trigger)
    }

    @Test
    fun `weekly trigger rolls a full week exactly at the alert time`() {
        val now = millis(2026, 10, 24, 20, 0)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.WEEKLY, nowMillis = now)

        assertEquals(millis(2026, 10, 31, 20, 0), trigger)
    }

    @Test
    fun `weekly trigger rolls a full week after the alert time`() {
        val now = millis(2026, 10, 24, 20, 0, 1)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.WEEKLY, nowMillis = now)

        assertEquals(millis(2026, 10, 31, 20, 0), trigger)
    }

    @Test
    fun `weekly trigger honors a custom day and time`() {
        val config = SummaryScheduler.ScheduleConfig(
            weeklyDay = DayOfWeek.MONDAY,
            weeklyHour = 9,
            weeklyMinute = 30,
        )
        val now = millis(2026, 10, 20, 12, 0)

        val trigger = SummaryScheduler.nextTriggerAt(SummaryKind.WEEKLY, config, now)

        assertEquals(millis(2026, 10, 26, 9, 30), trigger)
    }

    @Test
    fun `config coercion clamps out-of-range minutes and days`() {
        val config = SummaryScheduler.scheduleConfig(
            dailyMinutes = -1,
            weeklyMinutes = 24 * 60 + 30,
            weeklyDay = 99,
        )

        assertEquals(0, config.dailyHour)
        assertEquals(0, config.dailyMinute)
        assertEquals(23, config.weeklyHour)
        assertEquals(59, config.weeklyMinute)
        assertEquals(DayOfWeek.SUNDAY, config.weeklyDay)
    }

    @Test
    fun `config coercion keeps valid values and clamps a zero weekday to Monday`() {
        val valid = SummaryScheduler.scheduleConfig(
            dailyMinutes = 21 * 60,
            weeklyMinutes = 20 * 60,
            weeklyDay = 6,
        )

        assertEquals(21, valid.dailyHour)
        assertEquals(0, valid.dailyMinute)
        assertEquals(20, valid.weeklyHour)
        assertEquals(DayOfWeek.SATURDAY, valid.weeklyDay)

        val zeroDay = SummaryScheduler.scheduleConfig(0, 0, 0)
        assertEquals(DayOfWeek.MONDAY, zeroDay.weeklyDay)
    }
}
