package com.daytoday.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepAggregationTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 10, 20)

    /** 2026-10-20 15:30 in Tokyo. */
    private val now = Instant.parse("2026-10-20T06:30:00Z")

    private fun window(from: LocalDate, to: LocalDate): StepWindow? =
        stepWindow(from = from, to = to, today = today, now = now, zone = zone)

    @Test
    fun `past range ends at the local midnight after the last day`() {
        val result = window(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 19))

        assertEquals(Instant.parse("2026-10-17T15:00:00Z"), result?.start)
        assertEquals(Instant.parse("2026-10-19T15:00:00Z"), result?.end)
    }

    @Test
    fun `range ending today is capped at now`() {
        val result = window(LocalDate.of(2026, 10, 19), today)

        assertEquals(Instant.parse("2026-10-18T15:00:00Z"), result?.start)
        assertEquals(now, result?.end)
    }

    @Test
    fun `range extending into the future is capped at now`() {
        val result = window(LocalDate.of(2026, 10, 19), today.plusDays(5))

        assertEquals(now, result?.end)
    }

    @Test
    fun `inverted range produces no window`() {
        assertNull(window(today, today.minusDays(1)))
    }

    @Test
    fun `future-only range produces no window`() {
        assertNull(window(today.plusDays(1), today.plusDays(2)))
    }

    @Test
    fun `exactly midnight produces no window`() {
        val midnight = today.atStartOfDay(zone).toInstant()

        assertNull(stepWindow(today, today, today, midnight, zone))
    }

    @Test
    fun `samsung health origin is applied to the aggregate spec`() {
        val window = StepWindow(start = Instant.parse("2026-10-19T15:00:00Z"), end = now)

        val spec = stepAggregateSpec(window, "com.sec.android.app.shealth")

        assertEquals(setOf("com.sec.android.app.shealth"), spec.dataOriginFilter)
        assertEquals(window, spec.window)
    }

    @Test
    fun `missing origin leaves the aggregate unfiltered`() {
        val window = StepWindow(start = Instant.parse("2026-10-19T15:00:00Z"), end = now)

        val spec = stepAggregateSpec(window, null)

        assertTrue(spec.dataOriginFilter.isEmpty())
        assertEquals(window, spec.window)
    }
}
