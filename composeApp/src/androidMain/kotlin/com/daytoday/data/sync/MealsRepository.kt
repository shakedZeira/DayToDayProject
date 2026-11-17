package com.daytoday.data.sync

import com.daytoday.network.DayTodayApi
import com.daytoday.network.MealLogDto
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MealItem(
    val id: String,
    val foodId: String,
    val foodName: String,
    val grams: Double,
    val calories: Int,
    val date: String,
    val unsynced: Boolean,
)

/** Meal log entries (`/api/foods/meals`), offline-first. */
class MealsRepository(
    private val meals: MealLogDao,
    private val outbox: OutboxDao,
    private val api: DayTodayApi,
    private val token: suspend () -> String,
    private val sync: SyncTrigger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = ::newLocalId,
    private val today: () -> String = { LocalDate.now().toString() },
) : OutboxHandler {

    override val entityType: String = SyncEntity.MEAL

    private val queue = OutboxQueue(outbox, clock)

    fun mealsFor(date: String): Flow<List<MealItem>> =
        meals.observeForDate(date).map { rows -> rows.map { it.toItem() } }

    suspend fun refresh(date: String = today()): Boolean {
        if (outbox.countFor(entityType) > 0) return false
        return try {
            val remote = api.getMeals(token(), date)
            val now = clock()
            meals.deleteForDate(date)
            meals.upsertAll(remote.meals.mapIndexed { i, dto -> dto.toEntity(date, now + i) })
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Logs [grams] of a food; [calories] is the caller's estimate so the day total updates offline. */
    suspend fun log(
        foodId: String,
        foodName: String,
        grams: Double,
        calories: Int,
        date: String = today(),
    ): String? {
        if (foodId.isBlank() || !grams.isFinite() || grams <= 0.0) return null
        val id = newId()
        meals.upsert(
            MealLogEntity(
                id = id, foodId = foodId, foodName = foodName, grams = grams,
                calories = calories.coerceAtLeast(0), date = date, createdAt = clock(),
            ),
        )
        val body = buildJsonObject {
            put("foodId", foodId)
            put("grams", grams)
            put("date", Instant.ofEpochMilli(clock()).toString())
        }.toString()
        queue.enqueue(entityType, "POST", "api/foods/meals", body, tempId = id)
        sync.requestSync()
        return id
    }

    suspend fun delete(id: String) {
        meals.delete(id)
        if (id.isLocalId()) {
            queue.cancelAllFor(id)
        } else {
            queue.enqueue(entityType, "DELETE", "api/foods/meals/$id")
            sync.requestSync()
        }
    }

    override suspend fun onSent(entry: OutboxEntity, responseBody: String?): String? =
        if (entry.tempId != null) responseId(responseBody) else null

    override suspend fun onDropped(entry: OutboxEntity) {
        entry.tempId?.let { meals.delete(it) }
    }

    override suspend fun onIdAssigned(tempId: String, realId: String) {
        val row = meals.get(tempId) ?: return
        meals.delete(tempId)
        meals.upsert(row.copy(id = realId))
    }

    private fun MealLogEntity.toItem() = MealItem(
        id = id, foodId = foodId, foodName = foodName, grams = grams, calories = calories,
        date = date, unsynced = id.isLocalId(),
    )

    private fun MealLogDto.toEntity(date: String, createdAt: Long) = MealLogEntity(
        id = id, foodId = foodId, foodName = foodName, grams = grams, calories = calories,
        date = date, createdAt = createdAt,
    )
}
