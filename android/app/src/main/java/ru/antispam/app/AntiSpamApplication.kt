package ru.antispam.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.security.AntiTamperHelper
import ru.antispam.app.worker.SyncPoolsWorker

class AntiSpamApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AntiSpamApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting AntiSpam Application...")

        // Phase 7.1: Verify application package integrity and anti-tampering
        val signatureResult = AntiTamperHelper.verifyAppSignature(this)
        if (!signatureResult.isSafe) {
            Log.e(TAG, "SECURITY ALERT: Application has been tampered with or re-signed! Status: ${signatureResult.status}")
        }

        // Warm up Room database (initializes .createFromAsset if first launch)
        applicationScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val count = db.blockedPoolDao().getCount()
                Log.i(TAG, "Room Database ready. Active blocked pools: $count")
            } catch (e: Exception) {
                Log.e(TAG, "Error warming up local database: ${e.message}", e)
            }
        }

        // Schedule periodic background sync via WorkManager
        SyncPoolsWorker.schedulePeriodicSync(this)
    }
}
