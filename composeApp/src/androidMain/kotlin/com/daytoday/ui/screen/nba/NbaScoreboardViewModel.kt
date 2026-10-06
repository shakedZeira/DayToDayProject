package com.daytoday.ui.screen.nba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.model.NbaGame
import com.daytoday.repository.Result
import com.daytoday.usecase.NbaUseCases
import com.daytoday.usecase.UseCaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

@HiltViewModel
class NbaScoreboardViewModel @Inject constructor(
    private val nbaUseCases: NbaUseCases
) : ViewModel() {

    private val _scoreboard = MutableStateFlow<UiState<List<NbaGame>>>(UiState.Loading)
    val scoreboard: StateFlow<UiState<List<NbaGame>>> = _scoreboard

    private var currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

    init {
        loadScoreboard()
    }

    fun loadScoreboard() {
        _scoreboard.value = UiState.Loading
        viewModelScope.launch {
            val useCaseResult = nbaUseCases.getScoreboard(currentDate)
            _scoreboard.value = when (useCaseResult.result) {
                is Result.Success -> if (useCaseResult.isStale) UiState.Stale(useCaseResult.result.data) else UiState.Success(useCaseResult.result.data)
                is Result.Failure -> UiState.Error(useCaseResult.result.error)
            }
        }
    }

    fun refresh() {
        loadScoreboard()
    }

    fun setDate(date: LocalDate) {
        currentDate = date.toString()
        loadScoreboard()
    }
}