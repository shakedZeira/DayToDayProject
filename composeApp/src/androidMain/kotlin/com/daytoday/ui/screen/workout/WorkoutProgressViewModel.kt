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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
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
            val weekStart = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val result = workoutUseCases.getWeeklyProgress(weekStart)
            _weeklyProgress.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> {
                    // Network unavailable — build a local weekly summary from Room sessions.
                    val localWeek = computeLocalWeeklyProgress()
                    UiState.Success(localWeek)
                }
            }
        }
    }

    /** Local approximation of weekly progress from Room sessions. */
    private suspend fun computeLocalWeeklyProgress(): WeeklyProgress {
        val sessionsResult = workoutUseCases.getSessions(50, 0)
        val sessions = (sessionsResult as? Result.Success)?.data ?: emptyList()
        val weekStart = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .minus(7, DateTimeUnit.DAY)
            .toString()
        val weekStartLong = try {
            java.time.LocalDate.parse(weekStart)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }

        val inWeek = sessions.filter { it.startTime >= weekStartLong }
        val volume = inWeek.sumOf { session ->
            session.sets.filter { it.isCompleted }.sumOf { it.weight * it.reps }
        }

        return WeeklyProgress(
            weekStart = weekStartLong,
            workouts = inWeek.size,
            durationMinutes = inWeek.sumOf { it.durationMinutes },
            caloriesBurned = inWeek.sumOf { it.caloriesBurned },
            volume = volume,
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

