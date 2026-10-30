package com.daytoday.data.health

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.daytoday.repository.Result
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads step counts from Health Connect (which is where Samsung Health writes its data).
 *
 * Samsung Health shows its own step total, while an unfiltered Health Connect
 * aggregate is priority-deduped across every app that writes steps (including
 * on-device step sensors). To match the number the user sees in Samsung Health,
 * reads are filtered to the Samsung Health [DataOrigin] once one is discovered
 * at runtime. If discovery finds nothing the aggregate stays unfiltered rather
 * than reporting zero, so on-device steps Health Connect has not merged are
 * included in that fallback.
 *
 * Degrades silently: if the SDK is missing, permission is not granted, or a read throws,
 * callers get zero-valued data instead of an exception.
 */
@Singleton
class HealthRepositoryImpl @Inject constructor() : HealthRepository {

    /** Resolved Samsung Health data origin; null until discovery succeeds. */
    @Volatile
    private var cachedOriginPackage: String? = null

    /** Last discovery attempt, used to throttle repeated negative scans. */
    private var lastOriginScanMs = 0L

    /** Serializes discovery so parallel reads do not scan records concurrently. */
    private val originMutex = Mutex()

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

    override suspend fun hasBackgroundReadPermission(context: Context): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        } catch (e: Exception) {
            Log.w(TAG, "hasBackgroundReadPermission failed", e)
            false
        }
    }

    override suspend fun requestBackgroundReadPermission(context: Context): Boolean {
        // Try to request just the background permission if possible
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
        val zone = ZoneId.systemDefault()
        // Derive the date from the same instant as `now`; capturing LocalDate.now()
        // separately can cross midnight and invert the aggregation window.
        val date = now.atZone(zone).toLocalDate()
        val permissionResult = getHealthPermissionState(context)
        if (permissionResult.getOrNull() != HealthPermissionState.GRANTED) {
            return DailySteps(date = date, steps = 0, cachedAtMs = now.toEpochMilli())
        }
        val window = stepWindow(from = date, to = date, today = date, now = now, zone = zone)
            ?: return DailySteps(date = date, steps = 0, cachedAtMs = now.toEpochMilli())
        val steps = try {
            val client = HealthConnectClient.getOrCreate(context)
            val origin = resolveOriginPackage(client, now)
            aggregateSteps(client, window, origin)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "stepsRead failed", e)
            0L
        }
        return DailySteps(date = date, steps = steps, cachedAtMs = now.toEpochMilli())
    }

    override suspend fun stepsInRange(
        context: Context,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Long> {
        if (to.isBefore(from)) return emptyMap()
        val permissionResult = getHealthPermissionState(context)
        if (permissionResult.getOrNull() != HealthPermissionState.GRANTED) return emptyMap()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val origin = resolveOriginPackage(client, now)
            buildMap {
                var day = from
                while (!day.isAfter(to)) {
                    val window = stepWindow(from = day, to = day, today = today, now = now, zone = zone)
                    put(day, window?.let { aggregateSteps(client, it, origin) } ?: 0L)
                    day = day.plusDays(1)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "stepsInRange failed", e)
            emptyMap()
        }
    }

    override suspend fun totalSteps(
        context: Context,
        from: LocalDate,
        to: LocalDate,
    ): Long {
        if (to.isBefore(from)) return 0L
        val permissionResult = getHealthPermissionState(context)
        if (permissionResult.getOrNull() != HealthPermissionState.GRANTED) return 0L
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        // stepWindow caps at `now` when the range includes today or future days;
        // aggregating past the current instant is meaningless.
        val window = stepWindow(from = from, to = to, today = today, now = now, zone = zone)
            ?: return 0L
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val origin = resolveOriginPackage(client, now)
            aggregateSteps(client, window, origin)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "totalSteps failed", e)
            0L
        }
    }

    /**
     * Resolves the Health Connect data origin that should back step reads.
     *
     * Prefers Samsung Health's package and caches the result for the lifetime of
     * this singleton. When nothing is found the aggregate falls back to
     * unfiltered (an empty [StepAggregateSpec.dataOriginFilter]); a failed scan
     * is retried after [ORIGIN_RESCAN_INTERVAL_MS] so records synced later in
     * the process are still picked up.
     */
    private suspend fun resolveOriginPackage(
        client: HealthConnectClient,
        now: Instant,
    ): String? {
        cachedOriginPackage?.let { return it }
        return originMutex.withLock {
            cachedOriginPackage?.let { return@withLock it }
            if (now.toEpochMilli() - lastOriginScanMs < ORIGIN_RESCAN_INTERVAL_MS) {
                return@withLock null
            }
            lastOriginScanMs = now.toEpochMilli()
            val discovered = try {
                discoverSamsungHealthOrigin(client, now)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "dataOrigin discovery failed", e)
                null
            }
            discovered?.also { cachedOriginPackage = it }
        }
    }

    /**
     * Reads a small slice of recent [StepsRecord]s and returns the Samsung Health
     * package that contributed to them. Health Connect exposes no list of data
     * origins, so the records themselves are the discovery mechanism.
     */
    private suspend fun discoverSamsungHealthOrigin(
        client: HealthConnectClient,
        now: Instant,
    ): String? {
        // First try to find by looking at recent records
        val response = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        now.minusSeconds(ORIGIN_LOOKBACK_SECONDS),
                        now,
                    ),
                    ascendingOrder = false,
                    pageSize = ORIGIN_DISCOVERY_PAGE_SIZE,
                ),
            )
        }.getOrNull()

        val packagesFromRecords = response?.records?.mapTo(mutableSetOf()) { it.metadata.dataOrigin.packageName }.orEmpty()
        val fromRecords = SAMSUNG_HEALTH_PACKAGES.firstOrNull { it in packagesFromRecords }
        if (fromRecords != null) return fromRecords

        // Fallback: test each candidate origin with an aggregate to see if it has data
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val weekStart = today.minusDays(6)
        val window = stepWindow(from = weekStart, to = today, today = today, now = now, zone = zone)
            ?: return null

        for (pkg in SAMSUNG_HEALTH_PACKAGES) {
            val result = runCatching {
                val agg = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                        dataOriginFilter = setOf(DataOrigin(pkg)),
                    ),
                )
                agg[StepsRecord.COUNT_TOTAL] ?: 0L
            }.getOrDefault(0L)
            if (result > 0) {
                return pkg
            }
        }
        return null
    }

    /** Runs one aggregate, filtered to [originPackage] when it is not null. */
    private suspend fun aggregateSteps(
        client: HealthConnectClient,
        window: StepWindow,
        originPackage: String?,
    ): Long {
        if (originPackage != null) {
            val filtered = try {
                val result = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                        dataOriginFilter = setOf(DataOrigin(originPackage)),
                    ),
                )
                result[StepsRecord.COUNT_TOTAL] ?: 0L
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                -1L
            }
            if (filtered > 0) return filtered
            if (filtered == 0L) {
                // Try unfiltered as self-healing - cache might be stale
                val unfiltered = try {
                    val result = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                            dataOriginFilter = emptySet(),
                        ),
                    )
                    result[StepsRecord.COUNT_TOTAL] ?: 0L
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    0L
                }
                if (unfiltered > 0) {
                    // Clear stale cache
                    cachedOriginPackage = null
                    return unfiltered
                }
            }
            if (filtered == -1L) {
                // fell through to error, try unfiltered
                val unfiltered = try {
                    val result = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                            dataOriginFilter = emptySet(),
                        ),
                    )
                    result[StepsRecord.COUNT_TOTAL] ?: 0L
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    0L
                }
                return unfiltered
            }
            return 0L
        }
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                dataOriginFilter = emptySet(),
            ),
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    private companion object {
        const val TAG = "DayToDayHC"
        const val PERMISSION_TIMEOUT_MS = 60_000L

        /**
         * Samsung Health's data origin, then the Samsung Health platform service.
         * On-device sensor origins are deliberately not listed: they contain
         * steps Samsung Health has not merged, so preferring them would show a
         * number the user does not recognise.
         */
        val SAMSUNG_HEALTH_PACKAGES = listOf(
            "com.sec.android.app.shealth",
            "com.samsung.android.service.health",
        )

        /** How far back discovery looks for a Samsung Health step record. */
        const val ORIGIN_LOOKBACK_SECONDS = 48L * 60 * 60

        /** Recent records are enough to identify the origin; no paging needed. */
        const val ORIGIN_DISCOVERY_PAGE_SIZE = 200

        /** Retry a failed discovery after this long instead of on every read. */
        const val ORIGIN_RESCAN_INTERVAL_MS = 5L * 60 * 1000
    }
}

/** Inclusive local-day range reduced to the instants actually sent to Health Connect. */
internal data class StepWindow(
    val start: Instant,
    val end: Instant,
)

/**
 * Describes one step aggregation. Kept free of Android/Health Connect types so
 * the window and origin-filter rules can be unit tested on the JVM.
 */
internal data class StepAggregateSpec(
    val window: StepWindow,
    /** Empty means "every data origin", which is the safe fallback. */
    val dataOriginFilter: Set<String>,
)

/**
 * Builds the aggregation window for [from]..[to] (inclusive local days).
 *
 * The window always starts at local midnight of [from]. When [to] is today or a
 * future date it ends at [now] rather than at the next midnight: aggregating
 * past the current instant is meaningless, and using tomorrow 00:00 for a
 * "week start..today" range inflated the weekly total with hours that had not
 * happened yet. Returns null when the range is inverted or starts in the
 * future, so callers can return zero without issuing an invalid request.
 */
internal fun stepWindow(
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
): StepWindow? {
    if (to.isBefore(from)) return null
    val start = from.atStartOfDay(zone).toInstant()
    val end = if (to.isBefore(today)) {
        to.plusDays(1).atStartOfDay(zone).toInstant()
    } else {
        now
    }
    return if (end.isAfter(start)) StepWindow(start, end) else null
}

/**
 * Applies the resolved Samsung Health [dataOriginPackage] to [window]. A null
 * origin leaves [StepAggregateSpec.dataOriginFilter] empty, i.e. unfiltered.
 */
internal fun stepAggregateSpec(
    window: StepWindow,
    dataOriginPackage: String?,
): StepAggregateSpec = StepAggregateSpec(
    window = window,
    dataOriginFilter = dataOriginPackage?.let { setOf(it) } ?: emptySet(),
)
