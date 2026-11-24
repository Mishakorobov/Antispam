package ru.antispam.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.postcall.PhoneStateReceiver
import ru.antispam.app.util.OfficialWhitelistRegistry

class OfficialWhitelistAndCategoryScreeningTest {

    @Test
    fun testOfficialWhitelistRegistryRespectsEnabledGroupKeys() {
        val enabled = setOf("EMERGENCY", "GOV", "SBERBANK", "TBANK", "VTB", "ALFABANK")
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78005555550L, enabled)) // Sberbank
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78005557778L, enabled)) // T-Bank
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78001007010L, enabled)) // Gosuslugi
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78001002424L, enabled)) // VTB
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78002000000L, enabled)) // Alfa
        assertFalse(OfficialWhitelistRegistry.isWhitelistedInGroups(79991234567L, enabled))

        val sberDisabled = enabled - "SBERBANK"
        assertFalse(OfficialWhitelistRegistry.isWhitelistedInGroups(78005555550L, sberDisabled))
        assertTrue(OfficialWhitelistRegistry.isWhitelistedInGroups(78005557778L, sberDisabled))
    }

    @Test
    fun testShouldBlockPoolCategoryHonorsUserToggles() {
        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory("SURVEY", true, true, true))
        assertFalse(AntiSpamCallScreeningService.shouldBlockPoolCategory("SURVEY", false, true, true))

        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory("FINANCIAL", true, true, true))
        assertFalse(AntiSpamCallScreeningService.shouldBlockPoolCategory("FINANCIAL", true, false, true))

        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory("SILENT", true, true, true))
        assertFalse(AntiSpamCallScreeningService.shouldBlockPoolCategory("SILENT", true, true, false))

        // Core spam/spoofing categories always remain blocked
        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory("SPAM", false, false, false))
        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory("SPOOFING", false, false, false))
        assertTrue(AntiSpamCallScreeningService.shouldBlockPoolCategory(null, false, false, false))
    }

    @Test
    fun testPostCallTriggerRespectsToggleAndCustomThreshold() {
        assertFalse(PhoneStateReceiver.shouldTriggerPostCallForDuration(10, postCallEnabled = false, thresholdSeconds = 30))
        assertFalse(PhoneStateReceiver.shouldTriggerPostCallForDuration(0, postCallEnabled = true, thresholdSeconds = 30))
        assertTrue(PhoneStateReceiver.shouldTriggerPostCallForDuration(14, postCallEnabled = true, thresholdSeconds = 15))
        assertFalse(PhoneStateReceiver.shouldTriggerPostCallForDuration(15, postCallEnabled = true, thresholdSeconds = 15))
        assertTrue(PhoneStateReceiver.shouldTriggerPostCallForDuration(29, postCallEnabled = true, thresholdSeconds = 30))
        assertFalse(PhoneStateReceiver.shouldTriggerPostCallForDuration(30, postCallEnabled = true, thresholdSeconds = 30))
    }
}
