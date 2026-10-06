package com.daytoday.data.health

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.daytoday.repository.Result
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads step counts from Health Connect (which is where Samsung Health writes its data).
 *
 * Degrades silently: if the SDK is missing, permission is not granted, or a read throws,
 * callers get zero-valued data instead of an exception.
 */
@Singleton
class HealthRepositoryImpl @Inject constructor() : HealthRepository {

    override suspend fun isSdkAvailable(context: Context): HealthSdkStatus {
        val status = HealthConnectClient.getSdkStatus(context)
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> HealthSdkStatus.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE -> HealthSdkStatus.MISSING
            else -> HealthSdkStatus.UNAVAILABLE
        }
    }

    override suspend fun getHealthPermissionState(context: Context): Result<HealthPermissionState> {
        val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
        val client = HealthConnectClient.getOrCreate(context)
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            val hasStepsPermission = granted.contains(readSteps)
            Result.success(if (hasStepsPermission) HealthPermissionState.GRANTED else HealthPermissionState.NOT_GRANTED)
        } catch (e: Exception) {
            Log.e(TAG, "getGrantedPermissions failed", e)
            Result.failure("Failed to check permissions", e)
        }
    }

    override suspend fun requestStepsPermission(context: Context): Boolean {
        // Ensure any prior in-flight request is cleared so a second tap doesn't
        // resolve a stale deferred from an earlier permission dialog.
        HealthConnectPermissionRequest.complete(false)

        val deferred = HealthConnectPermissionRequest.next()
        val intent = Intent(context, HealthConnectPermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val canResolve = intent.resolveActivity(context.packageManager) != null
        if (!canResolve) {
            Log.e(TAG, "HealthConnectPermissionActivity cannot be resolved - check manifest")
            deferred.complete(false)
            return false
        }

        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.e(TAG, "failed to launch permission activity", it)
                deferred.complete(false)
            }

        return withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { deferred.await() } ?: false.also {
            Log.e(TAG, "permission request timed out")
        }
    }

    override suspend fun todaySteps(context: Context): DailySteps {
        val now = Instant.now()
        val date = LocalDate.now()
        val permissionResult = getHealthPermissionState(context)
        if (permissionResult.getOrNull() != HealthPermissionState.GRANTED) {
            return DailySteps(date = date, steps = 0, cachedAtMs = now.toEpochMilli())
        }
        val steps = runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                ),
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        }.onFailure { Log.e(TAG, "stepsRead failed", it) }.getOrDefault(0L)
        return DailySteps(date = date, steps = steps, cachedAtMs = now.toEpochMilli())
    }

    override suspend fun stepsInRange(
        context: Context,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Long> {
        val permissionResult = getHealthPermissionState(context)
        if (permissionResult.getOrNull() != HealthPermissionState.GRANTED) return emptyMap()
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            buildMap {
                var day = from
                while (!day.isAfter(to)) {
                    val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val result = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(start, end),
                        ),
                    )
                    put(day, result[StepsRecord.COUNT_TOTAL] ?: 0L)
                    day = day.plusDays(1)
                }
            }
        }.onFailure { Log.e(TAG, "stepsInRange failed", it) }.getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "DayToDayHC"
        const val PERMISSION_TIMEOUT_MS = 60_000L
    }
}
