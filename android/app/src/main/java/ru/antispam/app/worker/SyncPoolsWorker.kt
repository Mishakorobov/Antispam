package ru.antispam.app.worker

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.data.repository.SyncResult
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncPoolsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncPoolsWorker"
        private const val WORK_NAME = "antispam_periodic_sync"
        private const val KEY_LAST_DELTA_VERSION = "last_delta_version"
        private const val PREFS_NAME = "antispam_prefs"
        private const val PREF_DEVICE_HASH = "device_hash"

        /**
         * Schedule background sync every 6 hours when connected to network.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true) // Battery efficiency: never wake up radio on low battery
                .setRequiresStorageNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncPoolsWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Periodic sync scheduled (every 6 hours with BatteryNotLow constraint).")
        }

        /**
         * Opportunistic sync triggered when CallScreeningService intercepts incoming call
         * and detects that local database is stale (> 24 hours).
         * Wakes up network and updates pools even on aggressive OEM battery savers (MIUI/OneUI).
         */
        fun triggerOpportunisticSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = androidx.work.OneTimeWorkRequestBuilder<SyncPoolsWorker>()
                .setConstraints(constraints)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Opportunistic sync enqueued (bypassing aggressive OEM battery sleep).")
        }

        fun getOrCreateDeviceHash(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var hash = prefs.getString(PREF_DEVICE_HASH, null)
            if (hash == null) {
                val rawId = UUID.randomUUID().toString()
                val md = MessageDigest.getInstance("SHA-256")
                hash = md.digest(rawId.toByteArray()).joinToString("") { "%02x".format(it) }
                prefs.edit().putString(PREF_DEVICE_HASH, hash).apply()
            }
            return hash
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val deviceHash = getOrCreateDeviceHash(applicationContext)
        val repository = SyncRepository(db)

        when (val result = repository.synchronize(deviceHash)) {
            is SyncResult.Success -> {
                Log.i(TAG, "Sync completed successfully via ${result.source}: current version ${result.currentVersion} (+${result.addedCount}, -${result.removedCount})")
                Result.success()
            }
            is SyncResult.NotModified -> {
                Log.i(TAG, "Sync HTTP 304 Not Modified: Local database is up-to-date.")
                Result.success()
            }
            is SyncResult.Failure -> {
                Log.e(TAG, "Sync failed on all channels: ${result.message}", result.lastError)
                Result.retry()
            }
        }
    }
}
