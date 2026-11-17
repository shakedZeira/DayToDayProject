package com.daytoday.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point used by the WorkManager job and the UI: deliver the outbox, and once it is
 * empty pull fresh server state so local rows converge on the server copy.
 */
class SyncCoordinator(
    private val processor: OutboxProcessor,
    private val outbox: OutboxDao,
    private val notes: SyncNoteDao,
    private val tasks: TasksRepository,
    private val goals: WeeklyGoalsRepository,
    private val meals: MealsRepository,
) {
    val pendingCount: Flow<Int> = outbox.observeCount()
    val authPaused: StateFlow<Boolean> = processor.authPaused
    val notices: Flow<List<SyncNoteEntity>> = notes.observeAll()

    suspend fun dismissNotice(id: Long) = notes.delete(id)

    suspend fun clearNotices() = notes.clear()

    fun resumeAfterLogin() = processor.resumeAfterLogin()

    suspend fun syncAll(): DrainResult {
        val result = processor.drain()
        if (result.status == DrainStatus.DONE) {
            refreshAll()
        }
        return result
    }

    suspend fun refreshAll() {
        for (step in listOf<suspend () -> Boolean>({ tasks.refresh() }, { goals.refresh() }, { meals.refresh() })) {
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Refresh is best effort; the next sync tries again.
            }
        }
    }
}
