package com.daytoday.ui.screen.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.network.DayTodayApi
import com.daytoday.network.RecipeView
import com.daytoday.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class CookingViewModel @Inject constructor(
    private val api: DayTodayApi,
    private val settingsManager: SettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<CookingUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<CookingUiState>> = _uiState

    private var state = CookingUiState()

    init {
        refresh()
    }

    private fun setState(transform: (CookingUiState) -> CookingUiState) {
        state = transform(state)
        _uiState.value = UiState.Success(state)
    }

    fun refresh() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val recipes = api.getRecipes(token, meal = null, favorites = false)
                state = CookingUiState(recipes = recipes)
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load recipes")
            }
        }
    }

    fun toggleFavorite(recipe: RecipeView) {
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val nowFavorited = api.toggleFavorite(token, recipe.id)
                // Optimistically update the recipe in the list
                state = state.copy(
                    recipes = state.recipes.map { r ->
                        if (r.id == recipe.id) r.copy(isFavorited = nowFavorited) else r
                    }
                )
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to update favorite")
            }
        }
    }

    fun filterByMeal(meal: String?) {
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val recipes = api.getRecipes(token, meal = meal, favorites = false)
                state = CookingUiState(recipes = recipes, selectedMeal = meal)
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load recipes")
            }
        }
    }

    fun showFavorites() {
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val recipes = api.getRecipes(token, meal = null, favorites = true)
                state = CookingUiState(recipes = recipes, showFavorites = true)
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load favorites")
            }
        }
    }
}

data class CookingUiState(
    val recipes: List<RecipeView> = emptyList(),
    val selectedMeal: String? = null,
    val showFavorites: Boolean = false
)

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
