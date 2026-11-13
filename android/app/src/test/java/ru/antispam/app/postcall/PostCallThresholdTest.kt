package ru.antispam.app.postcall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCallThresholdTest {

    private fun isEligibleForPostCall(durationSeconds: Int): Boolean {
        // Condition: strictly 0 < duration < 30 seconds
        return durationSeconds in 1 until 30
    }

    @Test
    fun testEligibleDurations() {
        // Less than 30 seconds
        assertTrue(isEligibleForPostCall(1))
        assertTrue(isEligibleForPostCall(12))
        assertTrue(isEligibleForPostCall(29))
    }

    @Test
    fun testIneligibleDurations() {
        // 0 seconds (missed call / not answered)
        assertFalse(isEligibleForPostCall(0))
        assertFalse(isEligibleForPostCall(-1))

        // 30 seconds or longer (considered a conscious legitimate call)
        assertFalse(isEligibleForPostCall(30))
        assertFalse(isEligibleForPostCall(31))
        assertFalse(isEligibleForPostCall(120))
    }
}
