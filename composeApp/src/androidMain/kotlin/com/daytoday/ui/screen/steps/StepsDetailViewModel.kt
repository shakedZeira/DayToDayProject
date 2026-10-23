package com.daytoday.ui.screen.steps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.usecase.StepsHistory
import com.daytoday.usecase.StepsHistoryUseCases
import com.daytoday.usecase.StepsLoadOutcome
import com.daytoday.usecase.StepsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StepsUiState {
    data object Loading : StepsUiState
    data object PermissionMissing : StepsUiState
    data class Error(val message: String) : StepsUiState
    data class Success(val history: StepsHistory) : StepsUiState
}

@HiltViewModel
class StepsDetailViewModel @Inject constructor(
    private val useCases: StepsHistoryUseCases,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StepsUiState>(StepsUiState.Loading)
    val uiState: StateFlow<StepsUiState> = _uiState.asStateFlow()

    private val _period = MutableStateFlow(StepsPeriod.DAY)
    val period: StateFlow<StepsPeriod> = _period.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(StepsPeriod.DAY)
    }

    fun selectPeriod(period: StepsPeriod) {
        if (period == _period.value && _uiState.value is StepsUiState.Success) return
        _period.value = period
        load(period)
    }

    fun refresh() = load(_period.value, forceRefresh = true)

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            refresh()
        } else {
            _uiState.value = StepsUiState.PermissionMissing
        }
    }

    fun requestPermission() {
        viewModelScope.launch {
            if (useCases.requestPermission()) {
                refresh()
            } else {
                _uiState.value = StepsUiState.PermissionMissing
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            if (useCases.hasPermission()) {
                if (_uiState.value !is StepsUiState.Success) {
                    refresh()
                }
            } else if (_uiState.value is StepsUiState.Success) {
                _uiState.value = StepsUiState.PermissionMissing
            }
        }
    }

    private fun load(period: StepsPeriod, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = StepsUiState.Loading
            _uiState.value = when (val outcome = useCases.load(period, forceRefresh)) {
                is StepsLoadOutcome.Success -> StepsUiState.Success(outcome.history)
                StepsLoadOutcome.PermissionMissing -> StepsUiState.PermissionMissing
                is StepsLoadOutcome.Failure -> StepsUiState.Error(outcome.message)
            }
        }
    }
}
