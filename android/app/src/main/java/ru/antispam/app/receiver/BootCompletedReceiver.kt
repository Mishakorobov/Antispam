package ru.antispam.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.worker.SyncPoolsWorker

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "Device boot completed. Pre-warming Room database and scheduling sync...")

            // Pre-warm database in background so the first call encounters zero disk delay
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val count = db.blockedPoolDao().getCount()
                    Log.i(TAG, "Room database pre-warmed successfully. Active pools: $count")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during boot database warm-up: ${e.message}", e)
                }
            }

            // Ensure background sync schedule is active
            SyncPoolsWorker.schedulePeriodicSync(context)
        }
    }
}
