package ru.antispam.app.service

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.BlockedCallRecordEntity
import ru.antispam.app.util.ContactLookupHelper
import ru.antispam.app.util.DeliveryModeManager
import ru.antispam.app.util.PhoneNormalizer
import ru.antispam.app.worker.SyncPoolsWorker

class AntiSpamCallScreeningService : CallScreeningService() {

    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    companion object {
        private const val TAG = "AntiSpamScreener"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val rawHandle: Uri? = callDetails.handle
        val rawNumber = rawHandle?.schemeSpecificPart

        // Opportunistically sync if database has not updated in > 24 hours (bypasses aggressive OEM sleep)
        checkOpportunisticSync()

        if (rawNumber.isNullOrBlank()) {
            allowCall(callDetails)
            return
        }

        val normalizedNumber = PhoneNormalizer.normalize(rawNumber)
        if (normalizedNumber == null) {
            allowCall(callDetails)
            return
        }

        // Step 0: Verification status check (Anti-Spoofing / ЕПВВ "Антифрод")
        // If carrier signals caller ID was forged, block immediately without poisoning community pools
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (callDetails.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
                val durationMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                Log.w(TAG, "BLOCKED (Spoofed Caller ID / Verification Failed): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")

                recordBlockedCall(
                    phoneNumber = normalizedNumber,
                    rawNumber = rawNumber,
                    operator = null,
                    category = "SPOOFING",
                    reason = "SPOOFED_CALLER_ID"
                )

                val response = CallResponse.Builder()
                    .setDisallowCall(true)      // Intercept and prevent call ringing
                    .setRejectCall(true)        // Reject the incoming call
                    .setSkipCallLog(false)      // Keep visible entry in call log for user transparency
                    .setSkipNotification(true)  // Do not play ringtone or wake up screen
                    .build()

                respondToCall(callDetails, response)
                return
            }
        }

        // Step 1: Instant check for contacts or local whitelist (< 1ms with LRU cache)
        val isContact = ContactLookupHelper.isContact(contentResolver, rawNumber, normalizedNumber)
        val isWhitelisted = db.localWhitelistDao().isWhitelisted(normalizedNumber)

        if (isContact || isWhitelisted) {
            val durationMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            Log.d(TAG, "ALLOWED (Contact/Whitelist): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")
            allowCall(callDetails)
            return
        }

        // Step 1.5: VIP Mode ("Только контакты" / "Ночной купол")
        // Silences any incoming calls not in contacts (emergency & banks still pass through via whitelist)
        if (ru.antispam.app.util.VipModeManager.isVipModeActive(applicationContext)) {
            val durationMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            Log.i(TAG, "SILENCED (VIP Mode Active): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")

            recordBlockedCall(
                phoneNumber = normalizedNumber,
                rawNumber = rawNumber,
                operator = null,
                category = "VIP_MODE",
                reason = "SILENCED_VIP_MODE"
            )

            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(true)
                .setSkipNotification(true)
                .build()

            respondToCall(callDetails, response)
            return
        }

        // Step 1.6: Foreign +7 Filter (Kazakhstan / Abkhazia 6xx, 7xx)
        // Hard drop unrecognized cross-border calls while preserving saved contacts and whitelist
        if (ru.antispam.app.util.KazakhstanBlockManager.isBlockForeignPlus7Active(applicationContext) &&
            PhoneNormalizer.isKazakhstanOrForeignPlus7(normalizedNumber)
        ) {
            val durationMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            Log.w(TAG, "BLOCKED (Foreign +7 / Kazakhstan): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")

            recordBlockedCall(
                phoneNumber = normalizedNumber,
                rawNumber = rawNumber,
                operator = "Казахстан (+7)",
                category = "FOREIGN_PLUS7",
                reason = "BLOCKED_FOREIGN_PLUS7"
            )

            val response = CallResponse.Builder()
                .setDisallowCall(true)      // Intercept and prevent call ringing
                .setRejectCall(true)        // Reject the incoming call
                .setSkipCallLog(false)      // Keep visible entry in call log for user transparency
                .setSkipNotification(true)  // Do not play ringtone or wake up screen
                .build()

            respondToCall(callDetails, response)
            return
        }

        // Step 2: High-speed O(log N) interval lookup in Room SQLite
        val pool = db.blockedPoolDao().getPoolForNumber(normalizedNumber)
        val durationMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

        if (pool != null) {
            val isDeliveryMode = DeliveryModeManager.isDeliveryModeActive(applicationContext)

            if (isDeliveryMode) {
                // Courier / Delivery mode: mute ringer (quiet call) but do not reject
                // Allows couriers and taxi drivers on virtual PBX to connect
                Log.i(TAG, "SILENCED (Delivery Mode Active): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")

                recordBlockedCall(
                    phoneNumber = normalizedNumber,
                    rawNumber = rawNumber,
                    operator = pool.operator,
                    category = pool.category,
                    reason = "SILENCED_DELIVERY"
                )

                val response = CallResponse.Builder()
                    .setDisallowCall(false)     // Allow call to come through
                    .setSilenceCall(true)       // Mute the ringer sound
                    .setSkipNotification(false) // Keep visual incoming call screen visible
                    .setSkipCallLog(false)
                    .build()

                respondToCall(callDetails, response)
            } else {
                // Standard mode: hard reject
                Log.i(TAG, "BLOCKED (Spam Pool Match): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")

                recordBlockedCall(
                    phoneNumber = normalizedNumber,
                    rawNumber = rawNumber,
                    operator = pool.operator,
                    category = pool.category,
                    reason = "SPAM_POOL"
                )

                val response = CallResponse.Builder()
                    .setDisallowCall(true)      // Intercept and prevent call ringing
                    .setRejectCall(true)        // Reject the incoming call
                    .setSkipCallLog(false)      // Keep visible entry in call log for user transparency
                    .setSkipNotification(true)  // Do not play ringtone or wake up screen
                    .build()

                respondToCall(callDetails, response)
            }
        } else {
            Log.d(TAG, "ALLOWED (Clean Number): +$normalizedNumber in ${"%.2f".format(durationMs)} ms")
            allowCall(callDetails)
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder().build()
        respondToCall(callDetails, response)
    }

    private fun recordBlockedCall(
        phoneNumber: Long,
        rawNumber: String,
        operator: String?,
        category: String?,
        reason: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                db.blockedCallRecordDao().insert(
                    BlockedCallRecordEntity(
                        phoneNumber = phoneNumber,
                        formattedNumber = PhoneNormalizer.formatPretty(rawNumber),
                        operatorName = operator,
                        category = category,
                        blockReason = reason,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record blocked call in audit log: ${e.message}")
            }
        }
    }

    private fun checkOpportunisticSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastSync = db.syncMetadataDao().getValueLong("last_sync_timestamp") ?: 0L
                val now = System.currentTimeMillis()
                if (now - lastSync > 24 * 3600 * 1000L) {
                    Log.i(TAG, "Database is > 24 hours stale. Enqueueing opportunistic sync...")
                    SyncPoolsWorker.triggerOpportunisticSync(applicationContext)
                }
            } catch (_: Exception) {}
        }
    }
}
