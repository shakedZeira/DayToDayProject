package com.daytoday.usecase

import android.content.Context
import android.util.Log
import com.daytoday.data.health.HealthRepository
import com.daytoday.data.health.HealthPermissionState
import com.daytoday.data.health.HealthSdkStatus
import com.daytoday.data.notification.SummaryStatsCache
import com.daytoday.model.UserProfile
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.WorkoutRepository
import com.daytoday.settings.SettingsManager
import com.daytoday.util.CalorieCalculator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * Everything the home screen needs, computed entirely on-device.
 *
 * Steps come from Health Connect (which is where Samsung Health writes its data) and
 * calories are derived locally by [CalorieCalculator]. Nothing here touches the network,
 * so the screen keeps working while `https://api.daytoday.app/` is unreachable.
 */
@Serializable
data class LocalDayStats(
    val steps: Long = 0,
    val stepsCalories: Int = 0,
    val workoutCalories: Int = 0,
    val totalCalories: Int = 0,
    val calorieGoal: Int = 0,
    val completedSessions: Int = 0,
    val durationMinutes: Int = 0,
    val totalVolume: Double = 0.0,
    /** False when the step read could not run (no background permission, timeout, error); a 0 is then unknown, not real. */
    val stepsReadable: Boolean = true,
)

@Serializable
data class LocalWeekStats(
    val weekStart: Long = 0,
    val workouts: Int = 0,
    val durationMinutes: Int = 0,
    /** Combined burn: [workoutCalories] + [stepCalories]. */
    val caloriesBurned: Int = 0,
    val volume: Double = 0.0,
    val steps: Long = 0,
    val stepCalories: Int = 0,
    val workoutCalories: Int = 0,
    val stepsReadable: Boolean = true,
)

@Singleton
class LocalSummaryUseCases @Inject constructor(
    private val healthRepository: HealthRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context,
) {

    private val _healthPermissionGranted = MutableStateFlow(false)
    val healthPermissionGranted: StateFlow<Boolean> = _healthPermissionGranted.asStateFlow()

    /**
     * Warms the last-known-good summary cache from successful foreground reads.
     * Without this the cache would only ever be written while a notification is
     * being built, so the first background alarm after an update would have no
     * snapshot to fall back to when Health Connect denies a background read.
     * Lazy so unit tests that never produce steps do not touch Android prefs.
     */
    private val statsCache: SummaryStatsCache by lazy { SummaryStatsCache(context) }

    /**
     * Today's stats. Workout data is read from the Room cache first, so a slow or
     * failing Health Connect read can only zero the step-derived fields — it can
     * no longer cancel the workout part of the summary.
     */
    suspend fun todayStats(): LocalDayStats = withContext(Dispatchers.IO) {
        val profile = readProfile()
        val weightKg = profile?.weightKg ?: CalorieCalculator.DEFAULT_WEIGHT_KG

        val sessions = readCachedSessions()
        val completedToday = sessions.filter { it.isCompleted && isToday(it.startTime) }

        val workoutCalories = CalorieCalculator.workoutCalories(weightKg, completedToday).roundToInt()
        val steps = readTodaySteps()
        val stepsReadable = canReadSteps()
        val stepsCalories = CalorieCalculator.stepsCalories(steps, weightKg).roundToInt()

        val stats = LocalDayStats(
            steps = steps,
            stepsCalories = stepsCalories,
            workoutCalories = workoutCalories,
            totalCalories = stepsCalories + workoutCalories,
            calorieGoal = CalorieCalculator.dailyBurnGoalKcal(
                weightKg = weightKg,
                heightCm = profile?.heightCm ?: DEFAULT_HEIGHT_CM,
                ageYears = profile?.ageYears ?: DEFAULT_AGE_YEARS,
                isMale = profile?.isMale ?: true,
            ),
            completedSessions = completedToday.size,
            durationMinutes = completedToday.sumOf { it.durationMinutes },
            totalVolume = completedToday.sumOf { session -> volumeOf(session) },
            stepsReadable = stepsReadable,
        )
        if (stats.steps > 0) {
            cacheDay(stats)
        }
        stats
    }

    /**
     * This week's stats (Monday 00:00 → now), combining both burn sources the
     * user cares about: logged workout calories plus calories derived from
     * Health Connect steps over the same window. Nothing here touches the
     * server — the Prisma `Workout` model has no calories/duration columns, so
     * the device is the authoritative source for "calories burned per week".
     *
     * As in [todayStats], the workout totals are computed before the step read,
     * so a Health Connect timeout degrades only [LocalWeekStats.steps],
     * [LocalWeekStats.stepCalories] and [LocalWeekStats.caloriesBurned].
     */
    suspend fun weekStats(): LocalWeekStats = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val startMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val profile = readProfile()
        val weightKg = profile?.weightKg ?: CalorieCalculator.DEFAULT_WEIGHT_KG

        val sessions = readCachedSessions()
        val inWeek = sessions.filter { it.startTime >= startMillis && it.startTime < endMillis }

        // Prefer the calories recomputed from completed sets (same math the daily
        // view uses); fall back to the stored value for sessions saved before the
        // calorie model existed or by a path that persisted 0.
        val storedWorkoutCalories = inWeek.sumOf { it.caloriesBurned }
        val derivedWorkoutCalories = CalorieCalculator
            .workoutCalories(weightKg, inWeek.filter { it.isCompleted })
            .roundToInt()
        val workoutCalories = maxOf(storedWorkoutCalories, derivedWorkoutCalories)

        val weekSteps = readWeekSteps(weekStart, today)
        val stepCalories = CalorieCalculator.stepsCalories(weekSteps, weightKg).roundToInt()

        val stats = LocalWeekStats(
            weekStart = startMillis,
            workouts = inWeek.size,
            durationMinutes = inWeek.sumOf { it.durationMinutes },
            caloriesBurned = workoutCalories + stepCalories,
            volume = inWeek.sumOf { session -> volumeOf(session) },
            steps = weekSteps,
            stepCalories = stepCalories,
            workoutCalories = workoutCalories,
            stepsReadable = canReadSteps(),
        )
        if (stats.steps > 0) {
            cacheWeek(stats, weekStart)
        }
        stats
    }

    /** Best-effort cache write; a failure must never break the stats read. */
    private fun cacheDay(stats: LocalDayStats) = try {
        statsCache.saveDay(stats, date = LocalDate.now(ZoneId.systemDefault()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "failed to cache day stats", e)
    }

    private fun cacheWeek(stats: LocalWeekStats, weekStart: LocalDate) = try {
        statsCache.saveWeek(stats, weekStart = weekStart)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "failed to cache week stats", e)
    }

    /** Re-reads the granted-permission set; call on resume, since the user may revoke it in settings. */
    suspend fun refreshHealthPermission(): Boolean = withContext(Dispatchers.IO) {
        val permissionResult = healthRepository.getHealthPermissionState(context)
        val granted = permissionResult.getOrNull() == HealthPermissionState.GRANTED
        _healthPermissionGranted.value = granted
        granted
    }

    fun setHealthPermissionGranted(granted: Boolean) {
        _healthPermissionGranted.value = granted
    }

    suspend fun requestHealthPermission(): Boolean {
        val granted = runCatching { healthRepository.requestStepsPermission(context) }
            .getOrDefault(false)
        _healthPermissionGranted.value = granted
        return granted
    }

    suspend fun hasBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { healthRepository.hasBackgroundReadPermission(context) }
            .getOrDefault(false)
    }

    suspend fun requestBackgroundReadPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { healthRepository.requestBackgroundReadPermission(context) }
            .getOrDefault(false)
    }

    suspend fun healthSdkStatus(): HealthSdkStatus = withContext(Dispatchers.IO) {
        runCatching { healthRepository.isSdkAvailable(context) }
            .getOrDefault(HealthSdkStatus.UNAVAILABLE)
    }

    private suspend fun readProfile(): UserProfile? = try {
        withTimeoutOrNull(PROFILE_TIMEOUT_MS) { settingsManager.userProfile.first() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "profile read failed, using defaults", e)
        null
    }

    /** Local-only session read: the summary must never wait on the network. */
    private suspend fun readCachedSessions(): List<WorkoutSession> = try {
        val result = workoutRepository.getCachedSessions(SESSION_SCAN_LIMIT, 0)
        result.getOrNull() ?: run {
            Log.w(TAG, "local session read failed: ${result.errorOrNull()}", result.exceptionOrNull())
            emptyList()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "local session read failed", e)
        emptyList()
    }

    /**
     * Health Connect rejects reads from a backgrounded app that lacks the background
     * permission, and the repository reports that as 0 steps. Only when this is true
     * is a returned 0 a real "no steps".
     */
    private suspend fun canReadSteps(): Boolean = try {
        val granted = healthRepository.getHealthPermissionState(context).getOrNull() ==
            HealthPermissionState.GRANTED
        granted && (appInForeground() || healthRepository.hasBackgroundReadPermission(context))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun appInForeground(): Boolean = withContext(Dispatchers.Main) {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private suspend fun readTodaySteps(): Long = try {
        withTimeoutOrNull(STEP_READ_TIMEOUT_MS) {
            healthRepository.todaySteps(context).steps
        } ?: 0L.also { Log.w(TAG, "today's step read timed out") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "today's step read failed", e)
        0L
    }

    private suspend fun readWeekSteps(weekStart: LocalDate, today: LocalDate): Long = try {
        withTimeoutOrNull(STEP_READ_TIMEOUT_MS) {
            healthRepository.totalSteps(context, weekStart, today)
        } ?: 0L.also { Log.w(TAG, "weekly step read timed out") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "weekly step read failed", e)
        0L
    }

    private fun volumeOf(session: WorkoutSession): Double =
        session.sets.filter { it.isCompleted }.sumOf { it.weight * it.reps }

    private fun isToday(epochMillis: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return day == LocalDate.now(zone)
    }

    private companion object {
        const val TAG = "LocalSummary"
        const val SESSION_SCAN_LIMIT = 50
        const val DEFAULT_HEIGHT_CM = 175.0
        const val DEFAULT_AGE_YEARS = 30

        /** Keeps the whole summary inside the receiver's `goAsync()` budget. */
        const val STEP_READ_TIMEOUT_MS = 2_500L

        /** DataStore is fast, but never let it block the notification. */
        const val PROFILE_TIMEOUT_MS = 1_500L
    }
}
