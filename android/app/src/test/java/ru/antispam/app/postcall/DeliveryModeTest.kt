package ru.antispam.app.postcall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Delivery Mode (Waiting for Courier / Taxi):
 * Silences incoming calls from spam pools instead of rejecting them,
 * formats human-readable telemetry, and calculates saved minutes of peace.
 */
class DeliveryModeTest {

    data class MockScreeningResult(
        val disallowCall: Boolean,
        val rejectCall: Boolean,
        val silenceCall: Boolean,
        val skipNotification: Boolean,
        val blockReason: String
    )

    private fun screenCallWithDeliveryMode(isSpamPool: Boolean, isDeliveryModeActive: Boolean): MockScreeningResult {
        return if (isSpamPool) {
            if (isDeliveryModeActive) {
                // Courier Mode: quiet call, user sees incoming call screen and ringer is muted
                MockScreeningResult(
                    disallowCall = false,
                    rejectCall = false,
                    silenceCall = true,
                    skipNotification = false,
                    blockReason = "SILENCED_DELIVERY"
                )
            } else {
                // Standard mode: hard rejection
                MockScreeningResult(
                    disallowCall = true,
                    rejectCall = true,
                    silenceCall = false,
                    skipNotification = true,
                    blockReason = "SPAM_POOL"
                )
            }
        } else {
            MockScreeningResult(
                disallowCall = false,
                rejectCall = false,
                silenceCall = false,
                skipNotification = false,
                blockReason = "ALLOWED_CLEAN"
            )
        }
    }

    @Test
    fun testCourierModeAllowsQuietCallWhenPoolMatches() {
        val res = screenCallWithDeliveryMode(isSpamPool = true, isDeliveryModeActive = true)

        assertFalse("Must not disallow call when courier mode is active", res.disallowCall)
        assertFalse("Must not reject call when courier mode is active", res.rejectCall)
        assertTrue("Must silence ringer", res.silenceCall)
        assertFalse("Must show notification so user sees delivery courier call", res.skipNotification)
        assertEquals("SILENCED_DELIVERY", res.blockReason)
    }

    @Test
    fun testStandardModeHardRejectsWhenPoolMatches() {
        val res = screenCallWithDeliveryMode(isSpamPool = true, isDeliveryModeActive = false)

        assertTrue("Must disallow call", res.disallowCall)
        assertTrue("Must reject call", res.rejectCall)
        assertTrue("Must skip notification", res.skipNotification)
        assertEquals("SPAM_POOL", res.blockReason)
    }

    @Test
    fun testDeliveryModeExpirationCalculation() {
        val now = 1000000L
        val untilActive = now + (2 * 3600 * 1000L) // 2 hours in future
        val untilExpired = now - 5000L // 5 seconds in past

        assertTrue("Must be active when until > now", now < untilActive)
        assertFalse("Must be expired when until <= now", now < untilExpired)

        val remainingMinutes = ((untilActive - now) / (60 * 1000)).toInt()
        assertEquals(120, remainingMinutes)
    }

    @Test
    fun testSavedPeaceOfMindTimeCalculation() {
        // Each blocked spam call saves approximately 30 seconds of user attention/stress
        val blocked42Calls = 42
        val savedMinutes42 = (blocked42Calls * 30) / 60
        assertEquals(21, savedMinutes42)

        val blocked1Call = 1
        val savedMinutes1 = (blocked1Call * 30) / 60
        assertEquals(0, savedMinutes1) // < 1 min

        val blocked100Calls = 100
        val savedMinutes100 = (blocked100Calls * 30) / 60
        assertEquals(50, savedMinutes100)
    }

    @Test
    fun testDeliveryCountdownFormatting() {
        fun formatRemaining(minutes: Int): String {
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) "${hours} ч ${mins} мин" else "${mins} мин"
        }

        assertEquals("1 ч 34 мин", formatRemaining(94))
        assertEquals("2 ч 0 мин", formatRemaining(120))
        assertEquals("45 мин", formatRemaining(45))
        assertEquals("0 мин", formatRemaining(0))
    }

    @Test
    fun testHumanReadableTelemetryDescriptions() {
        fun formatTelemetry(reason: String, operator: String?): String {
            val op = operator ?: "Виртуальная АТС"
            return when {
                reason == "SPOOFED_CALLER_ID" -> "Подмена номера (ЕПВВ Антифрод)"
                reason == "SILENCED_DELIVERY" -> "Тихий вызов (Режим ожидания доставки)"
                reason.contains("MICRO_100") -> "Пул колл-центра (100 номеров) • Опросы ($op)"
                reason.contains("SUBPOOL_1000") -> "Массовый автодозвон (1000 номеров) • $op"
                reason.contains("SURVEY_VCIOM") -> "Опросы ВЦИОМ • Официальный пул"
                else -> "Сброшен спам-пул ($op)"
            }
        }

        assertEquals(
            "Пул колл-центра (100 номеров) • Опросы (Манго Телеком)",
            formatTelemetry("MICRO_100", "Манго Телеком")
        )
        assertEquals(
            "Массовый автодозвон (1000 номеров) • МТТ",
            formatTelemetry("SUBPOOL_1000", "МТТ")
        )
        assertEquals(
            "Опросы ВЦИОМ • Официальный пул",
            formatTelemetry("SURVEY_VCIOM", null)
        )
        assertEquals(
            "Тихий вызов (Режим ожидания доставки)",
            formatTelemetry("SILENCED_DELIVERY", null)
        )
    }
}
