package com.daytoday.ui.screen.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.data.sync.MealItem
import com.daytoday.data.sync.MealsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MealsUiState(
    val meals: List<MealItem> = emptyList(),
    val totalCalories: Int = 0,
)

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealsRepository,
) : ViewModel() {

    private val date: String = LocalDate.now().toString()

    val uiState: StateFlow<MealsUiState> = repository.mealsFor(date)
        .map { meals -> MealsUiState(meals, meals.sumOf { it.calories }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealsUiState())

    init {
        viewModelScope.launch { repository.refresh(date) }
    }

    fun log(foodId: String, foodName: String, grams: Double, calories: Int) {
        viewModelScope.launch { repository.log(foodId, foodName, grams, calories, date) }
    }

    fun delete(meal: MealItem) {
        viewModelScope.launch { repository.delete(meal.id) }
    }
}
