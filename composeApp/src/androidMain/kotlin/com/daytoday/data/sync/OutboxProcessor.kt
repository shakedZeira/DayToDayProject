package com.daytoday.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What happened when one queued request was sent. */
sealed interface SendOutcome {
    data class Ok(val body: String?) : SendOutcome
    data class Http(val status: Int, val message: String) : SendOutcome
    data class Network(val cause: Throwable) : SendOutcome
}

fun interface OutboxSender {
    suspend fun send(method: String, path: String, body: String?, token: String): SendOutcome
}

/**
 * Reconciles local state with what the server said about one outbox request.
 * One handler per [SyncEntity] type.
 */
interface OutboxHandler {
    val entityType: String

    /** The request succeeded. For creates, returns the id the server assigned (or null). */
    suspend fun onSent(entry: OutboxEntity, responseBody: String?): String?

    /** The request was dropped for good (a 4xx the server will never accept). */
    suspend fun onDropped(entry: OutboxEntity)

    /** A create made under [tempId] now lives on the server as [realId]. */
    suspend fun onIdAssigned(tempId: String, realId: String)
}

enum class DrainStatus {
    /** The queue is empty. */
    DONE,

    /** A transient failure (offline, 5xx, 408/429): try again later. */
    RETRY_LATER,

    /** The token is missing or rejected: nothing is sent until [OutboxProcessor.resumeAfterLogin]. */
    AUTH_PAUSED,
}

data class DrainResult(val status: DrainStatus, val sent: Int = 0, val dropped: Int = 0)

/**
 * Replays the outbox in order.
 *
 * - success: the row is deleted; creates remap their temp id in later requests.
 * - 401 / blank token: stop and stay paused (rows are kept) until the user signs in again.
 * - other 4xx except 408/429 ("poison"): the row would fail forever, so it is dropped,
 *   the owning handler reverts local state, and a [SyncNoteEntity] tells the user.
 *   Rows that depended on a dropped create are dropped with it.
 * - DELETE answered 404: already gone, treated as success.
 * - network failure, 5xx, 408, 429: stop, keep the row, report [DrainStatus.RETRY_LATER].
 */
class OutboxProcessor(
    private val outbox: OutboxDao,
    private val notes: SyncNoteDao,
    private val sender: OutboxSender,
    private val token: suspend () -> String,
    private val handlers: List<OutboxHandler>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _authPaused = MutableStateFlow(false)
    val authPaused: StateFlow<Boolean> = _authPaused.asStateFlow()

    fun resumeAfterLogin() {
        _authPaused.value = false
    }

    suspend fun drain(): DrainResult = mutex.withLock { drainLocked() }

    private suspend fun drainLocked(): DrainResult {
        if (_authPaused.value) return DrainResult(DrainStatus.AUTH_PAUSED)
        var sent = 0
        var dropped = 0
        while (true) {
            val entry = outbox.all().firstOrNull() ?: return DrainResult(DrainStatus.DONE, sent, dropped)
            val bearer = token()
            if (bearer.isBlank()) {
                _authPaused.value = true
                return DrainResult(DrainStatus.AUTH_PAUSED, sent, dropped)
            }
            val outcome = try {
                sender.send(entry.method, entry.path, entry.body, bearer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SendOutcome.Network(e)
            }
            when (outcome) {
                is SendOutcome.Ok -> {
                    outbox.delete(entry.id)
                    sent++
                    applySuccess(entry, outcome.body)
                }
                is SendOutcome.Http -> when {
                    outcome.status == 401 -> {
                        _authPaused.value = true
                        return DrainResult(DrainStatus.AUTH_PAUSED, sent, dropped)
                    }
                    outcome.status == 404 && entry.method == "DELETE" -> {
                        outbox.delete(entry.id)
                        sent++
                        applySuccess(entry, null)
                    }
                    isPoison(outcome.status) -> {
                        dropped += dropWithDependents(entry, outcome)
                    }
                    else -> {
                        outbox.bumpAttempts(entry.id)
                        return DrainResult(DrainStatus.RETRY_LATER, sent, dropped)
                    }
                }
                is SendOutcome.Network -> {
                    outbox.bumpAttempts(entry.id)
                    return DrainResult(DrainStatus.RETRY_LATER, sent, dropped)
                }
            }
        }
    }

    private fun isPoison(status: Int) = status in 400..499 && status != 408 && status != 429

    private fun handlerFor(entry: OutboxEntity) = handlers.firstOrNull { it.entityType == entry.entityType }

    private suspend fun applySuccess(entry: OutboxEntity, body: String?) {
        val handler = handlerFor(entry)
        val realId = handler?.onSent(entry, body)
        val temp = entry.tempId
        if (temp != null && realId != null && realId != temp) {
            outbox.all().forEach { pending ->
                if (pending.path.contains(temp) || pending.body?.contains(temp) == true) {
                    outbox.updateRequest(
                        pending.id,
                        pending.path.replace(temp, realId),
                        pending.body?.replace(temp, realId),
                    )
                }
            }
            handler?.onIdAssigned(temp, realId)
        }
    }

    /** Drops [entry] and any queued request that targets the entity it would have created. */
    private suspend fun dropWithDependents(entry: OutboxEntity, outcome: SendOutcome.Http): Int {
        var count = 0
        outbox.delete(entry.id)
        count++
        handlerFor(entry)?.onDropped(entry)
        notes.insert(
            SyncNoteEntity(
                message = "A change was rejected by the server and discarded: " +
                    "${describe(entry)} (${outcome.status}: ${outcome.message})",
                createdAt = clock(),
            ),
        )
        val temp = entry.tempId
        if (temp != null) {
            outbox.all().filter { it.path.contains(temp) || it.body?.contains(temp) == true }.forEach { dep ->
                outbox.delete(dep.id)
                handlerFor(dep)?.onDropped(dep)
                count++
            }
        }
        return count
    }

    private fun describe(entry: OutboxEntity): String {
        val verb = when (entry.method) {
            "POST" -> if (entry.tempId != null) "new ${entry.entityType}" else "${entry.entityType} update"
            "PATCH", "PUT" -> "${entry.entityType} edit"
            "DELETE" -> "${entry.entityType} removal"
            else -> entry.entityType
        }
        return verb
    }
}
