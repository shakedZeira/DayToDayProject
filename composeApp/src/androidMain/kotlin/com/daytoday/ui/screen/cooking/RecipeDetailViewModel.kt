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
class RecipeDetailViewModel @Inject constructor(
    private val api: DayTodayApi,
    private val settingsManager: SettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<RecipeView>>(UiState.Loading)
    val uiState: StateFlow<UiState<RecipeView>> = _uiState

    private var currentId: String? = null

    fun load(recipeId: String, force: Boolean = false) {
        if (!force && recipeId == currentId && _uiState.value is UiState.Success) return
        currentId = recipeId
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                _uiState.value = UiState.Success(api.getRecipe(token, recipeId))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load recipe")
            }
        }
    }

    fun refresh() {
        currentId?.let { load(it, force = true) }
    }

    fun toggleFavorite() {
        val recipe = (_uiState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val favorited = api.toggleFavorite(token, recipe.id)
                val delta = when {
                    favorited && !recipe.isFavorited -> 1
                    !favorited && recipe.isFavorited -> -1
                    else -> 0
                }
                _uiState.value = UiState.Success(
                    recipe.copy(
                        isFavorited = favorited,
                        favoriteCount = (recipe.favoriteCount + delta).coerceAtLeast(0),
                    )
                )
            } catch (_: Exception) {
                // Keep the current recipe on screen; the list will refresh next time.
            }
        }
    }
}
