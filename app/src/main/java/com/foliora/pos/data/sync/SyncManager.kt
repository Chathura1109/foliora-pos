package com.foliora.pos.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Singleton manager object responsible for enqueueing and scheduling background
 * synchronization work requests via Android [WorkManager].
 */
object SyncManager {

    /** Unique work identifier for background periodic synchronization tasks. */
    private const val PERIODIC_SYNC_WORK_NAME = "FolioraPeriodicSyncWork"

    /** Unique work identifier for manual/on-demand immediate synchronization tasks. */
    private const val IMMEDIATE_SYNC_WORK_NAME = "FolioraImmediateSyncWork"

    /**
     * Schedules a periodic background sync worker that runs every 15 minutes.
     * Enforces network connectivity constraints to prevent unnecessary execution offline.
     *
     * @param context Application context used to access [WorkManager].
     */
    fun schedulePeriodicSync(context: Context) {
        // Enforce network connection requirement for cloud synchronization
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create periodic request repeating every 15 minutes (minimum interval permitted by WorkManager)
        val periodicWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        // Enqueue unique periodic work keeping existing schedule if already scheduled
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    /**
     * Triggers an immediate one-time background sync request.
     * Useful for manual refresh or post-checkout immediate sync operations.
     *
     * @param context Application context used to access [WorkManager].
     */
    fun triggerImmediateSync(context: Context) {
        // Enforce network connection requirement for cloud synchronization
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create one-time request for execution as soon as constraints are met
        val immediateWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        // Enqueue unique immediate work, replacing any pending immediate execution
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateWorkRequest
        )
    }
}
