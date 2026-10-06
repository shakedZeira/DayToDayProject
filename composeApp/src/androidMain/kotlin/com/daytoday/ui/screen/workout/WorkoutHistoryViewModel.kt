package com.daytoday.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.usecase.WorkoutUseCases
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val workoutUseCases: WorkoutUseCases
) : ViewModel() {

    private val _sessions = MutableStateFlow<UiState<List<WorkoutSession>>>(UiState.Loading)
    val sessions: StateFlow<UiState<List<WorkoutSession>>> = _sessions

    init {
        loadSessions()
    }

    fun loadSessions() {
        _sessions.value = UiState.Loading
        viewModelScope.launch {
            val result = workoutUseCases.getSessions()
            _sessions.value = when (result) {
                is Result.Success -> UiState.Success(result.data)
                is Result.Failure -> UiState.Error(result.error)
            }
        }
    }
}