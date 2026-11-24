package ru.antispam.app.ui.calllog

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.data.local.entity.BlockedCallRecordEntity
import ru.antispam.app.data.local.entity.BlockedPoolEntity

class CallLogCancellationStatusTest {

    private val samplePool = BlockedPoolEntity(
        rangeStart = 79990001000L,
        rangeEnd = 79990001099L,
        operator = "Тест Телеком",
        category = "SURVEY",
        updatedAt = 1000L
    )

    @Test
    fun testAuditSpamPoolMatchMarksCancelledByDatabase() {
        val now = 1_700_000_000_000L
        val audit = BlockedCallRecordEntity(
            phoneNumber = 79990001050L,
            formattedNumber = "+7 (999) 000-10-50",
            operatorName = "Тест Телеком",
            category = "SURVEY",
            blockReason = "SPAM_POOL",
            timestamp = now - 500L
        )

        val status = CallLogViewModel.resolveCallCancellationStatus(
            callType = CallLog.Calls.BLOCKED_TYPE,
            blockReasonColumn = CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
            screeningComponent = "ru.antispam.app/.service.AntiSpamCallScreeningService",
            appPackageName = "ru.antispam.app",
            callTimestamp = now,
            pool = samplePool,
            auditRecordsForNumber = listOf(audit)
        )

        assertTrue(status.isCancelledByApp)
        assertTrue(status.isCancelledByDatabase)
        assertEquals("SPAM_POOL", status.cancelReasonCode)
        assertEquals("SURVEY", status.cancelCategory)
        assertEquals("Тест Телеком", status.operatorName)
    }

    @Test
    fun testSystemScreeningMatchFallsBackToPoolWhenAuditPruned() {
        val status = CallLogViewModel.resolveCallCancellationStatus(
            callType = CallLog.Calls.BLOCKED_TYPE,
            blockReasonColumn = CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
            screeningComponent = "ru.antispam.app/ru.antispam.app.service.AntiSpamCallScreeningService",
            appPackageName = "ru.antispam.app",
            callTimestamp = 1_700_000_000_000L,
            pool = samplePool,
            auditRecordsForNumber = emptyList()
        )

        assertTrue(status.isCancelledByApp)
        assertTrue(status.isCancelledByDatabase)
        assertEquals("SPAM_POOL", status.cancelReasonCode)
    }

    @Test
    fun testOutgoingCallToPooledNumberIsNotMarkedCancelled() {
        val now = 1_700_000_000_000L
        val audit = BlockedCallRecordEntity(
            phoneNumber = 79990001050L,
            formattedNumber = "+7 (999) 000-10-50",
            operatorName = "Тест Телеком",
            category = "SURVEY",
            blockReason = "SPAM_POOL",
            timestamp = now - 5_000L
        )

        val status = CallLogViewModel.resolveCallCancellationStatus(
            callType = CallLog.Calls.OUTGOING_TYPE,
            blockReasonColumn = CallLog.Calls.BLOCK_REASON_NOT_BLOCKED,
            screeningComponent = null,
            appPackageName = "ru.antispam.app",
            callTimestamp = now,
            pool = samplePool,
            auditRecordsForNumber = listOf(audit)
        )

        assertFalse(status.isCancelledByApp)
        assertFalse(status.isCancelledByDatabase)
        assertTrue(status.isBlockedPool)
    }

    @Test
    fun testSilencedDeliveryCallIsNotMarkedCancelled() {
        val now = 1_700_000_000_000L
        val audit = BlockedCallRecordEntity(
            phoneNumber = 79990001050L,
            formattedNumber = "+7 (999) 000-10-50",
            operatorName = "Курьерская АТС",
            category = "SPAM",
            blockReason = "SILENCED_DELIVERY",
            timestamp = now - 200L
        )

        val status = CallLogViewModel.resolveCallCancellationStatus(
            callType = CallLog.Calls.INCOMING_TYPE,
            blockReasonColumn = CallLog.Calls.BLOCK_REASON_NOT_BLOCKED,
            screeningComponent = null,
            appPackageName = "ru.antispam.app",
            callTimestamp = now,
            pool = samplePool,
            auditRecordsForNumber = listOf(audit)
        )

        assertFalse(status.isCancelledByApp)
        assertFalse(status.isCancelledByDatabase)
        assertEquals("SILENCED_DELIVERY", status.cancelReasonCode)
    }

    @Test
    fun testSpoofedCallIsCancelledByAppButNotByDatabase() {
        val now = 1_700_000_000_000L
        val audit = BlockedCallRecordEntity(
            phoneNumber = 79990001050L,
            formattedNumber = "+7 (999) 000-10-50",
            operatorName = null,
            category = "SPOOFING",
            blockReason = "SPOOFED_CALLER_ID",
            timestamp = now - 300L
        )

        val status = CallLogViewModel.resolveCallCancellationStatus(
            callType = CallLog.Calls.BLOCKED_TYPE,
            blockReasonColumn = CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE,
            screeningComponent = "ru.antispam.app/.service.AntiSpamCallScreeningService",
            appPackageName = "ru.antispam.app",
            callTimestamp = now,
            pool = null,
            auditRecordsForNumber = listOf(audit)
        )

        assertTrue(status.isCancelledByApp)
        assertFalse(status.isCancelledByDatabase)
        assertEquals("SPOOFED_CALLER_ID", status.cancelReasonCode)
    }
}
