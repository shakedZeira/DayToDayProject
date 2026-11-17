package com.daytoday.ui.screen.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.data.sync.SyncCoordinator
import com.daytoday.data.sync.SyncNoteEntity
import com.daytoday.data.sync.TaskItem
import com.daytoday.data.sync.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<TaskItem> = emptyList(),
    /** Writes made offline that have not reached the server yet. */
    val pendingChanges: Int = 0,
    /** Session expired: changes are kept and will be sent after the next sign-in. */
    val authPaused: Boolean = false,
    /** Messages such as "a change was rejected by the server and discarded". */
    val notices: List<SyncNoteEntity> = emptyList(),
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TasksRepository,
    private val coordinator: SyncCoordinator,
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = combine(
        repository.items,
        coordinator.pendingCount,
        coordinator.authPaused,
        coordinator.notices,
    ) { tasks, pending, paused, notices ->
        TasksUiState(tasks = tasks, pendingChanges = pending, authPaused = paused, notices = notices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        sync()
    }

    /** Deliver pending writes, then pull the server copy. Safe to call repeatedly. */
    fun sync() {
        viewModelScope.launch { coordinator.syncAll() }
    }

    fun add(title: String) {
        viewModelScope.launch { repository.add(title) }
    }

    fun setDone(task: TaskItem, done: Boolean) {
        viewModelScope.launch { repository.setDone(task.id, done) }
    }

    fun delete(task: TaskItem) {
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun dismissNotice(id: Long) {
        viewModelScope.launch { coordinator.dismissNotice(id) }
    }
}
