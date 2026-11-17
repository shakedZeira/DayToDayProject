package com.daytoday.testing

import com.daytoday.data.sync.MealLogDao
import com.daytoday.data.sync.MealLogEntity
import com.daytoday.data.sync.OutboxDao
import com.daytoday.data.sync.OutboxEntity
import com.daytoday.data.sync.OutboxHandler
import com.daytoday.data.sync.OutboxSender
import com.daytoday.data.sync.SendOutcome
import com.daytoday.data.sync.SyncNoteDao
import com.daytoday.data.sync.SyncNoteEntity
import com.daytoday.data.sync.SyncTrigger
import com.daytoday.data.sync.TaskDao
import com.daytoday.data.sync.TaskEntity
import com.daytoday.data.sync.WeeklyGoalDao
import com.daytoday.data.sync.WeeklyGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeOutboxDao : OutboxDao {
    private val rows = MutableStateFlow<List<OutboxEntity>>(emptyList())
    private var nextId = 1L

    val snapshot: List<OutboxEntity> get() = rows.value

    override suspend fun insert(entity: OutboxEntity): Long {
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }

    override suspend fun all(): List<OutboxEntity> = rows.value.sortedBy { it.id }
    override fun observeCount(): Flow<Int> = rows.map { it.size }
    override suspend fun countFor(entityType: String): Int = rows.value.count { it.entityType == entityType }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun bumpAttempts(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it }
    }
    override suspend fun updateRequest(id: Long, path: String, body: String?) {
        rows.value = rows.value.map { if (it.id == id) it.copy(path = path, body = body) else it }
    }
}

class FakeSyncNoteDao : SyncNoteDao {
    private val rows = MutableStateFlow<List<SyncNoteEntity>>(emptyList())
    private var nextId = 1L
    val snapshot: List<SyncNoteEntity> get() = rows.value

    override suspend fun insert(entity: SyncNoteEntity): Long {
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }
    override fun observeAll(): Flow<List<SyncNoteEntity>> = rows
    override suspend fun all(): List<SyncNoteEntity> = rows.value
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun clear() { rows.value = emptyList() }
}

class FakeTaskDao : TaskDao {
    private val rows = MutableStateFlow<List<TaskEntity>>(emptyList())
    val snapshot: List<TaskEntity> get() = rows.value.sortedByDescending { it.createdAt }

    override fun observeAll(): Flow<List<TaskEntity>> = rows.map { r -> r.sortedByDescending { it.createdAt } }
    override suspend fun all(): List<TaskEntity> = snapshot
    override suspend fun get(id: String): TaskEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun upsert(entity: TaskEntity) { rows.value = rows.value.filterNot { it.id == entity.id } + entity }
    override suspend fun upsertAll(entities: List<TaskEntity>) { entities.forEach { upsert(it) } }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class FakeWeeklyGoalDao : WeeklyGoalDao {
    private val rows = MutableStateFlow<List<WeeklyGoalEntity>>(emptyList())
    val snapshot: List<WeeklyGoalEntity> get() = rows.value.sortedByDescending { it.createdAt }

    override fun observeAll(): Flow<List<WeeklyGoalEntity>> = rows.map { r -> r.sortedByDescending { it.createdAt } }
    override suspend fun get(id: String): WeeklyGoalEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun upsert(entity: WeeklyGoalEntity) { rows.value = rows.value.filterNot { it.id == entity.id } + entity }
    override suspend fun upsertAll(entities: List<WeeklyGoalEntity>) { entities.forEach { upsert(it) } }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class FakeMealLogDao : MealLogDao {
    private val rows = MutableStateFlow<List<MealLogEntity>>(emptyList())
    val snapshot: List<MealLogEntity> get() = rows.value

    override fun observeForDate(date: String): Flow<List<MealLogEntity>> =
        rows.map { r -> r.filter { it.date == date }.sortedBy { it.createdAt } }
    override suspend fun get(id: String): MealLogEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun upsert(entity: MealLogEntity) { rows.value = rows.value.filterNot { it.id == entity.id } + entity }
    override suspend fun upsertAll(entities: List<MealLogEntity>) { entities.forEach { upsert(it) } }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteForDate(date: String) { rows.value = rows.value.filterNot { it.date == date } }
}

class CountingTrigger : SyncTrigger {
    var requests = 0
    override fun requestSync() { requests++ }
}

/** Scripted sender: pops outcomes in order (or uses [default]) and records every call. */
class ScriptedSender(var default: SendOutcome = SendOutcome.Ok(null)) : OutboxSender {
    data class Call(val method: String, val path: String, val body: String?, val token: String)

    val calls = mutableListOf<Call>()
    val script = ArrayDeque<SendOutcome>()
    var throwOnSend: Throwable? = null

    override suspend fun send(method: String, path: String, body: String?, token: String): SendOutcome {
        calls += Call(method, path, body, token)
        throwOnSend?.let { throw it }
        return script.removeFirstOrNull() ?: default
    }
}

class RecordingHandler(override val entityType: String) : OutboxHandler {
    val sent = mutableListOf<OutboxEntity>()
    val dropped = mutableListOf<OutboxEntity>()
    val assigned = mutableListOf<Pair<String, String>>()
    var createdId: String? = null

    override suspend fun onSent(entry: OutboxEntity, responseBody: String?): String? {
        sent += entry
        return if (entry.tempId != null) createdId else null
    }
    override suspend fun onDropped(entry: OutboxEntity) { dropped += entry }
    override suspend fun onIdAssigned(tempId: String, realId: String) { assigned += tempId to realId }
}
