package com.daytoday.ui.screen.nba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.model.Injury
import com.daytoday.repository.Result
import com.daytoday.usecase.NbaUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

const val ALL_TEAMS_FILTER = "all"

fun Injury.matchesTeamFilter(token: String): Boolean =
    teamId == token ||
        teamName.equals(token, ignoreCase = true) ||
        teamName.contains(token, ignoreCase = true)

@HiltViewModel
class NbaInjuriesViewModel @Inject constructor(
    private val nbaUseCases: NbaUseCases
) : ViewModel() {

    private val _injuries = MutableStateFlow<UiState<List<Injury>>>(UiState.Loading)
    val injuries: StateFlow<UiState<List<Injury>>> = _injuries

    private val _teams = MutableStateFlow<List<Pair<String, String>>>(
        listOf("All" to ALL_TEAMS_FILTER)
    )
    val teams: StateFlow<List<Pair<String, String>>> = _teams

    private var currentTeamId: String? = null

    init {
        loadInjuries()
    }

    fun loadInjuries(teamId: String? = null) {
        currentTeamId = teamId
        _injuries.value = UiState.Loading
        viewModelScope.launch {
            val useCaseResult = nbaUseCases.getInjuries(null)
            when (val result = useCaseResult.result) {
                is Result.Success -> {
                    val all = result.data
                    _teams.value = buildTeamOptions(all)
                    val filtered = if (teamId != null) {
                        all.filter { injury -> injury.matchesTeamFilter(teamId) }
                    } else {
                        all
                    }
                    _injuries.value = if (useCaseResult.isStale) {
                        UiState.Stale(filtered)
                    } else {
                        UiState.Success(filtered)
                    }
                }
                is Result.Failure -> {
                    _injuries.value = UiState.Error(result.error)
                }
            }
        }
    }

    fun refresh() {
        loadInjuries(currentTeamId)
    }

    fun setTeamFilter(teamId: String?) {
        loadInjuries(teamId)
    }

    fun clearTeamFilter() {
        loadInjuries(null)
    }

    private fun buildTeamOptions(all: List<Injury>): List<Pair<String, String>> {
        val byTeamId = LinkedHashMap<String, String>()
        for (injury in all) {
            if (injury.teamId.isBlank() || injury.teamName.isBlank()) continue
            if (!byTeamId.containsKey(injury.teamId)) {
                byTeamId[injury.teamId] = injury.teamName
            }
        }
        val sortedTeams = byTeamId.entries
            .sortedBy { entry -> entry.value.lowercase() }
            .map { entry -> entry.value to entry.key }
        return listOf("All" to ALL_TEAMS_FILTER) + sortedTeams
    }
}
