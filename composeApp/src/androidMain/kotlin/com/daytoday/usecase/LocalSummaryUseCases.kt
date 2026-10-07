package com.daytoday.usecase

import android.content.Context
import com.daytoday.data.health.HealthRepository
import com.daytoday.data.health.HealthPermissionState
import com.daytoday.data.health.HealthSdkStatus
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.WorkoutRepository
import com.daytoday.settings.SettingsManager
import com.daytoday.util.CalorieCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Everything the home screen needs, computed entirely on-device.
 *
 * Steps come from Health Connect (which is where Samsung Health writes its data) and
 * calories are derived locally by [CalorieCalculator]. Nothing here touches the network,
 * so the screen keeps working while `https://api.daytoday.app/` is unreachable.
 */
data class LocalDayStats(
    val steps: Long = 0,
    val stepsCalories: Int = 0,
    val workoutCalories: Int = 0,
    val totalCalories: Int = 0,
    val calorieGoal: Int = 0,
    val completedSessions: Int = 0,
    val durationMinutes: Int = 0,
    val totalVolume: Double = 0.0,
)

data class LocalWeekStats(
    val weekStart: Long = 0,
    val workouts: Int = 0,
    val durationMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    val volume: Double = 0.0,
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

    suspend fun todayStats(): LocalDayStats = withContext(Dispatchers.IO) {
        val profile = runCatching { settingsManager.userProfile.first() }.getOrNull()
        val weightKg = profile?.weightKg ?: CalorieCalculator.DEFAULT_WEIGHT_KG

        val steps = runCatching { healthRepository.todaySteps(context).steps }.getOrDefault(0L)

        val sessions = workoutRepository.getSessions(SESSION_SCAN_LIMIT, 0).getOrElse(emptyList())
        val completedToday = sessions.filter { it.isCompleted && isToday(it.startTime) }

        val stepsCalories = CalorieCalculator.stepsCalories(steps, weightKg).roundToInt()
        val workoutCalories = CalorieCalculator.workoutCalories(weightKg, completedToday).roundToInt()

        LocalDayStats(
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
        )
    }

    suspend fun weekStats(): LocalWeekStats = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val startMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val sessions = workoutRepository.getSessions(SESSION_SCAN_LIMIT, 0).getOrElse(emptyList())
        val inWeek = sessions.filter { it.startTime >= startMillis && it.startTime < endMillis }

        LocalWeekStats(
            weekStart = startMillis,
            workouts = inWeek.size,
            durationMinutes = inWeek.sumOf { it.durationMinutes },
            caloriesBurned = inWeek.sumOf { it.caloriesBurned },
            volume = inWeek.sumOf { session -> volumeOf(session) },
        )
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

    suspend fun healthSdkStatus(): HealthSdkStatus = withContext(Dispatchers.IO) {
        runCatching { healthRepository.isSdkAvailable(context) }
            .getOrDefault(HealthSdkStatus.UNAVAILABLE)
    }

    private fun volumeOf(session: WorkoutSession): Double =
        session.sets.filter { it.isCompleted }.sumOf { it.weight * it.reps }

    private fun isToday(epochMillis: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return day == LocalDate.now(zone)
    }

    private companion object {
        const val SESSION_SCAN_LIMIT = 50
        const val DEFAULT_HEIGHT_CM = 175.0
        const val DEFAULT_AGE_YEARS = 30
    }
}
