package ru.antispam.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests anti-spoofing logic for Call.Details.callerNumberVerificationStatus.
 * Connection.VERIFICATION_STATUS_NOT_VERIFIED = 0
 * Connection.VERIFICATION_STATUS_PASSED = 1
 * Connection.VERIFICATION_STATUS_FAILED = 2
 */
class CallerVerificationStatusTest {

    companion object {
        const val VERIFICATION_STATUS_NOT_VERIFIED = 0
        const val VERIFICATION_STATUS_PASSED = 1
        const val VERIFICATION_STATUS_FAILED = 2
    }

    data class ScreeningDecision(
        val disallowCall: Boolean,
        val rejectCall: Boolean,
        val reason: String,
        val shouldReportToCommunityPools: Boolean
    )

    private fun evaluateCallerVerification(verificationStatus: Int): ScreeningDecision? {
        return if (verificationStatus == VERIFICATION_STATUS_FAILED) {
            ScreeningDecision(
                disallowCall = true,
                rejectCall = true,
                reason = "BLOCKED_SPOOFED_CALLER_ID",
                // Critical: Spoofed calls must NEVER poison collective community pools
                shouldReportToCommunityPools = false
            )
        } else {
            // Null means proceed to next pipeline step (whitelist / pool database lookup)
            null
        }
    }

    @Test
    fun testSpoofedCallerIsBlockedAndDoesNotPoisonPools() {
        val decision = evaluateCallerVerification(VERIFICATION_STATUS_FAILED)
        assertTrue("Spoofed call must be intercepted", decision != null)
        assertTrue("Spoofed call must be disallowed", decision!!.disallowCall)
        assertTrue("Spoofed call must be rejected", decision.rejectCall)
        assertEquals("BLOCKED_SPOOFED_CALLER_ID", decision.reason)
        assertFalse("Must never submit spoofed number to collective pools", decision.shouldReportToCommunityPools)
    }

    @Test
    fun testVerifiedOrUnverifiedCallProceedsToNormalScreening() {
        // Normal verified call (e.g. valid mobile operator call)
        val passedDecision = evaluateCallerVerification(VERIFICATION_STATUS_PASSED)
        assertNull("Verified calls proceed to whitelist/database screening", passedDecision)

        // Legacy or unverified call (e.g. operator has not deployed STIR/SHAKEN or EPVV yet)
        val notVerifiedDecision = evaluateCallerVerification(VERIFICATION_STATUS_NOT_VERIFIED)
        assertNull("Unverified calls proceed to whitelist/database screening", notVerifiedDecision)
    }
}
