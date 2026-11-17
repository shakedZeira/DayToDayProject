package com.daytoday.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectMappingTest {

    @Test
    fun `only real sleep stages count as asleep`() {
        assertTrue(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_SLEEPING))
        assertTrue(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_LIGHT))
        assertTrue(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_DEEP))
        assertTrue(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_REM))
        assertFalse(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_AWAKE))
        assertFalse(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED))
        assertFalse(isAsleepStageType(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED))
    }

    @Test
    fun `a session without stages counts as one interval`() {
        val start = Instant.parse("2026-11-10T23:00:00Z")
        val end = Instant.parse("2026-11-11T07:00:00Z")
        val record = SleepSessionRecord(start, null, end, null, metadata = Metadata.unknownRecordingMethod())
        assertEquals(listOf(SleepInterval(start, end)), record.asleepIntervals())
    }

    @Test
    fun `awake stages are excluded from the intervals`() {
        val t = { h: Int -> Instant.parse("2026-11-11T0$h:00:00Z") }
        val record = SleepSessionRecord(
            startTime = t(0), startZoneOffset = null, endTime = t(6), endZoneOffset = null,
            metadata = Metadata.unknownRecordingMethod(),
            stages = listOf(
                SleepSessionRecord.Stage(t(0), t(2), SleepSessionRecord.STAGE_TYPE_DEEP),
                SleepSessionRecord.Stage(t(2), t(3), SleepSessionRecord.STAGE_TYPE_AWAKE),
                SleepSessionRecord.Stage(t(3), t(6), SleepSessionRecord.STAGE_TYPE_LIGHT),
            ),
        )
        assertEquals(
            listOf(SleepInterval(t(0), t(2)), SleepInterval(t(3), t(6))),
            record.asleepIntervals(),
        )
    }

    @Test
    fun `permissions map one to one onto the kinds`() {
        assertEquals(3, VitalsPermissions.all.size)
        val granted = setOf(HealthPermission.getReadPermission(WeightRecord::class), "unrelated")
        assertEquals(setOf(VitalsKind.WEIGHT), VitalsPermissions.grantedKinds(granted))
        assertEquals(emptySet<VitalsKind>(), VitalsPermissions.grantedKinds(emptySet()))
    }

    @Test
    fun `weight record converts to kilograms`() {
        val record = WeightRecord(Instant.parse("2026-11-11T07:00:00Z"), null, Mass.kilograms(71.5), Metadata.unknownRecordingMethod())
        assertEquals(71.5, record.weight.inKilograms, 0.0001)
    }
}
