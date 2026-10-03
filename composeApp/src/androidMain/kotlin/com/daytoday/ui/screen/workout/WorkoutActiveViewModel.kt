package com.daytoday.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.usecase.WorkoutUseCases
import com.daytoday.model.Exercise
import com.daytoday.model.SetRecord
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import com.daytoday.util.CalorieCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlin.math.roundToInt

@HiltViewModel
class WorkoutActiveViewModel @Inject constructor(
    private val workoutUseCases: WorkoutUseCases,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _activeSession = MutableStateFlow<WorkoutSession?>(null)
    val activeSession: StateFlow<WorkoutSession?> = _activeSession

    private val _exercises = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val exercises: StateFlow<UiState<List<Exercise>>> = _exercises

    private val _builderSessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val builderSessions: StateFlow<List<WorkoutSession>> = _builderSessions

    private val _building = MutableStateFlow(false)
    val building: StateFlow<Boolean> = _building

    init {
        loadExercises()
    }

    private fun loadExercises() {
        viewModelScope.launch {
            _exercises.value = UiState.Loading
            val result = workoutRepository.getExercises()
            _exercises.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> UiState.Error(result.error)
            }
        }
    }

    fun retryLoadExercises() {
        loadExercises()
    }

    // ---------- Build-mode: pick exercises into a saved workout plan ----------

    fun startBuilding() {
        _building.value = true
    }

    fun stopBuilding() {
        _building.value = false
    }

    fun addExerciseToBuilder(exercise: Exercise) {
        if (_building.value) {
            val session = WorkoutSession(
                id = "wks_builder_${UUID.randomUUID()}",
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                startTime = 0,
                endTime = null,
                durationMinutes = 0,
                caloriesBurned = 0,
                sets = listOf(
                    SetRecord(
                        id = UUID.randomUUID().toString(),
                        sessionId = "",
                        setNumber = 1,
                        weight = 0.0,
                        reps = 0,
                        rpe = null,
                        isCompleted = false,
                        completedAt = null
                    )
                ),
                isCompleted = false
            )
            _builderSessions.value = _builderSessions.value + session
        }
    }

    fun removeFromBuilder(index: Int) {
        val current = _builderSessions.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _builderSessions.value = current
        }
    }

    fun saveBuilderAsWorkoutPlan() {
        viewModelScope.launch {
            // Persist each builder session to Room as a saved workout plan.
            // They are incomplete (startTime=0) so they won't show as completed.
            val sessions = _builderSessions.value
            if (sessions.isEmpty()) return@launch
            sessions.forEach { session ->
                workoutRepository.saveSession(session)
            }
        }
    }

    fun startWorkoutFromBuilder(exercise: Exercise) {
        viewModelScope.launch {
            val session = workoutUseCases.startWorkout(exercise.id).copy(
                exerciseName = exercise.name
            )
            _activeSession.value = session
            stopBuilding()
            _builderSessions.value = emptyList()
        }
    }

    fun startWorkout(exercise: Exercise) {
        viewModelScope.launch {
            val session = workoutUseCases.startWorkout(exercise.id).copy(
                exerciseName = exercise.name
            )
            _activeSession.value = session
        }
    }

    // ---------- Active workout ----------

    fun onBackPressed() {
        if (_activeSession.value != null) {
            _activeSession.value = null
        }
    }

    fun addSet() {
        _activeSession.value?.let { session ->
            val newSet = SetRecord(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                setNumber = session.sets.size + 1,
                weight = 0.0,
                reps = 0,
                rpe = null,
                isCompleted = false,
                completedAt = null
            )
            val updatedSets = session.sets + newSet
            _activeSession.value = session.copy(sets = updatedSets)
        }
    }

    fun updateSetWeight(setIndex: Int, weight: Double) {
        _activeSession.value?.let { session ->
            if (setIndex < session.sets.size) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(weight = weight)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun updateSetReps(setIndex: Int, reps: Int) {
        _activeSession.value?.let { session ->
            if (setIndex < session.sets.size) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(reps = reps)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun updateSetRpe(setIndex: Int, rpe: Double?) {
        _activeSession.value?.let { session ->
            if (setIndex < session.sets.size) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(rpe = rpe)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun completeSet(setIndex: Int) {
        _activeSession.value?.let { session ->
            if (setIndex < session.sets.size) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(
                    isCompleted = true,
                    completedAt = System.currentTimeMillis()
                )
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun completeWorkout() {
        _activeSession.value?.let { session ->
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val completedSession = session.copy(
                    isCompleted = true,
                    endTime = now,
                    durationMinutes = ((now - session.startTime) / 1000 / 60).toInt(),
                    // Calculate calories from weight, reps, and exercise type.
                    caloriesBurned = calculateCaloriesBurned(session)
                )
                val result = workoutUseCases.completeWorkout(completedSession)
                if (result is Result.Success) {
                    _activeSession.value = completedSession
                }
            }
        }
    }

    /** Derive calories burned from completed sets: weight × reps × per-rep factor,
     *  scaled by the user's body weight relative to the reference weight.
     *
     *  Uses the same per-rep coefficients as [CalorieCalculator] so the numbers shown
     *  on the home dashboard (workoutCalories) match what the workout screen reports. */
    private fun calculateCaloriesBurned(session: WorkoutSession): Int {
        var kcal = 0.0
        session.sets.forEach { set ->
            if (!set.isCompleted || set.reps <= 0) return@forEach
            val kcalPerRep = kcalPerRepFor(session.exerciseName)
            kcal += kcalPerRep * set.reps
        }
        if (kcal <= 0.0) return 0
        // Weight scaling: heavier users burn more per rep.
        // Default to 70 kg; a full impl reads the saved profile from SettingsManager.
        val weightKg = 70.0
        val scaled = kcal * (weightKg / 70.0)
        return scaled.roundToInt()
    }

    private fun kcalPerRepFor(exerciseName: String): Double {
        val name = exerciseName.lowercase()
        return when {
            name.contains("pull") -> 0.60
            name.contains("push") -> 0.45
            name.contains("squat") -> 0.30
            else -> 0.30
        }
    }
}