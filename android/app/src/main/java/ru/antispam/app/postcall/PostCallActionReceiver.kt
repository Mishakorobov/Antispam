package ru.antispam.app.postcall

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.LocalWhitelistEntity
import ru.antispam.app.data.local.entity.PendingReportEntity
import ru.antispam.app.data.remote.NetworkModule
import ru.antispam.app.data.remote.dto.SingleReportRequest
import ru.antispam.app.worker.SyncPoolsWorker

class PostCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPORT = "ru.antispam.app.ACTION_REPORT"
        const val ACTION_WHITELIST = "ru.antispam.app.ACTION_WHITELIST"

        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val phoneNumber = intent.getLongExtra(EXTRA_PHONE, 0L)
        if (phoneNumber == 0L) return

        // 1. Immediately dismiss the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)

        val action = intent.action
        val db = AppDatabase.getInstance(context)

        CoroutineScope(Dispatchers.IO).launch {
            if (action == ACTION_REPORT) {
                val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "SPAM"
                val duration = intent.getIntExtra(EXTRA_DURATION, 0)
                val deviceHash = SyncPoolsWorker.getOrCreateDeviceHash(context)

                try {
                    // Try immediate upload to server
                    NetworkModule.apiService.submitReport(
                        SingleReportRequest(
                            phoneNumber = phoneNumber.toString(),
                            deviceHash = deviceHash,
                            category = category,
                            callDurationSeconds = duration
                        )
                    )
                } catch (_: Exception) {
                    // Save to local offline queue
                    db.pendingReportDao().insert(
                        PendingReportEntity(
                            phoneNumber = phoneNumber,
                            category = category,
                            callDurationSeconds = duration,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                showToastOnMain(context, "Спасибо! Номер добавлен в базу спама.")
            } else if (action == ACTION_WHITELIST) {
                db.localWhitelistDao().add(
                    LocalWhitelistEntity(
                        phoneNumber = phoneNumber,
                        label = "Добавлен из Post-Call",
                        addedAt = System.currentTimeMillis()
                    )
                )
                showToastOnMain(context, "Номер добавлен в белый список.")
            }
        }
    }

    private fun showToastOnMain(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
