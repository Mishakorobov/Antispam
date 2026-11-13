package ru.antispam.app.privacy

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.VaultCallRecordEntity
import ru.antispam.app.util.ContactLookupHelper
import ru.antispam.app.util.PhoneNormalizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PrivateCallLogVaultManager: Incognito Call History Vault.
 * Protects users from social graph harvesting and call metadata profiling
 * by super-apps and messengers (e.g. MAX, aggressive banking/ad SDKs) that read CallLog.Calls.
 * When enabled, moves sensitive call logs from the public Android system CallLog
 * into AntiSpam's private, encrypted Room database.
 *
 * Phase 6.1 (Race Condition Elimination):
 * Eliminates the Android TelephonyProvider asynchronous insertion race condition:
 * Android writes completed calls to CallLog.Calls 300ms–1500ms AFTER TelephonyManager.EXTRA_STATE_IDLE.
 * Uses a dual-layer strategy:
 * 1. Short-lived ContentObserver on CallLog.Calls.CONTENT_URI (3500ms timeout).
 * 2. Multi-stage scheduled retry sweeps (+400ms, +1000ms, +2200ms) with Room DB state synchronization.
 */
object PrivateCallLogVaultManager {

    private const val TAG = "CallLogVault"
    private const val PREFS_NAME = "antispam_privacy_prefs"
    private const val KEY_VAULT_ENABLED = "call_incognito_vault_enabled"

    // Multi-stage retry delays for catching asynchronous system write
    val RETRY_DELAYS_MS = listOf(400L, 1000L, 2200L)
    const val OBSERVER_TIMEOUT_MS = 3500L

    fun isVaultEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VAULT_ENABLED, false)
    }

    fun setVaultEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VAULT_ENABLED, enabled)
            .apply()
        Log.i(TAG, "Private Call Log Vault enabled = $enabled")
    }

    /**
     * Builds selection query and arguments matching raw, international, and local national variations
     * of the phone number within the given time window.
     */
    fun buildDeleteSelection(rawNumber: String, normalized: Long, minDate: Long): Pair<String, Array<String>> {
        val numbers = linkedSetOf<String>()
        if (rawNumber.isNotBlank()) {
            numbers.add(rawNumber.trim())
        }
        val normStr = normalized.toString()
        numbers.add(normStr)
        numbers.add("+$normStr")
        if (normStr.length == 11 && (normStr.startsWith("7") || normStr.startsWith("8"))) {
            val tail = normStr.substring(1)
            numbers.add("8$tail")
            numbers.add("+7$tail")
            numbers.add("7$tail")
        }

        val placeholders = numbers.joinToString(",") { "?" }
        val selection = "${CallLog.Calls.NUMBER} IN ($placeholders) AND ${CallLog.Calls.DATE} >= ?"
        val args = (numbers + minDate.toString()).toTypedArray()
        return Pair(selection, args)
    }

    /**
     * Attempts a single deletion pass from CallLog.Calls.
     * Returns the number of rows deleted.
     */
    fun executeScrubPass(
        contentResolver: ContentResolver,
        rawNumber: String,
        normalized: Long,
        timestamp: Long
    ): Int {
        return try {
            val minDate = timestamp - 120_000 // Within +/- 2 minutes
            val (selection, args) = buildDeleteSelection(rawNumber, normalized, minDate)
            val deleted = contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, args)
            if (deleted > 0) {
                Log.i(TAG, "Scrubbed $deleted row(s) from CallLog for $rawNumber (norm: $normalized)")
            }
            deleted
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_CALL_LOG not granted: ${e.message}")
            0
        } catch (e: Exception) {
            Log.e(TAG, "Error executing CallLog scrub: ${e.message}")
            0
        }
    }

    /**
     * Archives a completed call into the private database vault
     * and guarantees elimination of the race condition via ContentObserver & staged delayed sweeps.
     */
    suspend fun archiveAndScrubCall(
        context: Context,
        rawNumber: String,
        isIncoming: Boolean,
        durationSeconds: Int,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isVaultEnabled(context)) {
            return@withContext false
        }

        val normalized = PhoneNormalizer.normalize(rawNumber) ?: return@withContext false
        val contactName = ContactLookupHelper.getContactDisplayName(context.contentResolver, rawNumber, normalized)

        val callType = when {
            !isIncoming -> 2 // OUTGOING
            durationSeconds > 0 -> 1 // INCOMING
            else -> 3 // MISSED
        }

        // 1. Initial immediate scrub pass
        val initialDeleted = executeScrubPass(context.contentResolver, rawNumber, normalized, timestamp)
        val scrubbedInitial = initialDeleted > 0

        // 2. Insert into private Room database vault immediately
        val db = AppDatabase.getInstance(context)
        val record = VaultCallRecordEntity(
            phoneNumber = normalized,
            formattedNumber = PhoneNormalizer.formatRussian(normalized),
            callerName = contactName,
            callType = callType,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            isScrubbedFromSystem = scrubbedInitial
        )

        val recordId = db.privateCallLogDao().insertRecord(record)
        Log.i(TAG, "Call archived in vault (ID: $recordId, +$normalized, initialScrubbed=$scrubbedInitial)")

        // 3. Launch race condition defense: ContentObserver + staged delayed sweeps
        scheduleAntiRaceConditionSweeps(
            context = context.applicationContext ?: context,
            recordId = recordId,
            rawNumber = rawNumber,
            normalized = normalized,
            timestamp = timestamp,
            alreadyScrubbed = scrubbedInitial
        )

        return@withContext true
    }

    /**
     * Schedules a ContentObserver and multi-stage coroutine sweeps to guarantee
     * late-arriving asynchronous CallLog entries are scrubbed even under high system load.
     */
    fun scheduleAntiRaceConditionSweeps(
        context: Context,
        recordId: Long,
        rawNumber: String,
        normalized: Long,
        timestamp: Long,
        alreadyScrubbed: Boolean,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        val scrubbedFlag = AtomicBoolean(alreadyScrubbed)
        val isObserverActive = AtomicBoolean(true)

        // A. Register short-lived ContentObserver (Layer 1)
        var observer: ContentObserver? = null
        try {
            val handler = Handler(Looper.getMainLooper())
            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    if (!isObserverActive.get()) return
                    coroutineScope.launch {
                        val deleted = executeScrubPass(context.contentResolver, rawNumber, normalized, timestamp)
                        if (deleted > 0) {
                            scrubbedFlag.set(true)
                            updateRoomStatus(context, recordId, true)
                        }
                    }
                }
            }
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register ContentObserver: ${e.message}")
        }

        // B. Staged delayed retry sweeps (Layer 2) & safe unregistering
        coroutineScope.launch {
            for (delayMs in RETRY_DELAYS_MS) {
                delay(delayMs)
                val deleted = executeScrubPass(context.contentResolver, rawNumber, normalized, timestamp)
                if (deleted > 0) {
                    scrubbedFlag.set(true)
                    updateRoomStatus(context, recordId, true)
                }
            }

            // Keep ContentObserver active until OBSERVER_TIMEOUT_MS
            val remainingTimeout = OBSERVER_TIMEOUT_MS - RETRY_DELAYS_MS.sum()
            if (remainingTimeout > 0) {
                delay(remainingTimeout)
            }

            // Cleanup ContentObserver
            isObserverActive.set(false)
            observer?.let { obs ->
                try {
                    context.contentResolver.unregisterContentObserver(obs)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering ContentObserver: ${e.message}")
                }
            }
            Log.d(TAG, "Anti-race condition sweep cycle completed for record $recordId (Scrubbed: ${scrubbedFlag.get()})")
        }
    }

    private suspend fun updateRoomStatus(context: Context, recordId: Long, scrubbed: Boolean) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            db.privateCallLogDao().updateScrubbedStatus(recordId, scrubbed)
            Log.d(TAG, "Updated vault record $recordId isScrubbedFromSystem -> $scrubbed")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Room status for record $recordId: ${e.message}")
        }
    }
}
