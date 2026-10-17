package com.daytoday.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.model.DailyProgress
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.repository.Result
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.WorkoutUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutProgressViewModel @Inject constructor(
    private val workoutUseCases: WorkoutUseCases,
    private val localSummaryUseCases: LocalSummaryUseCases,
) : ViewModel() {

    private val _dailyProgress = MutableStateFlow<UiState<DailyProgress>>(UiState.Loading)
    val dailyProgress: StateFlow<UiState<DailyProgress>> = _dailyProgress

    private val _personalBests = MutableStateFlow<UiState<List<PersonalBests>>>(UiState.Loading)
    val personalBests: StateFlow<UiState<List<PersonalBests>>> = _personalBests

    private val _weeklyProgress = MutableStateFlow<UiState<WeeklyProgress>>(UiState.Loading)
    val weeklyProgress: StateFlow<UiState<WeeklyProgress>> = _weeklyProgress

    init {
        loadAll()
    }

    fun loadAll() {
        loadDailyProgress()
        loadPersonalBests()
        loadWeeklyProgress()
    }

    private fun loadDailyProgress() {
        _dailyProgress.value = UiState.Loading
        viewModelScope.launch {
            // Local-first: compute today's progress from on-device data (Health Connect
            // steps + Room workout sessions). The network call is best-effort.
            val local = localSummaryUseCases.todayStats()
            val dateLong = todayInMillis()

            // Try the network for the enriched DailyProgress (heart rate, personal bests
            // served by the server). If it fails (SSL / offline), fall back to the local
            // computation so the screen always has something to show.
            val result = workoutUseCases.getDailyProgress()
            _dailyProgress.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> {
                    // Network unavailable — build a local DailyProgress from what we know.
                    val localProgress = DailyProgress(
                        date = dateLong,
                        steps = local.steps.toInt(),
                        activeCalories = local.stepsCalories,
                        workoutsCompleted = local.completedSessions,
                        totalDurationMinutes = local.durationMinutes,
                        caloriesBurned = local.totalCalories,
                        heartRateAvg = null,
                        personalBests = emptyList(),
                    )
                    UiState.Success(localProgress)
                }
            }
        }
    }

    private fun loadPersonalBests() {
        _personalBests.value = UiState.Loading
        viewModelScope.launch {
            val result = workoutUseCases.getPersonalBests()
            _personalBests.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> {
                    // No network — personal bests require server-side aggregation.
                    // Show empty list rather than an SSL error.
                    UiState.Success(emptyList())
                }
            }
        }
    }

    private fun loadWeeklyProgress() {
        _weeklyProgress.value = UiState.Loading
        viewModelScope.launch {
            val localWeek = computeLocalWeeklyProgress()
            val weekStart = java.time.Instant.ofEpochMilli(localWeek.weekStart)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val result = workoutUseCases.getWeeklyProgress(weekStart)
            val remoteWeek = (result as? Result.Success)?.data
            // The device is authoritative for calories (it alone can see Health
            // Connect steps, and the server's Workout model has no calories
            // column, so its caloriesBurned is always 0). Only fall back to the
            // server when the local store has no signal at all, and then only
            // for the fields the server actually knows (workouts/volume).
            val week = when {
                remoteWeek == null -> localWeek
                localWeek.workouts == 0 && localWeek.caloriesBurned == 0 -> remoteWeek
                else -> localWeek.copy(
                    workouts = localWeek.workouts.takeIf { it > 0 } ?: remoteWeek.workouts,
                    durationMinutes = localWeek.durationMinutes.takeIf { it > 0 }
                        ?: remoteWeek.durationMinutes,
                    volume = localWeek.volume.takeIf { it > 0.0 } ?: remoteWeek.volume,
                )
            }
            _weeklyProgress.value = UiState.Success(week)
        }
    }

    private suspend fun computeLocalWeeklyProgress(): WeeklyProgress {
        val week = localSummaryUseCases.weekStats()
        return WeeklyProgress(
            weekStart = week.weekStart,
            workouts = week.workouts,
            durationMinutes = week.durationMinutes,
            caloriesBurned = week.caloriesBurned,
            volume = week.volume,
            stepCalories = week.stepCalories,
        )
    }

    private fun todayInMillis(): Long {
        return try {
            java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}

