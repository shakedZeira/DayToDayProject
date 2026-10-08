package com.daytoday.ui.screen.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.settings.GoalEntry
import com.daytoday.settings.GoalPeriod
import com.daytoday.settings.GoalType
import com.daytoday.settings.CUSTOM_TARGET_MAX
import com.daytoday.settings.MAX_CUSTOM_GOAL_LENGTH
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.generateGoalId
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.usecase.LocalDayStats
import com.daytoday.usecase.LocalSummaryUseCases
import com.daytoday.usecase.LocalWeekStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
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
                val entries = applyWeeklyResets(settingsManager.goals.first())
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
        if (type == GoalType.CUSTOM) {
            // Custom goals carry free text instead of a numeric target.
            return
        }
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

    fun addCustomGoal(label: String, target: Double, period: GoalPeriod) {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CUSTOM_GOAL_LENGTH) return
        viewModelScope.launch {
            val entries = settingsManager.goals.first()
            val safeTarget = target.coerceIn(1.0, CUSTOM_TARGET_MAX.toDouble())
            val entry = GoalEntry(
                id = generateGoalId(GoalType.CUSTOM, entries.map { it.id }),
                type = GoalType.CUSTOM,
                target = safeTarget,
                label = trimmed,
                period = period,
                progress = 0.0,
                lastResetWeekStart = if (period == GoalPeriod.WEEK) weekStart() else "",
            )
            settingsManager.setGoals(entries + entry)
            refresh()
        }
    }

    fun updateCustomGoal(id: String, label: String, target: Double, period: GoalPeriod) {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_CUSTOM_GOAL_LENGTH) return
        viewModelScope.launch {
            val anchor = weekStart()
            val entries = settingsManager.goals.first()
            settingsManager.setGoals(
                entries.map { entry ->
                    if (entry.id != id) {
                        entry
                    } else {
                        weeklyReset(
                            entry.copy(
                                label = trimmed,
                                target = target.coerceIn(1.0, CUSTOM_TARGET_MAX.toDouble()),
                                period = period,
                            ),
                            anchor,
                        )
                    }
                }
            )
            refresh()
        }
    }

    fun incrementCustomProgress(id: String) = changeCustomProgress(id, +1.0)

    fun decrementCustomProgress(id: String) = changeCustomProgress(id, -1.0)

    private fun changeCustomProgress(id: String, delta: Double) {
        viewModelScope.launch {
            val anchor = weekStart()
            val entries = settingsManager.goals.first()
            settingsManager.setGoals(
                entries.map { entry ->
                    if (entry.id == id && entry.type == GoalType.CUSTOM) {
                        val reset = weeklyReset(entry, anchor)
                        val upper = (reset.target * 2).coerceAtLeast(1.0)
                        reset.copy(
                            progress = floor((reset.progress + delta).coerceIn(0.0, upper))
                        )
                    } else {
                        entry
                    }
                }
            )
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

    /** Sunday 00:00 anchor of the current week (week runs Sunday -> Saturday). */
    private fun weekStart(): String =
        LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .toString()

    /**
     * WEEK-period custom goals zero their counter when the stored anchor is a
     * different Sunday than today's; an empty anchor (legacy entry) is only
     * recorded, never zeroed. DAY-period goals never auto-reset.
     */
    private fun weeklyReset(entry: GoalEntry, anchor: String): GoalEntry {
        if (entry.type != GoalType.CUSTOM || entry.period != GoalPeriod.WEEK) return entry
        return when {
            entry.lastResetWeekStart.isEmpty() -> entry.copy(lastResetWeekStart = anchor)
            entry.lastResetWeekStart != anchor ->
                entry.copy(progress = 0.0, lastResetWeekStart = anchor)
            else -> entry
        }
    }

    private suspend fun applyWeeklyResets(entries: List<GoalEntry>): List<GoalEntry> {
        val anchor = weekStart()
        val updated = entries.map { weeklyReset(it, anchor) }
        if (updated != entries) {
            settingsManager.setGoals(updated)
        }
        return updated
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
            GoalType.CUSTOM -> 0.0
        }
}
