package com.daytoday.data.sync

import java.util.UUID

/** Asks the platform to deliver the outbox soon (WorkManager on Android). */
fun interface SyncTrigger {
    fun requestSync()
}

internal const val LOCAL_ID_PREFIX = "local-"

internal fun newLocalId(): String = LOCAL_ID_PREFIX + UUID.randomUUID().toString()

internal fun String.isLocalId(): Boolean = startsWith(LOCAL_ID_PREFIX)

/** Small helper shared by the repositories to append to / cancel entries in the outbox. */
class OutboxQueue(
    private val outbox: OutboxDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun enqueue(
        entityType: String,
        method: String,
        path: String,
        body: String? = null,
        tempId: String? = null,
    ) {
        outbox.insert(
            OutboxEntity(
                method = method,
                path = path,
                body = body,
                createdAt = clock(),
                entityType = entityType,
                tempId = tempId,
            ),
        )
    }

    /**
     * Removes every queued request about [localId]: its create and any follow-ups.
     * Used when something created offline is deleted again before it ever synced.
     */
    suspend fun cancelAllFor(localId: String) {
        outbox.all()
            .filter { it.tempId == localId || it.path.contains(localId) || it.body?.contains(localId) == true }
            .forEach { outbox.delete(it.id) }
    }
}
