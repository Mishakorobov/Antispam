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
import ru.antispam.app.util.OfficialWhitelistRegistry
import ru.antispam.app.util.PhoneNormalizer
import ru.antispam.app.worker.SyncPoolsWorker

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PostCallReceiver"
        private const val POST_CALL_DEFAULT_THRESHOLD_SECONDS = 30
        private const val CALL_STATE_PREFS = "antispam_call_state_prefs"
        private const val KEY_OFFHOOK_START_TIME = "offhook_start_time"
        private const val KEY_IS_INCOMING = "is_incoming_call"
        private const val KEY_ACTIVE_NUMBER = "active_call_number"

        internal fun shouldTriggerPostCallForDuration(
            durationSeconds: Int,
            postCallEnabled: Boolean,
            thresholdSeconds: Int = POST_CALL_DEFAULT_THRESHOLD_SECONDS
        ): Boolean {
            if (!postCallEnabled) return false
            val clampedThreshold = thresholdSeconds.coerceIn(5, 120)
            return durationSeconds in 1 until clampedThreshold
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val statePrefs = context.getSharedPreferences(CALL_STATE_PREFS, Context.MODE_PRIVATE)

        if (!rawNumber.isNullOrBlank()) {
            statePrefs.edit().putString(KEY_ACTIVE_NUMBER, rawNumber).apply()
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing; conversation timer starts only on OFFHOOK
                statePrefs.edit()
                    .putLong(KEY_OFFHOOK_START_TIME, 0L)
                    .putBoolean(KEY_IS_INCOMING, true)
                    .apply()
                Log.d(TAG, "Incoming call ringing: $rawNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val now = System.currentTimeMillis()
                val isIncoming = statePrefs.getBoolean(KEY_IS_INCOMING, false)
                statePrefs.edit()
                    .putLong(KEY_OFFHOOK_START_TIME, now)
                    .apply()

                val activeNumber = rawNumber?.takeIf { it.isNotBlank() }
                    ?: statePrefs.getString(KEY_ACTIVE_NUMBER, null)

                if (!isIncoming) {
                    Log.d(TAG, "Outgoing call started")
                } else {
                    Log.d(TAG, "Incoming call offhook (conversation started)")
                    // Phase 12.1: If caller is not in contacts, arm real-time Anti-Fraud Call+SMS monitor
                    if (!activeNumber.isNullOrBlank()) {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val normalized = PhoneNormalizer.normalize(activeNumber)
                                val isContact = ContactLookupHelper.isContact(
                                    context.contentResolver,
                                    activeNumber,
                                    normalized
                                )
                                if (!isContact) {
                                    ru.antispam.app.antifraud.AntiFraudCallSmsMonitor.startActiveCallMonitoring(activeNumber)
                                }
                            } finally {
                                pendingResult.finish()
                            }
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

                val offhookStartTime = statePrefs.getLong(KEY_OFFHOOK_START_TIME, 0L)
                val wasIncoming = statePrefs.getBoolean(KEY_IS_INCOMING, false)
                val number = statePrefs.getString(KEY_ACTIVE_NUMBER, null)

                val duration = if (offhookStartTime > 0L) {
                    ((System.currentTimeMillis() - offhookStartTime) / 1000).toInt().coerceAtLeast(0)
                } else {
                    0
                }

                statePrefs.edit()
                    .remove(KEY_OFFHOOK_START_TIME)
                    .remove(KEY_IS_INCOMING)
                    .remove(KEY_ACTIVE_NUMBER)
                    .apply()

                if (!number.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Phase 5.4: Archive to Incognito Vault and scrub from public CallLog if enabled
                            ru.antispam.app.privacy.PrivateCallLogVaultManager.archiveAndScrubCall(
                                context = context,
                                rawNumber = number,
                                isIncoming = wasIncoming,
                                durationSeconds = duration
                            )

                            if (wasIncoming) {
                                Log.d(TAG, "Incoming call finished: $number (duration: $duration s)")
                                checkAndTriggerPostCallSuspending(context, number, duration)
                            } else {
                                Log.d(TAG, "Outgoing call finished: $number. Checking auto-amnesty...")
                                checkOutgoingCallAmnestySuspending(context, number)
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkAndTriggerPostCallSuspending(
        context: Context,
        rawNumber: String,
        durationSeconds: Int
    ) {
        val settingsPrefs = context.getSharedPreferences("antispam_settings", Context.MODE_PRIVATE)
        val postCallEnabled = settingsPrefs.getBoolean("post_call_enabled", true)
        val thresholdSeconds = settingsPrefs.getInt("post_call_threshold", POST_CALL_DEFAULT_THRESHOLD_SECONDS)

        if (!shouldTriggerPostCallForDuration(durationSeconds, postCallEnabled, thresholdSeconds)) {
            Log.d(TAG, "Post-Call skipped (enabled=$postCallEnabled, duration=$durationSeconds s, threshold=$thresholdSeconds s)")
            return
        }

        val normalized = PhoneNormalizer.normalize(rawNumber) ?: return
        val db = AppDatabase.getInstance(context)

        // Check if contact, official whitelist group, or already whitelisted
        val isContact = ContactLookupHelper.isContact(context.contentResolver, rawNumber, normalized)
        val isWhitelisted = db.localWhitelistDao().isWhitelisted(normalized) ||
            OfficialWhitelistRegistry.isWhitelistedByEnabledGroup(context, normalized)
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

    private suspend fun checkOutgoingCallAmnestySuspending(context: Context, rawNumber: String) {
        val normalized = PhoneNormalizer.normalize(rawNumber) ?: return
        val db = AppDatabase.getInstance(context)
        if (db.blockedPoolDao().isNumberBlocked(normalized)) {
            if (NetworkModule.isServerlessMode) {
                Log.i(TAG, "Outgoing call to blocked number +$normalized detected (Serverless mode: skipping legacy API amnesty call)")
                return
            }
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
