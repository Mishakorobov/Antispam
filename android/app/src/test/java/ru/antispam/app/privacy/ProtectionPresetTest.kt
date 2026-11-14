package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.ProtectionPreset

class ProtectionPresetTest {

    @Test
    fun test_fromString_resolvesCorrectPreset() {
        assertEquals(ProtectionPreset.BASIC_COMFORT, ProtectionPreset.fromString("BASIC_COMFORT"))
        assertEquals(ProtectionPreset.BASIC_COMFORT, ProtectionPreset.fromString("basic_comfort"))
        assertEquals(ProtectionPreset.ANTI_EAVESDROPPING, ProtectionPreset.fromString("ANTI_EAVESDROPPING"))
        assertEquals(ProtectionPreset.MAX_BUNKER, ProtectionPreset.fromString("MAX_BUNKER"))
        assertEquals(ProtectionPreset.MAX_BUNKER, ProtectionPreset.fromString("max_bunker"))

        // Default fallbacks
        assertEquals(ProtectionPreset.ANTI_EAVESDROPPING, ProtectionPreset.fromString(null))
        assertEquals(ProtectionPreset.ANTI_EAVESDROPPING, ProtectionPreset.fromString("NON_EXISTENT_PRESET"))
    }

    @Test
    fun test_presetAttributes_areDescriptiveAndNotEmpty() {
        for (preset in ProtectionPreset.entries) {
            assertTrue("Title should not be blank for ${preset.name}", preset.title.isNotBlank())
            assertTrue("Subtitle should not be blank for ${preset.name}", preset.subtitle.isNotBlank())
            assertTrue("Description should not be blank for ${preset.name}", preset.description.isNotBlank())
        }
    }

    @Test
    fun test_basicComfortConfiguration() {
        val config = ProtectionPreset.BASIC_COMFORT.getConfiguration()

        assertTrue(config.blockSurveys)
        assertTrue(config.blockFinancial)
        assertTrue(config.blockSilentCalls)
        assertTrue(config.postCallEnabled)

        // Privacy shields are off in basic comfort mode
        assertFalse(config.micGuardEnabled)
        assertFalse(config.jammerAutoEnabled)
        assertFalse(config.clipboardGuardEnabled)
        assertFalse(config.callVaultEnabled)

        // Delivery mode is active (+2 hours bypass for couriers/taxis)
        assertTrue(config.deliveryModeActive)
        assertFalse(config.blockForeignPlus7Active)
    }

    @Test
    fun test_antiEavesdroppingConfiguration() {
        val config = ProtectionPreset.ANTI_EAVESDROPPING.getConfiguration()

        assertTrue(config.blockSurveys)
        assertTrue(config.blockFinancial)
        assertTrue(config.blockSilentCalls)
        assertTrue(config.postCallEnabled)

        // Anti-eavesdropping shields active
        assertTrue(config.micGuardEnabled)
        assertTrue(config.jammerAutoEnabled)
        assertTrue(config.clipboardGuardEnabled)
        assertFalse(config.callVaultEnabled)

        // Delivery bypass disabled to prevent spam courier spoofing
        assertFalse(config.deliveryModeActive)
        assertFalse(config.blockForeignPlus7Active)
    }

    @Test
    fun test_maxBunkerConfiguration() {
        val config = ProtectionPreset.MAX_BUNKER.getConfiguration()

        assertTrue(config.blockSurveys)
        assertTrue(config.blockFinancial)
        assertTrue(config.blockSilentCalls)

        // Stealth mode: post-call popups hidden to avoid snooping
        assertFalse("Post-call notifications must be hidden in Max Bunker mode", config.postCallEnabled)

        // All privacy shields active
        assertTrue(config.micGuardEnabled)
        assertTrue(config.jammerAutoEnabled)
        assertTrue(config.clipboardGuardEnabled)

        // Encrypted call log vault active
        assertTrue(config.callVaultEnabled)

        // Delivery bypass disabled & foreign +7 spoofing blocked
        assertFalse(config.deliveryModeActive)
        assertTrue(config.blockForeignPlus7Active)
    }

    @Test
    fun test_presetManagerConstants() {
        assertEquals("antispam_settings", ProtectionPresetManager.PREFS_SETTINGS)
        assertEquals("antispam_privacy_prefs", ProtectionPresetManager.PREFS_PRIVACY)
        assertEquals("active_protection_preset", ProtectionPresetManager.KEY_ACTIVE_PRESET)
        assertEquals("block_surveys", ProtectionPresetManager.KEY_BLOCK_SURVEYS)
        assertEquals("block_financial", ProtectionPresetManager.KEY_BLOCK_FINANCIAL)
        assertEquals("block_silent", ProtectionPresetManager.KEY_BLOCK_SILENT)
        assertEquals("post_call_enabled", ProtectionPresetManager.KEY_POST_CALL_ENABLED)
        assertEquals("mic_guard_enabled", ProtectionPresetManager.KEY_MIC_GUARD_ENABLED)
        assertEquals("mic_jammer_auto_enabled", ProtectionPresetManager.KEY_JAMMER_AUTO_ENABLED)
        assertEquals("call_incognito_vault_enabled", ProtectionPresetManager.KEY_VAULT_ENABLED)
    }
}
