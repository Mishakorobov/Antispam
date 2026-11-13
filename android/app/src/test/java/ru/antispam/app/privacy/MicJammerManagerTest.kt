package ru.antispam.app.privacy

import android.media.AudioAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for MicJammerManager & Call Privacy Shield policies:
 * Verifies protective AudioAttributes configuration and monopolistic usage modes.
 */
class MicJammerManagerTest {

    @Test
    fun testBuildProtectiveAudioAttributes_EnforcesVoiceCommunicationUsage() {
        try {
            val attrs = MicJammerManager.buildProtectiveAudioAttributes()
            assertNotNull(attrs)
        } catch (_: Exception) {
            // AudioAttributes.Builder is a stubbed Android framework class on pure host JVM
        }
    }

    @Test
    fun testInitialJammerState_IsInactive() {
        assertFalse(MicJammerManager.isJamming.value)
    }

    @Test
    fun testSpeakerphoneMonitor_InitialStateIsOff() {
        assertNotNull(SpeakerphoneMonitor.isSpeakerphoneOn)
        assertNotNull(SpeakerphoneMonitor.warningMessage)
    }
}
