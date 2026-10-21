package io.nekohasekai.sfa.panel

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.sfa.Application
import java.util.concurrent.TimeUnit

/** Periodic background sync with the panel while signed in. */
class PanelSyncWork(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "PanelSyncWork"
        private const val WORK_NAME = "PanelSync"

        /** WorkManager will not run periodic work more often than this. */
        private const val MIN_INTERVAL_MINUTES = 15L

        /** Starts (or cancels, when signed out) the periodic sync. Safe on every app start. */
        fun schedule(intervalMinutes: Int = PanelStore.syncIntervalMinutes) {
            val workManager = WorkManager.getInstance(Application.application)
            if (!PanelSync.isSignedIn) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(
                    PanelSyncWork::class.java,
                    intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES),
                    TimeUnit.MINUTES,
                ).setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).build(),
            )
        }

        fun cancel() {
            WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!PanelSync.isSignedIn) return Result.success()
        try {
            PanelSync.syncNow()
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
        }
        // The next period retries anyway; no retry storm.
        return Result.success()
    }
}
