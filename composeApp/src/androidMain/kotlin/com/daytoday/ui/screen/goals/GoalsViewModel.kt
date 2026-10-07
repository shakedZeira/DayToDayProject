package com.daytoday.ui.screen.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalType
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.generateGoalId
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalProgress(
    val entry: GoalEntry,
    val current: Double,
)

data class GoalsUiState(
    val goals: List<GoalProgress> = emptyList(),
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val localSummaryUseCases: LocalSummaryUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GoalsUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GoalsUiState>> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                settingsManager.seedGoalsIfAbsent()
                val entries = settingsManager.goals.first()
                val day = localSummaryUseCases.todayStats()
                val week = localSummaryUseCases.weekStats()
                UiState.Success(
                    GoalsUiState(
                        goals = entries.map { entry ->
                            GoalProgress(
                                entry = entry,
                                current = currentValue(entry.type, day, week),
                            )
                        }
                    )
                )
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unable to load goals")
            }
        }
    }

    fun addGoal(type: GoalType, target: Double) {
        viewModelScope.launch {
            val entries = settingsManager.goals.first()
            val entry = GoalEntry(
                id = generateGoalId(type, entries.map { it.id }),
                type = type,
                target = target,
            )
            settingsManager.setGoals(entries + entry)
            refresh()
        }
    }

    fun updateGoalTarget(id: String, target: Double) {
        viewModelScope.launch {
            val entries = settingsManager.goals.first()
            settingsManager.setGoals(
                entries.map { if (it.id == id) it.copy(target = target) else it }
            )
            refresh()
        }
    }

    fun removeGoal(id: String) {
        viewModelScope.launch {
            val entries = settingsManager.goals.first()
            settingsManager.setGoals(entries.filterNot { it.id == id })
            refresh()
        }
    }

    private fun currentValue(type: GoalType, day: LocalDayStats, week: LocalWeekStats): Double =
        when (type) {
            GoalType.CALORIES_TODAY -> day.totalCalories.toDouble()
            GoalType.WORKOUTS_PER_WEEK -> week.workouts.toDouble()
            GoalType.STEPS_TODAY -> day.steps.toDouble()
            GoalType.ACTIVE_MINUTES_TODAY -> day.durationMinutes.toDouble()
            GoalType.WORKOUT_MINUTES_PER_WEEK -> week.durationMinutes.toDouble()
            GoalType.CALORIES_BURNED_PER_WEEK -> week.caloriesBurned.toDouble()
            GoalType.LIFTING_VOLUME_PER_WEEK -> week.volume
        }
}
