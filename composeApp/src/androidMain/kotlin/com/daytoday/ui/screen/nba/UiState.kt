package com.daytoday.ui.screen.nba

sealed interface UiState<out T> {
    data class Success<out T>(val data: T) : UiState<T>
    data class Stale<out T>(val data: T) : UiState<T>
    object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}