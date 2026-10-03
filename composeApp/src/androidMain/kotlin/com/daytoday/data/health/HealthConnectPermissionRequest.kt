package com.daytoday.data.health

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the Health Connect permission result from [HealthConnectPermissionActivity]
 * back to the suspended caller in [HealthRepositoryImpl].
 *
 * The repository holds no Activity reference; it starts a transparent Activity and
 * awaits the deferred this object holds.
 */
object HealthConnectPermissionRequest {
    private val current = AtomicReference<CompletableDeferred<Boolean>?>(null)

    /** Returns the deferred that the active permission request should complete. */
    fun next(): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        current.set(deferred)
        return deferred
    }

    /** Must be called from the ActivityResultLauncher callback to resolve the in-flight request. */
    fun complete(granted: Boolean) {
        val deferred = current.getAndSet(null) // clear so a stale second tap doesn't resolve this
        if (deferred != null && !deferred.isCompleted) {
            deferred.complete(granted)
        }
    }
}
