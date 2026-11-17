package com.daytoday.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import kotlin.reflect.KClass

/** Read permissions the Progress screen asks for. */
object VitalsPermissions {
    val byKind: Map<VitalsKind, String> = mapOf(
        VitalsKind.WEIGHT to HealthPermission.getReadPermission(WeightRecord::class),
        VitalsKind.SLEEP to HealthPermission.getReadPermission(SleepSessionRecord::class),
        VitalsKind.HEART_RATE to HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    val all: Set<String> = byKind.values.toSet()

    fun grantedKinds(granted: Set<String>): Set<VitalsKind> =
        byKind.filterValues { it in granted }.keys
}

/** Sleep stages that count as being asleep (awake / out-of-bed time does not). */
internal fun isAsleepStageType(type: Int): Boolean = when (type) {
    SleepSessionRecord.STAGE_TYPE_SLEEPING,
    SleepSessionRecord.STAGE_TYPE_LIGHT,
    SleepSessionRecord.STAGE_TYPE_DEEP,
    SleepSessionRecord.STAGE_TYPE_REM,
    -> true
    else -> false
}

internal fun SleepSessionRecord.asleepIntervals(): List<SleepInterval> =
    if (stages.isEmpty()) {
        listOf(SleepInterval(startTime, endTime))
    } else {
        stages.filter { isAsleepStageType(it.stage) }.map { SleepInterval(it.startTime, it.endTime) }
    }

class HealthConnectVitalsSource(private val context: Context) : VitalsSource {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): VitalsUnavailable? =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> null
            HealthConnectClient.SDK_UNAVAILABLE -> VitalsUnavailable.UNSUPPORTED
            else -> VitalsUnavailable.NOT_INSTALLED
        }

    override suspend fun grantedKinds(): Set<VitalsKind> =
        VitalsPermissions.grantedKinds(client.permissionController.getGrantedPermissions())

    override suspend fun weights(from: Instant, to: Instant): List<WeightReading> =
        readAll(WeightRecord::class, from, to).map { WeightReading(it.time, it.weight.inKilograms) }

    override suspend fun sleep(from: Instant, to: Instant): List<SleepInterval> =
        readAll(SleepSessionRecord::class, from, to).flatMap { it.asleepIntervals() }

    override suspend fun heartRate(from: Instant, to: Instant): List<HeartRateSample> =
        readAll(HeartRateRecord::class, from, to).flatMap { record ->
            record.samples.map { HeartRateSample(it.time, it.beatsPerMinute) }
        }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageSize = PAGE_SIZE,
                    pageToken = token,
                ),
            )
            out += response.records
            token = response.pageToken
            pages++
        } while (token != null && pages < MAX_PAGES)
        return out
    }

    private companion object {
        const val PAGE_SIZE = 1_000
        const val MAX_PAGES = 30
    }
}
