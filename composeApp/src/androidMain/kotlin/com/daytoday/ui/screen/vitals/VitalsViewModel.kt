package com.daytoday.ui.screen.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.data.health.VitalsLoad
import com.daytoday.data.health.VitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VitalsUiState(
    val loading: Boolean = true,
    val result: VitalsLoad? = null,
)

@HiltViewModel
class VitalsViewModel @Inject constructor(
    private val repository: VitalsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VitalsUiState())
    val uiState: StateFlow<VitalsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val result = repository.load()
            _uiState.value = VitalsUiState(loading = false, result = result)
        }
    }
}
