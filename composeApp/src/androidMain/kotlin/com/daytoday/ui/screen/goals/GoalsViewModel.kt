package com.daytoday.ui.screen.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.settings.SettingsManager
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.usecase.LocalSummaryUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val caloriesToday: Int = 0,
    val caloriesGoal: Int = 500,
    val workoutsThisWeek: Int = 0,
    val weeklyWorkoutGoal: Int = 2,
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val localSummaryUseCases: LocalSummaryUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<GoalsUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GoalsUiState>> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val today = localSummaryUseCases.todayStats()
                val week = localSummaryUseCases.weekStats()
                UiState.Success(
                    GoalsUiState(
                        caloriesToday = today.totalCalories,
                        caloriesGoal = settingsManager.caloriesGoal.first(),
                        workoutsThisWeek = week.workouts,
                        weeklyWorkoutGoal = settingsManager.weeklyWorkoutGoal.first(),
                    )
                )
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Unable to load goals")
            }
        }
    }

    fun updateCaloriesGoal(goal: Int) {
        viewModelScope.launch {
            settingsManager.setCaloriesGoal(goal)
            load()
        }
    }

    fun updateWeeklyWorkoutGoal(goal: Int) {
        viewModelScope.launch {
            settingsManager.setWeeklyWorkoutGoal(goal)
            load()
        }
    }
}
