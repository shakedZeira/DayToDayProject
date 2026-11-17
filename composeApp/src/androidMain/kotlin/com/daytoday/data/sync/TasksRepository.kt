package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.TaskDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String?,
    val done: Boolean,
    val dueAt: String?,
    val recurrence: String?,
    val category: String?,
    val remindAt: String?,
    /** True while the task only exists on this device (created offline, not yet synced). */
    val unsynced: Boolean,
)

/**
 * Tasks with optimistic local state: every write lands in Room immediately and is queued in
 * the outbox; [refresh] pulls the server copy only when nothing is waiting to be sent.
 */
class TasksRepository(
    private val tasks: TaskDao,
    private val outbox: OutboxDao,
    private val api: DayTodayApi,
    private val token: suspend () -> String,
    private val sync: SyncTrigger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = ::newLocalId,
) : OutboxHandler {

    override val entityType: String = SyncEntity.TASK

    private val queue = OutboxQueue(outbox, clock)

    val items: Flow<List<TaskItem>> = tasks.observeAll().map { rows -> rows.map { it.toItem() } }

    suspend fun snapshot(): List<TaskItem> = tasks.all().map { it.toItem() }

    /** Pulls the server list. Returns false (and keeps local state) if writes are pending or the call fails. */
    suspend fun refresh(): Boolean {
        if (outbox.countFor(entityType) > 0) return false
        return try {
            val remote = api.getTasks(token())
            val now = clock()
            tasks.deleteAll()
            // Server order is newest first; keep it by spacing createdAt downwards.
            tasks.upsertAll(remote.mapIndexed { i, dto -> dto.toEntity(now - i) })
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    suspend fun add(
        title: String,
        notes: String? = null,
        dueAt: String? = null,
        recurrence: String = "none",
        category: String? = null,
    ): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val id = newId()
        tasks.upsert(
            TaskEntity(
                id = id, title = clean, notes = notes, status = "PENDING", dueAt = dueAt,
                recurrence = recurrence, category = category, remindAt = null, createdAt = clock(),
            ),
        )
        val body = buildJsonObject {
            put("title", clean)
            notes?.let { put("notes", it) }
            dueAt?.let { put("dueAt", it) }
            put("recurrence", recurrence)
            category?.let { put("category", it) }
        }.toString()
        queue.enqueue(entityType, "POST", "api/tasks", body, tempId = id)
        sync.requestSync()
        return id
    }

    suspend fun setDone(id: String, done: Boolean) {
        val row = tasks.get(id) ?: return
        tasks.upsert(row.copy(status = if (done) "DONE" else "PENDING"))
        queue.enqueue(
            entityType, "PATCH", "api/tasks/$id",
            buildJsonObject { put("status", if (done) "DONE" else "PENDING") }.toString(),
        )
        sync.requestSync()
    }

    suspend fun rename(id: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val row = tasks.get(id) ?: return
        tasks.upsert(row.copy(title = clean))
        queue.enqueue(entityType, "PATCH", "api/tasks/$id", buildJsonObject { put("title", clean) }.toString())
        sync.requestSync()
    }

    suspend fun delete(id: String) {
        tasks.delete(id)
        if (id.isLocalId()) {
            queue.cancelAllFor(id)
        } else {
            queue.enqueue(entityType, "DELETE", "api/tasks/$id")
            sync.requestSync()
        }
    }

    // ---- OutboxHandler -------------------------------------------------------------------

    override suspend fun onSent(entry: OutboxEntity, responseBody: String?): String? =
        if (entry.tempId != null) responseId(responseBody) else null

    override suspend fun onDropped(entry: OutboxEntity) {
        // A rejected create never existed on the server; other rejected edits are healed by
        // the next refresh once the queue is empty.
        entry.tempId?.let { tasks.delete(it) }
    }

    override suspend fun onIdAssigned(tempId: String, realId: String) {
        val row = tasks.get(tempId) ?: return
        tasks.delete(tempId)
        tasks.upsert(row.copy(id = realId))
    }

    private fun TaskEntity.toItem() = TaskItem(
        id = id, title = title, notes = notes, done = status == "DONE", dueAt = dueAt,
        recurrence = recurrence, category = category, remindAt = remindAt, unsynced = id.isLocalId(),
    )

    private fun TaskDto.toEntity(createdAt: Long) = TaskEntity(
        id = id, title = title, notes = notes, status = status, dueAt = dueAt, recurrence = recurrence,
        category = category, remindAt = remindAt, createdAt = createdAt,
    )
}

internal fun responseId(body: String?): String? =
    body?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
    }
