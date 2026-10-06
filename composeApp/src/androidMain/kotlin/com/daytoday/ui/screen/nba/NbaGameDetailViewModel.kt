package com.daytoday.ui.screen.nba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.usecase.NbaUseCases
import com.daytoday.usecase.UseCaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class NbaGameDetailViewModel @Inject constructor(
    private val nbaUseCases: NbaUseCases
) : ViewModel() {

    private val _gameDetail = MutableStateFlow<UiState<com.daytoday.model.NbaGame>>(UiState.Loading)
    val gameDetail: StateFlow<UiState<com.daytoday.model.NbaGame>> = _gameDetail

    fun loadGameDetail(gameId: String) {
        _gameDetail.value = UiState.Loading
        viewModelScope.launch {
            val useCaseResult = nbaUseCases.getGameDetail(gameId)
            _gameDetail.value = when (useCaseResult.result) {
                is com.daytoday.repository.Result.Success -> if (useCaseResult.isStale) UiState.Stale(useCaseResult.result.data) else UiState.Success(useCaseResult.result.data)
                is com.daytoday.repository.Result.Failure -> UiState.Error(useCaseResult.result.error)
            }
        }
    }

    fun refresh(gameId: String) {
        loadGameDetail(gameId)
    }
}