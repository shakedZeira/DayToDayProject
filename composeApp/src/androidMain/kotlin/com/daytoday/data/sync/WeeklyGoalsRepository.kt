package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.WeeklyGoalDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WeeklyGoalItem(
    val id: String,
    val title: String,
    val targetCount: Int,
    val unit: String?,
    val category: String?,
    val thisWeekCount: Int,
    val unsynced: Boolean,
)

/** Server-side weekly goals (check-offs), offline-first. The on-device metric goals stay in SettingsManager. */
class WeeklyGoalsRepository(
    private val goals: WeeklyGoalDao,
    private val outbox: OutboxDao,
    private val api: DayTodayApi,
    private val token: suspend () -> String,
    private val sync: SyncTrigger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = ::newLocalId,
) : OutboxHandler {

    override val entityType: String = SyncEntity.GOAL

    private val queue = OutboxQueue(outbox, clock)

    val items: Flow<List<WeeklyGoalItem>> = goals.observeAll().map { rows -> rows.map { it.toItem() } }

    suspend fun refresh(): Boolean {
        if (outbox.countFor(entityType) > 0) return false
        return try {
            val remote = api.getWeeklyGoals(token())
            val now = clock()
            goals.deleteAll()
            goals.upsertAll(remote.mapIndexed { i, dto -> dto.toEntity(now - i) })
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun add(title: String, targetCount: Int, unit: String? = null): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val target = targetCount.coerceAtLeast(1)
        val id = newId()
        goals.upsert(
            WeeklyGoalEntity(
                id = id, title = clean, targetCount = target, unit = unit, category = null,
                thisWeekCount = 0, createdAt = clock(),
            ),
        )
        val body = buildJsonObject {
            put("title", clean)
            put("targetCount", target)
            unit?.let { put("unit", it) }
        }.toString()
        queue.enqueue(entityType, "POST", "api/goals", body, tempId = id)
        sync.requestSync()
        return id
    }

    suspend fun checkOff(id: String) = bump(id, +1, "checkoff")

    suspend fun undo(id: String) = bump(id, -1, "undo")

    private suspend fun bump(id: String, delta: Int, action: String) {
        val row = goals.get(id) ?: return
        if (delta < 0 && row.thisWeekCount == 0) return
        goals.upsert(row.copy(thisWeekCount = (row.thisWeekCount + delta).coerceAtLeast(0)))
        queue.enqueue(entityType, "POST", "api/goals/$id/$action")
        sync.requestSync()
    }

    suspend fun delete(id: String) {
        goals.delete(id)
        if (id.isLocalId()) {
            queue.cancelAllFor(id)
        } else {
            queue.enqueue(entityType, "DELETE", "api/goals/$id")
            sync.requestSync()
        }
    }

    override suspend fun onSent(entry: OutboxEntity, responseBody: String?): String? =
        if (entry.tempId != null) responseId(responseBody) else null

    override suspend fun onDropped(entry: OutboxEntity) {
        entry.tempId?.let { goals.delete(it) }
    }

    override suspend fun onIdAssigned(tempId: String, realId: String) {
        val row = goals.get(tempId) ?: return
        goals.delete(tempId)
        goals.upsert(row.copy(id = realId))
    }

    private fun WeeklyGoalEntity.toItem() = WeeklyGoalItem(
        id = id, title = title, targetCount = targetCount, unit = unit, category = category,
        thisWeekCount = thisWeekCount, unsynced = id.isLocalId(),
    )

    private fun WeeklyGoalDto.toEntity(createdAt: Long) = WeeklyGoalEntity(
        id = id, title = title, targetCount = targetCount, unit = unit, category = category,
        thisWeekCount = thisWeekCount, createdAt = createdAt,
    )
}
