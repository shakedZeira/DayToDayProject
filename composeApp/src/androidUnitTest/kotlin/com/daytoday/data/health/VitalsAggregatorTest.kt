package com.daytoday.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalsAggregatorTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun at(date: String, time: String = "00:00") = Instant.parse("${date}T$time:00Z")

    // ---- weight ----

    @Test
    fun `latest weight picks the newest valid reading`() {
        val readings = listOf(
            WeightReading(at("2026-11-10"), 80.0),
            WeightReading(at("2026-11-12"), 79.2),
            WeightReading(at("2026-11-11"), 79.8),
        )
        assertEquals(79.2, VitalsAggregator.latestWeight(readings)!!.kg, 0.0)
    }

    @Test
    fun `implausible weights are ignored`() {
        val readings = listOf(
            WeightReading(at("2026-11-10"), 0.0),
            WeightReading(at("2026-11-11"), Double.NaN),
            WeightReading(at("2026-11-12"), 900.0),
            WeightReading(at("2026-11-09"), 75.0),
        )
        assertEquals(75.0, VitalsAggregator.latestWeight(readings)!!.kg, 0.0)
        assertNull(VitalsAggregator.latestWeight(emptyList()))
    }

    @Test
    fun `weight by day keeps the last reading of each local day`() {
        val readings = listOf(
            WeightReading(at("2026-11-10", "07:00"), 80.5),
            WeightReading(at("2026-11-10", "21:00"), 80.9),
            WeightReading(at("2026-11-11", "07:00"), 80.1),
        )
        val byDay = VitalsAggregator.weightByDay(readings, utc)
        assertEquals(80.9, byDay[LocalDate.parse("2026-11-10")]!!, 0.0)
        assertEquals(80.1, byDay[LocalDate.parse("2026-11-11")]!!, 0.0)
    }

    @Test
    fun `weight day boundaries follow the zone`() {
        val readings = listOf(WeightReading(at("2026-11-10", "23:30"), 70.0))
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(LocalDate.parse("2026-11-11"), VitalsAggregator.weightByDay(readings, tokyo).keys.single())
    }

    // ---- sleep ----

    @Test
    fun `a night is attributed to the day the user woke up`() {
        val night = SleepInterval(at("2026-11-10", "23:00"), at("2026-11-11", "07:00"))
        val byDay = VitalsAggregator.sleepMinutesByDay(listOf(night), utc)
        assertEquals(mapOf(LocalDate.parse("2026-11-11") to 480), byDay)
    }

    @Test
    fun `overlapping recordings of the same night are not double counted`() {
        val a = SleepInterval(at("2026-11-10", "23:00"), at("2026-11-11", "06:00"))
        val b = SleepInterval(at("2026-11-10", "23:30"), at("2026-11-11", "07:00"))
        val byDay = VitalsAggregator.sleepMinutesByDay(listOf(b, a), utc)
        assertEquals(480, byDay[LocalDate.parse("2026-11-11")])
    }

    @Test
    fun `separate stages of one night add up`() {
        val stages = listOf(
            SleepInterval(at("2026-11-11", "00:00"), at("2026-11-11", "02:00")),
            SleepInterval(at("2026-11-11", "02:30"), at("2026-11-11", "05:00")),
        )
        assertEquals(270, VitalsAggregator.sleepMinutesByDay(stages, utc)[LocalDate.parse("2026-11-11")])
    }

    @Test
    fun `a nap and the night count toward the same day`() {
        val nap = SleepInterval(at("2026-11-11", "14:00"), at("2026-11-11", "14:30"))
        val night = SleepInterval(at("2026-11-10", "23:00"), at("2026-11-11", "06:00"))
        assertEquals(450, VitalsAggregator.sleepMinutesByDay(listOf(nap, night), utc)[LocalDate.parse("2026-11-11")])
    }

    @Test
    fun `empty or inverted intervals are ignored`() {
        val bad = listOf(
            SleepInterval(at("2026-11-11", "05:00"), at("2026-11-11", "05:00")),
            SleepInterval(at("2026-11-11", "06:00"), at("2026-11-11", "05:00")),
        )
        assertTrue(VitalsAggregator.sleepMinutesByDay(bad, utc).isEmpty())
    }

    // ---- heart rate ----

    @Test
    fun `heart rate summary gives average min max and a resting estimate`() {
        val samples = listOf(50L, 55L, 60L, 70L, 80L, 100L, 110L, 120L, 65L, 90L).map {
            HeartRateSample(at("2026-11-11", "10:00"), it)
        }
        val s = VitalsAggregator.heartRateSummary(samples)!!
        assertEquals(80, s.average)
        assertEquals(50, s.min)
        assertEquals(120, s.max)
        // lowest fifth of 10 samples = 50 and 55
        assertEquals(53, s.resting)
    }

    @Test
    fun `out of range beats are discarded and an empty set has no summary`() {
        val samples = listOf(0L, 5L, 300L, 72L).map { HeartRateSample(at("2026-11-11"), it) }
        val s = VitalsAggregator.heartRateSummary(samples)!!
        assertEquals(72, s.average)
        assertEquals(72, s.resting)
        assertNull(VitalsAggregator.heartRateSummary(listOf(HeartRateSample(at("2026-11-11"), 3L))))
        assertNull(VitalsAggregator.heartRateSummary(emptyList()))
    }

    @Test
    fun `single sample is its own resting rate`() {
        val s = VitalsAggregator.heartRateSummary(listOf(HeartRateSample(at("2026-11-11"), 61L)))!!
        assertEquals(61, s.resting)
    }

    @Test
    fun `heart rate is grouped per local day`() {
        val samples = listOf(
            HeartRateSample(at("2026-11-10", "12:00"), 60L),
            HeartRateSample(at("2026-11-11", "12:00"), 90L),
        )
        val byDay = VitalsAggregator.heartRateByDay(samples, utc)
        assertEquals(60, byDay[LocalDate.parse("2026-11-10")]!!.average)
        assertEquals(90, byDay[LocalDate.parse("2026-11-11")]!!.average)
    }

    // ---- snapshot ----

    @Test
    fun `snapshot covers the window oldest first and reports weight change`() {
        val today = LocalDate.parse("2026-11-11")
        val snap = VitalsAggregator.snapshot(
            today = today,
            days = 3,
            weights = listOf(
                WeightReading(at("2026-11-09", "08:00"), 81.0),
                WeightReading(at("2026-11-11", "08:00"), 80.4),
            ),
            sleep = listOf(SleepInterval(at("2026-11-10", "23:00"), at("2026-11-11", "06:30"))),
            heartRate = listOf(HeartRateSample(at("2026-11-11", "09:00"), 64L)),
            grantedKinds = VitalsKind.values().toSet(),
            zone = utc,
        )
        assertEquals(
            listOf("2026-11-09", "2026-11-10", "2026-11-11"),
            snap.history.map { it.date.toString() },
        )
        assertEquals(today, snap.today.date)
        assertEquals(80.4, snap.latestWeightKg!!, 0.0)
        assertEquals(-0.6, snap.weightChangeKg!!, 0.0001)
        assertEquals(450, snap.today.sleepMinutes)
        assertEquals(64, snap.today.heartRate!!.average)
        assertTrue(snap.history[1].isEmpty)
    }

    @Test
    fun `weight change needs two readings`() {
        val snap = VitalsAggregator.snapshot(
            LocalDate.parse("2026-11-11"), 7,
            listOf(WeightReading(at("2026-11-11"), 70.0)), emptyList(), emptyList(),
            setOf(VitalsKind.WEIGHT), utc,
        )
        assertNull(snap.weightChangeKg)
        assertNotNull(snap.latestWeightKg)
    }

    @Test
    fun `formatSleep pads minutes`() {
        assertEquals("7h 05m", formatSleep(425))
        assertEquals("0h 00m", formatSleep(0))
        assertEquals("8h 30m", formatSleep(510))
    }
}
