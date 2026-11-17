package com.daytoday.data.health

import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VitalsRepositoryImplTest {

    private class FakeSource : VitalsSource {
        var unavailable: VitalsUnavailable? = null
        var granted: Set<VitalsKind> = VitalsKind.values().toSet()
        var failWith: Throwable? = null
        val weightCalls = mutableListOf<Pair<Instant, Instant>>()
        val sleepCalls = mutableListOf<Pair<Instant, Instant>>()
        val hrCalls = mutableListOf<Pair<Instant, Instant>>()
        var weights = listOf(WeightReading(Instant.parse("2026-11-11T07:00:00Z"), 72.0))
        var sleep = listOf(SleepInterval(Instant.parse("2026-11-10T23:00:00Z"), Instant.parse("2026-11-11T06:00:00Z")))
        var hr = listOf(HeartRateSample(Instant.parse("2026-11-11T08:00:00Z"), 66L))

        override suspend fun availability() = unavailable
        override suspend fun grantedKinds(): Set<VitalsKind> {
            failWith?.let { throw it }
            return granted
        }
        override suspend fun weights(from: Instant, to: Instant): List<WeightReading> {
            weightCalls += from to to; return weights
        }
        override suspend fun sleep(from: Instant, to: Instant): List<SleepInterval> {
            sleepCalls += from to to; return sleep
        }
        override suspend fun heartRate(from: Instant, to: Instant): List<HeartRateSample> {
            hrCalls += from to to; return hr
        }
    }

    private val source = FakeSource()
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val repo = VitalsRepositoryImpl(source, zone = { ZoneOffset.UTC }, now = { now })

    @Test
    fun `reports unavailable when Health Connect is missing`() = runTest {
        source.unavailable = VitalsUnavailable.NOT_INSTALLED
        assertEquals(VitalsLoad.Unavailable(VitalsUnavailable.NOT_INSTALLED), repo.load())
        assertTrue(source.weightCalls.isEmpty())
    }

    @Test
    fun `asks for permission when nothing is granted`() = runTest {
        source.granted = emptySet()
        assertEquals(VitalsLoad.NeedsPermission, repo.load())
        assertTrue(source.weightCalls.isEmpty() && source.sleepCalls.isEmpty() && source.hrCalls.isEmpty())
    }

    @Test
    fun `reads all three kinds and builds the snapshot`() = runTest {
        val ready = repo.load(days = 7) as VitalsLoad.Ready
        assertEquals(72.0, ready.snapshot.latestWeightKg!!, 0.0)
        assertEquals(420, ready.snapshot.today.sleepMinutes)
        assertEquals(66, ready.snapshot.today.heartRate!!.average)
        assertEquals(7, ready.snapshot.history.size)
    }

    @Test
    fun `only granted kinds are read`() = runTest {
        source.granted = setOf(VitalsKind.SLEEP)
        val ready = repo.load() as VitalsLoad.Ready
        assertTrue(source.weightCalls.isEmpty())
        assertTrue(source.hrCalls.isEmpty())
        assertEquals(1, source.sleepCalls.size)
        assertEquals(null, ready.snapshot.latestWeightKg)
        assertEquals(setOf(VitalsKind.SLEEP), ready.snapshot.grantedKinds)
    }

    @Test
    fun `window starts at midnight of the first day and sleep looks back further`() = runTest {
        repo.load(days = 3)
        val (from, to) = source.weightCalls.single()
        assertEquals(Instant.parse("2026-11-09T00:00:00Z"), from)
        assertEquals(now, to)
        assertEquals(Instant.parse("2026-11-08T12:00:00Z"), source.sleepCalls.single().first)
    }

    @Test
    fun `days is clamped to a sane range`() = runTest {
        assertEquals(1, (repo.load(days = 0) as VitalsLoad.Ready).snapshot.history.size)
        assertEquals(31, (repo.load(days = 400) as VitalsLoad.Ready).snapshot.history.size)
    }

    @Test
    fun `a failing read is reported without throwing`() = runTest {
        source.failWith = IOException("Health Connect died")
        assertEquals(VitalsLoad.Failed("Health Connect died"), repo.load())
    }

    @Test
    fun `a security exception from a revoked permission becomes a failure`() = runTest {
        source.failWith = SecurityException("permission revoked")
        assertTrue(repo.load() is VitalsLoad.Failed)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        source.failWith = CancellationException("cancelled")
        try {
            repo.load()
            fail("expected cancellation")
        } catch (_: CancellationException) {
        }
    }
}
