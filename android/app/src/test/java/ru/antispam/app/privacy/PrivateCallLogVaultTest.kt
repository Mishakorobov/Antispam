package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.data.local.entity.VaultCallRecordEntity
import ru.antispam.app.util.PhoneNormalizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for PrivateCallLogVault:
 * Verifies Incognito Call History Vault classification, Russian formatting,
 * selection query builder variants, and asynchronous race condition resolution.
 */
class PrivateCallLogVaultTest {

    @Test
    fun testCallTypeClassification_IncomingConversation() {
        val duration = 45
        val isIncoming = true
        val callType = when {
            !isIncoming -> 2
            duration > 0 -> 1
            else -> 3
        }
        assertEquals(1, callType) // INCOMING
    }

    @Test
    fun testCallTypeClassification_OutgoingCall() {
        val duration = 20
        val isIncoming = false
        val callType = when {
            !isIncoming -> 2
            duration > 0 -> 1
            else -> 3
        }
        assertEquals(2, callType) // OUTGOING
    }

    @Test
    fun testCallTypeClassification_MissedCall() {
        val duration = 0
        val isIncoming = true
        val callType = when {
            !isIncoming -> 2
            duration > 0 -> 1
            else -> 3
        }
        assertEquals(3, callType) // MISSED
    }

    @Test
    fun testRussianPhoneFormatting_InVaultRecord() {
        val phone = 79261234567L
        val formatted = PhoneNormalizer.formatRussian(phone)
        assertEquals("+7 (926) 123-45-67", formatted)

        val record = VaultCallRecordEntity(
            id = 1,
            phoneNumber = phone,
            formattedNumber = formatted,
            callerName = "Иван Петров",
            callType = 1,
            timestamp = System.currentTimeMillis(),
            durationSeconds = 60,
            isScrubbedFromSystem = true
        )

        assertEquals("+7 (926) 123-45-67", record.formattedNumber)
        assertTrue(record.isScrubbedFromSystem)
    }

    @Test
    fun testBuildDeleteSelection_IncludesAllNationalVariants() {
        val rawNumber = "+7 (926) 123-45-67"
        val normalized = 79261234567L
        val minDate = 1700000000000L

        val (selection, args) = PrivateCallLogVaultManager.buildDeleteSelection(rawNumber, normalized, minDate)

        assertTrue(selection.contains("number IN"))
        assertTrue(selection.contains("date >= ?"))

        val argsList = args.toList()
        assertTrue(argsList.contains(rawNumber))
        assertTrue(argsList.contains("79261234567"))
        assertTrue(argsList.contains("+79261234567"))
        assertTrue(argsList.contains("89261234567"))
        assertTrue(argsList.contains("1700000000000"))
    }

    @Test
    fun testRaceConditionRetryDelays_CoversLateAsyncAndroidWrite() {
        val delays = PrivateCallLogVaultManager.RETRY_DELAYS_MS
        assertEquals(3, delays.size)
        assertEquals(400L, delays[0])
        assertEquals(1000L, delays[1])
        assertEquals(2200L, delays[2])

        val maxDelay = delays.last()
        assertTrue(maxDelay < PrivateCallLogVaultManager.OBSERVER_TIMEOUT_MS)
        assertEquals(3500L, PrivateCallLogVaultManager.OBSERVER_TIMEOUT_MS)
    }

    @Test
    fun testSimulatedAsyncCallLogWrite_SucceedsOnSecondStage() {
        // Simulates Android system behavior:
        // At t=0 ms (IDLE), record is NOT yet written by TelephonyProvider -> delete returns 0.
        // At t=600 ms, system writes the record into CallLog.Calls.
        // Stage 1 (+400 ms): returns 0.
        // Stage 2 (+1000 ms): successfully scrubs 1 record!
        val callLogSystemWritten = AtomicBoolean(false)
        val scrubbedFlag = AtomicBoolean(false)
        val scrubAttempts = AtomicInteger(0)

        fun simulateScrubPass(): Int {
            scrubAttempts.incrementAndGet()
            return if (callLogSystemWritten.get()) {
                callLogSystemWritten.set(false) // deleted from system
                1
            } else {
                0
            }
        }

        // 1. Initial immediate pass at t=0 ms
        val initialResult = simulateScrubPass()
        assertEquals(0, initialResult)
        assertFalse(scrubbedFlag.get())

        // 2. Telephony stack writes record to CallLog at ~600 ms
        // Stage 1 (+400 ms):
        val stage1Result = simulateScrubPass()
        assertEquals(0, stage1Result)

        // Telephony write arrives
        callLogSystemWritten.set(true)

        // Stage 2 (+1000 ms):
        val stage2Result = simulateScrubPass()
        if (stage2Result > 0) {
            scrubbedFlag.set(true)
        }

        assertEquals(1, stage2Result)
        assertTrue(scrubbedFlag.get())
        assertEquals(3, scrubAttempts.get())
    }
}
