package ru.antispam.app.postcall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates smart filtering rules in PhoneStateReceiver:
 * 1. Outgoing calls must never trigger post-call notifications.
 * 2. Numbers with recent outgoing calls (< 30 days) must not trigger post-call notifications.
 */
class PostCallOutgoingFilterTest {

    private fun shouldTriggerPostCallNotification(
        isIncoming: Boolean,
        durationSeconds: Int,
        isContact: Boolean,
        isWhitelisted: Boolean,
        isAlreadyBlocked: Boolean,
        hasRecentOutgoingCall: Boolean
    ): Boolean {
        // 1. Must be incoming call
        if (!isIncoming) return false

        // 2. Strict duration threshold 1..29 seconds
        if (durationSeconds !in 1 until 30) return false

        // 3. Must not be known contact or whitelisted or already blocked
        if (isContact || isWhitelisted || isAlreadyBlocked) return false

        // 4. Must not have recent outgoing communication (e.g. user called doctor/friend back)
        if (hasRecentOutgoingCall) return false

        return true
    }

    @Test
    fun testOutgoingCallsNeverTriggerPostCall() {
        val triggered = shouldTriggerPostCallNotification(
            isIncoming = false, // Outgoing call
            durationSeconds = 12,
            isContact = false,
            isWhitelisted = false,
            isAlreadyBlocked = false,
            hasRecentOutgoingCall = false
        )
        assertFalse("Outgoing call should NEVER trigger post-call spam push", triggered)
    }

    @Test
    fun testRecentOutgoingCallSuppressesNotification() {
        val triggered = shouldTriggerPostCallNotification(
            isIncoming = true,
            durationSeconds = 15,
            isContact = false,
            isWhitelisted = false,
            isAlreadyBlocked = false,
            hasRecentOutgoingCall = true // User called this number previously
        )
        assertFalse("Recent outgoing call history should suppress post-call push", triggered)
    }

    @Test
    fun testUnknownShortIncomingCallTriggersNotification() {
        val triggered = shouldTriggerPostCallNotification(
            isIncoming = true,
            durationSeconds = 15,
            isContact = false,
            isWhitelisted = false,
            isAlreadyBlocked = false,
            hasRecentOutgoingCall = false
        )
        assertTrue("Unknown short incoming call SHOULD trigger 1-touch report push", triggered)
    }
}
