package ru.antispam.app.postcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.remote.NetworkModule
import ru.antispam.app.data.remote.dto.FalsePositiveRequestDto
import ru.antispam.app.util.ContactLookupHelper
import ru.antispam.app.util.PhoneNormalizer
import ru.antispam.app.worker.SyncPoolsWorker

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PostCallReceiver"
        private const val POST_CALL_MAX_THRESHOLD_SECONDS = 30 // < 30 seconds strictly

        private var callStartTime: Long = 0L
        private var isIncomingCall: Boolean = false
        private var currentIncomingNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!rawNumber.isNullOrBlank()) {
            currentIncomingNumber = rawNumber
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                callStartTime = System.currentTimeMillis()
                isIncomingCall = true
                Log.d(TAG, "Incoming call ringing: $rawNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (callStartTime == 0L) {
                    // Offhook without ringing indicates an OUTGOING call
                    callStartTime = System.currentTimeMillis()
                    isIncomingCall = false
                    Log.d(TAG, "Outgoing call started")
                } else {
                    Log.d(TAG, "Incoming call offhook (conversation started)")
                    // Phase 12.1: If caller is not in contacts, arm real-time Anti-Fraud Call+SMS monitor
                    val incomingNumber = currentIncomingNumber
                    if (!incomingNumber.isNullOrBlank()) {
                        val normalized = PhoneNormalizer.normalize(incomingNumber)
                        val isContact = ContactLookupHelper.isContact(context.contentResolver, incomingNumber, normalized)
                        if (!isContact) {
                            ru.antispam.app.antifraud.AntiFraudCallSmsMonitor.startActiveCallMonitoring(incomingNumber)
                        }
                    }
                }

                // Phase 5: Start Call Privacy Shield (MicGuard monitoring) during call conversation
                val privacyPrefs = context.getSharedPreferences("antispam_privacy_prefs", Context.MODE_PRIVATE)
                if (privacyPrefs.getBoolean("mic_guard_enabled", true)) {
                    ru.antispam.app.privacy.MicGuardService.startGuard(context)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Phase 5: Stop Call Privacy Shield when conversation ends
                ru.antispam.app.privacy.MicGuardService.stopGuard(context)
                // Phase 12.1: Disarm Anti-Fraud Call+SMS monitor
                ru.antispam.app.antifraud.AntiFraudCallSmsMonitor.stopActiveCallMonitoring()
                // Phase 13.2: Dismiss wearable emergency alerts
                ru.antispam.app.service.WearableAlertManager.dismissAlert(context)

                val wasIncoming = isIncomingCall
                val duration = if (callStartTime > 0) ((System.currentTimeMillis() - callStartTime) / 1000).toInt() else 0
                val number = currentIncomingNumber

                callStartTime = 0L
                isIncomingCall = false
                currentIncomingNumber = null

                if (number != null) {
                    // Phase 5.4: Archive to Incognito Vault and scrub from public CallLog if enabled
                    CoroutineScope(Dispatchers.IO).launch {
                        ru.antispam.app.privacy.PrivateCallLogVaultManager.archiveAndScrubCall(
                            context = context,
                            rawNumber = number,
                            isIncoming = wasIncoming,
                            durationSeconds = duration
                        )
                    }

                    if (wasIncoming) {
                        Log.d(TAG, "Incoming call finished: $number (duration: $duration s)")
                        checkAndTriggerPostCall(context, number, duration)
                    } else {
                        Log.d(TAG, "Outgoing call finished: $number. Checking auto-amnesty...")
                        checkOutgoingCallAmnesty(context, number)
                    }
                }
            }
        }
    }

    private fun checkAndTriggerPostCall(context: Context, rawNumber: String, durationSeconds: Int) {
        // Enforce threshold strictly LESS THAN 30 seconds (1..29s)
        if (durationSeconds !in 1 until POST_CALL_MAX_THRESHOLD_SECONDS) {
            Log.d(TAG, "Call duration ($durationSeconds s) is >= 30s or <= 0s. Skipping post-call.")
            return
        }

        val normalized = PhoneNormalizer.normalize(rawNumber) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)

            // Check if contact or already whitelisted
            val isContact = ContactLookupHelper.isContact(context.contentResolver, rawNumber, normalized)
            val isWhitelisted = db.localWhitelistDao().isWhitelisted(normalized)
            val isAlreadyBlocked = db.blockedPoolDao().isNumberBlocked(normalized)

            // Smart filter: check if user called this number in the last 30 days
            val hasRecentOutgoing = hasRecentOutgoingCall(context, rawNumber, normalized)

            if (!isContact && !isWhitelisted && !isAlreadyBlocked && !hasRecentOutgoing) {
                Log.i(TAG, "Triggering Post-Call notification for unknown caller: +$normalized ($durationSeconds s)")
                PostCallNotificationHelper.showPostCallNotification(context, normalized, durationSeconds)
            } else {
                Log.d(TAG, "Post-Call skipped (Contact=$isContact, Whitelist=$isWhitelisted, Blocked=$isAlreadyBlocked, RecentOutgoing=$hasRecentOutgoing)")
            }
        }
    }

    private fun hasRecentOutgoingCall(context: Context, rawNumber: String, normalized: Long): Boolean {
        val resolver = context.contentResolver
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)
        return try {
            val cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), thirtyDaysAgo.toString()),
                null
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                while (it.moveToNext()) {
                    val num = it.getString(numIdx) ?: continue
                    val norm = PhoneNormalizer.normalize(num)
                    if (num == rawNumber || (norm != null && norm == normalized)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun checkOutgoingCallAmnesty(context: Context, rawNumber: String) {
        val normalized = PhoneNormalizer.normalize(rawNumber) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            if (db.blockedPoolDao().isNumberBlocked(normalized)) {
                Log.i(TAG, "Outgoing call to blocked number +$normalized detected! Submitting auto-amnesty signal...")
                try {
                    val devHash = SyncPoolsWorker.getOrCreateDeviceHash(context)
                    val api = NetworkModule.apiService
                    api.submitFalsePositive(
                        FalsePositiveRequestDto(
                            phoneNumber = normalized.toString(),
                            deviceHash = devHash,
                            reason = "OUTGOING_CALL"
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to submit auto-amnesty: ${e.message}")
                }
            }
        }
    }
}
