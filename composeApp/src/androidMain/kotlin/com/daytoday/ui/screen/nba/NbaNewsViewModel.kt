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
class NbaNewsViewModel @Inject constructor(
    private val nbaUseCases: NbaUseCases
) : ViewModel() {

    private val _news = MutableStateFlow<UiState<List<com.daytoday.model.NbaNews>>>(UiState.Loading)
    val news: StateFlow<UiState<List<com.daytoday.model.NbaNews>>> = _news

    init {
        loadNews()
    }

    fun loadNews(limit: Int = 20) {
        _news.value = UiState.Loading
        viewModelScope.launch {
            val useCaseResult = nbaUseCases.getNews(limit)
            _news.value = when (useCaseResult.result) {
                is com.daytoday.repository.Result.Success -> if (useCaseResult.isStale) UiState.Stale(useCaseResult.result.data) else UiState.Success(useCaseResult.result.data)
                is com.daytoday.repository.Result.Failure -> UiState.Error(useCaseResult.result.error)
            }
        }
    }

    fun refresh() {
        loadNews()
    }
}