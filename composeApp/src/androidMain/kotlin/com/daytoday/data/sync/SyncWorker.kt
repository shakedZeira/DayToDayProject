package com.daytoday.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun coordinator(): SyncCoordinator
}

/**
 * Delivers the outbox once the network is back. Transient failures ask WorkManager to retry
 * with exponential backoff; an expired session finishes successfully and stays paused until
 * the user signs in again (see [SyncCoordinator.resumeAfterLogin]).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors
            .fromApplication(applicationContext, SyncEntryPoint::class.java)
            .coordinator()
        return when (coordinator.syncAll().status) {
            DrainStatus.RETRY_LATER -> Result.retry()
            DrainStatus.DONE, DrainStatus.AUTH_PAUSED -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "daytoday-outbox-sync"
    }
}

class WorkManagerSyncTrigger(private val context: Context) : SyncTrigger {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: a request made while a run is in flight queues behind it, so a
        // write that lands after the run read the outbox is never stranded.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
