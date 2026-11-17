package com.daytoday.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Why vitals cannot be shown at all. */
enum class VitalsUnavailable {
    /** Health Connect is not installed (or needs an update). */
    NOT_INSTALLED,

    /** The device does not support Health Connect. */
    UNSUPPORTED,
}

sealed interface VitalsLoad {
    data class Ready(val snapshot: VitalsSnapshot) : VitalsLoad

    /** Nothing granted yet; the UI offers to ask. */
    data object NeedsPermission : VitalsLoad

    data class Unavailable(val reason: VitalsUnavailable) : VitalsLoad

    data class Failed(val message: String) : VitalsLoad
}

/** Thin boundary around Health Connect so the repository logic can be tested without Android. */
interface VitalsSource {
    suspend fun availability(): VitalsUnavailable?

    suspend fun grantedKinds(): Set<VitalsKind>

    suspend fun weights(from: Instant, to: Instant): List<WeightReading>

    suspend fun sleep(from: Instant, to: Instant): List<SleepInterval>

    suspend fun heartRate(from: Instant, to: Instant): List<HeartRateSample>
}

interface VitalsRepository {
    suspend fun load(days: Int = DEFAULT_DAYS): VitalsLoad

    companion object {
        const val DEFAULT_DAYS = 7
    }
}

/**
 * Reads weight, sleep and heart rate for the last few days. Each kind is only requested when
 * its permission is granted, so a partial grant still shows what it can. Data stays on the
 * device: the server has no endpoint for these values.
 */
class VitalsRepositoryImpl(
    private val source: VitalsSource,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = Instant::now,
) : VitalsRepository {

    override suspend fun load(days: Int): VitalsLoad {
        source.availability()?.let { return VitalsLoad.Unavailable(it) }
        return try {
            val granted = source.grantedKinds()
            if (granted.isEmpty()) return VitalsLoad.NeedsPermission
            val z = zone()
            val nowInstant = now()
            val window = days.coerceIn(1, 31)
            val today = nowInstant.atZone(z).toLocalDate()
            val from = today.minusDays((window - 1).toLong()).atStartOfDay(z).toInstant()
            // Sleep that ended this morning started the previous evening.
            val sleepFrom = from.minusSeconds(SLEEP_LOOKBACK_SECONDS)
            val weights = if (VitalsKind.WEIGHT in granted) source.weights(from, nowInstant) else emptyList()
            val sleep = if (VitalsKind.SLEEP in granted) source.sleep(sleepFrom, nowInstant) else emptyList()
            val hr = if (VitalsKind.HEART_RATE in granted) source.heartRate(from, nowInstant) else emptyList()
            VitalsLoad.Ready(VitalsAggregator.snapshot(today, window, weights, sleep, hr, granted, z))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VitalsLoad.Failed(e.message ?: "Could not read Health Connect")
        }
    }

    private companion object {
        const val SLEEP_LOOKBACK_SECONDS = 12L * 60 * 60
    }
}
