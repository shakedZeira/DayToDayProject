package com.daytoday.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.data.spotify.SpotifyPlayResult
import com.daytoday.data.spotify.SpotifyRepository
import com.daytoday.model.Exercise
import com.daytoday.model.PlanExercise
import com.daytoday.model.SetRecord
import com.daytoday.model.WorkoutPlan
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import com.daytoday.settings.SettingsManager
import com.daytoday.usecase.WorkoutUseCases
import com.daytoday.util.CalorieCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

enum class WorkoutScreenTab {
    BUILDER,
    SAVED_PLANS,
    QUICK_START,
    PROGRESS
}

@HiltViewModel
class WorkoutActiveViewModel @Inject constructor(
    private val workoutUseCases: WorkoutUseCases,
    private val workoutRepository: WorkoutRepository,
    private val settingsManager: SettingsManager,
    private val spotifyRepository: SpotifyRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _currentTab = MutableStateFlow(WorkoutScreenTab.BUILDER)
    val currentTab: StateFlow<WorkoutScreenTab> = _currentTab.asStateFlow()

    private val _activeSession = MutableStateFlow<WorkoutSession?>(null)
    val activeSession: StateFlow<WorkoutSession?> = _activeSession.asStateFlow()

    private val _activePlanQueue = MutableStateFlow<List<PlanExercise>>(emptyList())
    val activePlanQueue: StateFlow<List<PlanExercise>> = _activePlanQueue.asStateFlow()

    private val _activePlanIndex = MutableStateFlow(0)
    val activePlanIndex: StateFlow<Int> = _activePlanIndex.asStateFlow()

    private val _exercises = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val exercises: StateFlow<UiState<List<Exercise>>> = _exercises.asStateFlow()

    // Pre-selected exercises in workout builder
    private val _selectedExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val selectedExercises: StateFlow<List<Exercise>> = _selectedExercises.asStateFlow()

    // Configured exercises with per-exercise sets, reps, and weight for workout builder
    private val _builderExercises = MutableStateFlow<List<PlanExercise>>(emptyList())
    val builderExercises: StateFlow<List<PlanExercise>> = _builderExercises.asStateFlow()
    val builderPlanExercises: StateFlow<List<PlanExercise>> get() = builderExercises

    val savedPlans: StateFlow<List<WorkoutPlan>> = settingsManager.savedWorkoutPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile = settingsManager.userProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.daytoday.model.UserProfile())

    val spotifyLoggedIn: StateFlow<Boolean> = settingsManager.spotifyLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val spotifyPlaylistName: StateFlow<String?> = settingsManager.spotifyPlaylistName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _spotifyMessage = MutableStateFlow<String?>(null)
    val spotifyMessage: StateFlow<String?> = _spotifyMessage.asStateFlow()

    init {
        loadExercises()
    }

    fun setTab(tab: WorkoutScreenTab) {
        _currentTab.value = tab
    }

    fun loadExercises() {
        viewModelScope.launch {
            _exercises.value = UiState.Loading
            val result = workoutRepository.getExercises()
            _exercises.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> UiState.Error(result.error)
            }
        }
    }

    // ---------- Builder Actions ----------

    fun toggleExerciseSelection(exercise: Exercise) {
        val current = _selectedExercises.value
        if (current.any { it.id == exercise.id }) {
            _selectedExercises.value = current.filterNot { it.id == exercise.id }
            _builderExercises.value = _builderExercises.value.filterNot { it.exerciseId == exercise.id }
        } else {
            _selectedExercises.value = current + exercise
            val existing = _builderExercises.value.firstOrNull { it.exerciseId == exercise.id }
            val planEx = existing ?: PlanExercise(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                muscleGroup = exercise.muscleGroup,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = 0.0
            )
            _builderExercises.value = _builderExercises.value + planEx
        }
    }

    fun removeSelectedExercise(exerciseId: String) {
        _selectedExercises.value = _selectedExercises.value.filterNot { it.id == exerciseId }
        _builderExercises.value = _builderExercises.value.filterNot { it.exerciseId == exerciseId }
    }

    fun clearSelectedExercises() {
        _selectedExercises.value = emptyList()
        _builderExercises.value = emptyList()
    }

    fun updateExerciseSets(exerciseId: String, sets: Int) {
        val safeSets = maxOf(1, sets)
        _builderExercises.value = _builderExercises.value.map {
            if (it.exerciseId == exerciseId) it.copy(targetSets = safeSets) else it
        }
    }

    fun updateExerciseReps(exerciseId: String, reps: Int) {
        val safeReps = maxOf(1, reps)
        _builderExercises.value = _builderExercises.value.map {
            if (it.exerciseId == exerciseId) it.copy(targetReps = safeReps) else it
        }
    }

    fun updateExerciseWeight(exerciseId: String, weight: Double) {
        val safeWeight = maxOf(0.0, weight)
        _builderExercises.value = _builderExercises.value.map {
            if (it.exerciseId == exerciseId) it.copy(targetWeightKg = safeWeight) else it
        }
    }

    fun updateExercisePlanSets(exerciseId: String, sets: Int) = updateExerciseSets(exerciseId, sets)
    fun updateExercisePlanReps(exerciseId: String, reps: Int) = updateExerciseReps(exerciseId, reps)
    fun updateExercisePlanWeight(exerciseId: String, weightKg: Double) = updateExerciseWeight(exerciseId, weightKg)

    fun saveCurrentPlan(
        name: String,
        targetSets: Int = 3,
        targetReps: Int = 10,
        targetWeightKg: Double = 0.0
    ) {
        val configured = _builderExercises.value
        val planExercises = if (configured.isNotEmpty()) {
            configured
        } else {
            val selected = _selectedExercises.value
            if (selected.isEmpty()) return
            selected.map { ex ->
                PlanExercise(
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    muscleGroup = ex.muscleGroup,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    targetWeightKg = targetWeightKg
                )
            }
        }
        val plan = WorkoutPlan(
            id = "plan_${UUID.randomUUID()}",
            name = name.ifBlank { "Custom Workout Plan" },
            exercises = planExercises,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            settingsManager.saveWorkoutPlan(plan)
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            settingsManager.deleteWorkoutPlan(planId)
        }
    }

    // ---------- Starting Workouts ----------

    fun startPlan(plan: WorkoutPlan) {
        if (plan.exercises.isEmpty()) return
        _activePlanQueue.value = plan.exercises
        _activePlanIndex.value = 0
        startExerciseFromPlan(plan.exercises[0])
    }

    fun startSelectedAsWorkout(targetSets: Int = 3, targetReps: Int = 10) {
        val configured = _builderExercises.value
        val planExercises = if (configured.isNotEmpty()) {
            configured
        } else {
            val selected = _selectedExercises.value
            if (selected.isEmpty()) return
            selected.map { ex ->
                PlanExercise(
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    muscleGroup = ex.muscleGroup,
                    targetSets = targetSets,
                    targetReps = targetReps,
                    targetWeightKg = 0.0
                )
            }
        }
        _activePlanQueue.value = planExercises
        _activePlanIndex.value = 0
        startExerciseFromPlan(planExercises[0])
    }

    fun startSingleExercise(exercise: Exercise) {
        _activePlanQueue.value = emptyList()
        _activePlanIndex.value = 0
        val sessionId = UUID.randomUUID().toString()
        val initialSets = (1..3).map { setNumber ->
            SetRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                setNumber = setNumber,
                weight = 0.0,
                reps = 10,
                rpe = null,
                isCompleted = false,
                completedAt = null
            )
        }
        val session = WorkoutSession(
            id = sessionId,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            startTime = System.currentTimeMillis(),
            endTime = null,
            durationMinutes = 0,
            caloriesBurned = 0,
            sets = initialSets,
            isCompleted = false
        )
        _activeSession.value = session
    }

    private fun startExerciseFromPlan(planExercise: PlanExercise) {
        val sessionId = UUID.randomUUID().toString()
        val setCount = if (planExercise.targetSets > 0) planExercise.targetSets else 3
        val repCount = if (planExercise.targetReps > 0) planExercise.targetReps else 10
        val initialSets = (1..setCount).map { setNumber ->
            SetRecord(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                setNumber = setNumber,
                weight = planExercise.targetWeightKg,
                reps = repCount,
                rpe = null,
                isCompleted = false,
                completedAt = null
            )
        }
        val session = WorkoutSession(
            id = sessionId,
            exerciseId = planExercise.exerciseId,
            exerciseName = planExercise.exerciseName,
            startTime = System.currentTimeMillis(),
            endTime = null,
            durationMinutes = 0,
            caloriesBurned = 0,
            sets = initialSets,
            isCompleted = false
        )
        _activeSession.value = session
    }

    fun nextExerciseInPlan() {
        val currentSession = _activeSession.value ?: return
        val queue = _activePlanQueue.value
        val currentIndex = _activePlanIndex.value

        // Complete & save current exercise session
        saveCompletedSession(currentSession)

        val nextIndex = currentIndex + 1
        if (nextIndex < queue.size) {
            _activePlanIndex.value = nextIndex
            startExerciseFromPlan(queue[nextIndex])
        } else {
            // All exercises in plan finished!
            _activeSession.value = currentSession.copy(isCompleted = true)
        }
    }

    // ---------- Set and Session Logging ----------

    fun addSet() {
        _activeSession.value?.let { session ->
            val newSet = SetRecord(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                setNumber = session.sets.size + 1,
                weight = session.sets.lastOrNull()?.weight ?: 0.0,
                reps = session.sets.lastOrNull()?.reps ?: 10,
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
            if (setIndex in session.sets.indices) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(weight = weight)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun updateSetReps(setIndex: Int, reps: Int) {
        _activeSession.value?.let { session ->
            if (setIndex in session.sets.indices) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(reps = reps)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun updateSetRpe(setIndex: Int, rpe: Double?) {
        _activeSession.value?.let { session ->
            if (setIndex in session.sets.indices) {
                val updatedSets = session.sets.toMutableList()
                updatedSets[setIndex] = updatedSets[setIndex].copy(rpe = rpe)
                _activeSession.value = session.copy(sets = updatedSets)
            }
        }
    }

    fun completeSet(setIndex: Int) {
        _activeSession.value?.let { session ->
            if (setIndex in session.sets.indices) {
                val updatedSets = session.sets.toMutableList()
                val current = updatedSets[setIndex]
                updatedSets[setIndex] = current.copy(
                    isCompleted = !current.isCompleted,
                    completedAt = if (!current.isCompleted) System.currentTimeMillis() else null
                )
                val updated = session.copy(sets = updatedSets)
                _activeSession.value = updated.copy(caloriesBurned = calculateCalories(updated))
            }
        }
    }

    fun completeWorkout() {
        val session = _activeSession.value ?: return
        val now = System.currentTimeMillis()
        val durationMins = maxOf(1, ((now - session.startTime) / 1000 / 60).toInt())
        val burned = calculateCalories(session)
        val completedSession = session.copy(
            isCompleted = true,
            endTime = now,
            durationMinutes = durationMins,
            caloriesBurned = burned
        )
        _activeSession.value = completedSession
        saveCompletedSession(completedSession)
    }

    private fun saveCompletedSession(session: WorkoutSession) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationMins = maxOf(1, ((now - session.startTime) / 1000 / 60).toInt())
            val burned = calculateCalories(session)
            val completed = session.copy(
                isCompleted = true,
                endTime = now,
                durationMinutes = durationMins,
                caloriesBurned = burned
            )
            workoutRepository.saveSession(completed)
        }
    }

    fun calculateCalories(session: WorkoutSession): Int {
        val userWeight = userProfile.value.weightKg.takeIf { it > 0.0 } ?: CalorieCalculator.DEFAULT_WEIGHT_KG
        return CalorieCalculator.workoutCalories(userWeight, listOf(session)).roundToInt()
    }

    fun playSpotifyMusic() {
        viewModelScope.launch {
            when (val result = spotifyRepository.playPlaylist(context)) {
                SpotifyPlayResult.PLAYING -> {
                    val playlistName = spotifyPlaylistName.value
                    _spotifyMessage.value = if (playlistName.isNullOrBlank()) {
                        "Playing on Spotify"
                    } else {
                        "Playing $playlistName on Spotify"
                    }
                }
                SpotifyPlayResult.NOT_LOGGED_IN -> {
                    _spotifyMessage.value = "Connect Spotify first"
                }
                is SpotifyPlayResult.NO_DEVICE -> {
                    _spotifyMessage.value = "Opening Spotify — tap play in the app"
                }
                is SpotifyPlayResult.ERROR -> {
                    _spotifyMessage.value = result.message
                }
            }
            scheduleSpotifyMessageClear()
        }
    }

    fun clearSpotifyMessage() {
        _spotifyMessage.value = null
    }

    private fun scheduleSpotifyMessageClear() {
        val message = _spotifyMessage.value ?: return
        viewModelScope.launch {
            delay(4_000L)
            if (_spotifyMessage.value == message) {
                _spotifyMessage.value = null
            }
        }
    }

    fun exitActiveWorkout() {
        _activeSession.value = null
        _activePlanQueue.value = emptyList()
        _activePlanIndex.value = 0
    }
}
