package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for PrivacyScannerHelper:
 * Tests 3-tier categorization (Presumption of Innocence):
 * - System Dialers (Google/Samsung) -> SAFE / SYSTEM_DIALER
 * - Surveillance Superapps (ru.vk.max) -> CRITICAL / SUPERAPP_SURVEILLANCE
 * - Trusted Messengers (Telegram) -> HIGH / TRUSTED_MESSENGER
 */
class PrivacyScannerHelperTest {

    @Test
    fun testClassifyRisk_SuperappSurveillance_Max() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Мессенджер MAX",
            packageName = "ru.vk.max",
            hasPhoneState = true,
            hasRecordAudio = true,
            hasCallLog = false,
            hasAccessibility = false,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.CRITICAL, risk.riskLevel)
        assertEquals(PrivacyCategory.SUPERAPP_SURVEILLANCE, risk.category)
        assertTrue(risk.riskDescription.contains("Телефон + Микрофон"))
    }

    @Test
    fun testClassifyRisk_PresumptionOfInnocence_GoogleDialer() {
        // System phone dialers need Phone state and Record audio to function,
        // and must NOT be flagged as threats (Presumption of Innocence)
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Телефон Google",
            packageName = "com.google.android.dialer",
            hasPhoneState = true,
            hasRecordAudio = true,
            hasCallLog = true,
            hasAccessibility = false,
            isSystem = true
        )

        assertEquals(PrivacyRiskLevel.SAFE, risk.riskLevel)
        assertEquals(PrivacyCategory.SYSTEM_DIALER, risk.category)
        assertTrue(risk.riskDescription.contains("Исключен из угроз"))
    }

    @Test
    fun testClassifyRisk_PresumptionOfInnocence_SamsungInCallUI() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Samsung InCallUI",
            packageName = "com.samsung.android.incallui",
            hasPhoneState = true,
            hasRecordAudio = true,
            hasCallLog = true,
            hasAccessibility = false,
            isSystem = true
        )

        assertEquals(PrivacyRiskLevel.SAFE, risk.riskLevel)
        assertEquals(PrivacyCategory.SYSTEM_DIALER, risk.category)
        assertTrue(risk.riskDescription.contains("Исключен из угроз"))
    }

    @Test
    fun testClassifyRisk_TrustedMessenger_Telegram() {
        // Legitimate messengers are classified as TRUSTED_MESSENGER with amber HIGH badge (not scary red CRITICAL)
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Telegram",
            packageName = "org.telegram.messenger",
            hasPhoneState = true,
            hasRecordAudio = true,
            hasCallLog = false,
            hasAccessibility = false,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.HIGH, risk.riskLevel)
        assertEquals(PrivacyCategory.TRUSTED_MESSENGER, risk.category)
        assertTrue(risk.riskDescription.contains("VoIP"))
    }

    @Test
    fun testClassifyRisk_CriticalWhenAccessibilityAndMicPresent() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Suspicious Accessibility Tool",
            packageName = "com.spy.access",
            hasPhoneState = false,
            hasRecordAudio = true,
            hasCallLog = false,
            hasAccessibility = true,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.CRITICAL, risk.riskLevel)
        assertEquals(PrivacyCategory.SUPERAPP_SURVEILLANCE, risk.category)
        assertTrue(risk.riskDescription.contains("Спецвозможности"))
    }

    @Test
    fun testClassifyRisk_HighWhenCallLogPresent() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Caller Profile Harvester",
            packageName = "com.harvester.profile",
            hasPhoneState = false,
            hasRecordAudio = false,
            hasCallLog = true,
            hasAccessibility = false,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.HIGH, risk.riskLevel)
        assertEquals(PrivacyCategory.TRUSTED_MESSENGER, risk.category)
        assertTrue(risk.riskDescription.contains("журнал"))
    }

    @Test
    fun testClassifyRisk_MediumWhenMicOnlyPresent() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Voice Recorder",
            packageName = "com.standard.recorder",
            hasPhoneState = false,
            hasRecordAudio = true,
            hasCallLog = false,
            hasAccessibility = false,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.MEDIUM, risk.riskLevel)
        assertEquals(PrivacyCategory.TRUSTED_MESSENGER, risk.category)
        assertTrue(risk.riskDescription.contains("микрофон"))
    }

    @Test
    fun testClassifyRisk_SafeWhenNoSensitivePermissions() {
        val risk = PrivacyScannerHelper.classifyRisk(
            appName = "Calculator Clean",
            packageName = "com.clean.calc",
            hasPhoneState = false,
            hasRecordAudio = false,
            hasCallLog = false,
            hasAccessibility = false,
            isSystem = false
        )

        assertEquals(PrivacyRiskLevel.SAFE, risk.riskLevel)
        assertEquals(PrivacyCategory.SYSTEM_DIALER, risk.category)
    }
}
