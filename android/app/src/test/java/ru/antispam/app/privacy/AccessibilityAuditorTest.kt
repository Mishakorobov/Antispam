package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.AccessibilityRiskLevel

class AccessibilityAuditorTest {

    @Test
    fun test_parseEnabledServices_emptyOrNull_returnsEmptyList() {
        assertTrue(AccessibilityAuditor.parseEnabledServices(null).isEmpty())
        assertTrue(AccessibilityAuditor.parseEnabledServices("").isEmpty())
        assertTrue(AccessibilityAuditor.parseEnabledServices("   ").isEmpty())
    }

    @Test
    fun test_parseEnabledServices_colonSeparatedComponents() {
        val raw = "com.google.android.marvin.talkback/.TalkBackService:ru.vk.max/.accessibility.ScreenSpyService"
        val parsed = AccessibilityAuditor.parseEnabledServices(raw)

        assertEquals(2, parsed.size)
        assertEquals("com.google.android.marvin.talkback", parsed[0].first)
        assertEquals(".TalkBackService", parsed[0].second)

        assertEquals("ru.vk.max", parsed[1].first)
        assertEquals(".accessibility.ScreenSpyService", parsed[1].second)
    }

    @Test
    fun test_parseEnabledServices_singleComponent() {
        val raw = "ru.vk.max/ru.vk.max.accessibility.ScreenSpyService"
        val parsed = AccessibilityAuditor.parseEnabledServices(raw)

        assertEquals(1, parsed.size)
        assertEquals("ru.vk.max", parsed[0].first)
        assertEquals("ru.vk.max.accessibility.ScreenSpyService", parsed[0].second)
    }

    @Test
    fun test_classifyRisk_systemAospIsSafe() {
        val (risk1, desc1) = AccessibilityAuditor.classifyRisk("com.google.android.marvin.talkback", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk1)
        assertTrue(desc1.contains("Официальный") || desc1.contains("TalkBack"))

        val (risk2, _) = AccessibilityAuditor.classifyRisk("com.google.android.accessibility.suite", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk2)

        val (risk3, _) = AccessibilityAuditor.classifyRisk("com.android.switchaccess", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk3)

        val (risk4, _) = AccessibilityAuditor.classifyRisk("android", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk4)

        val (risk5, _) = AccessibilityAuditor.classifyRisk("com.android.systemui", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk5)

        val (risk6, _) = AccessibilityAuditor.classifyRisk("com.google.android.gms", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk6)

        // Subpackage of google accessibility when system app
        val (risk7, _) = AccessibilityAuditor.classifyRisk("com.google.android.accessibility.voiceaccess", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.SAFE, risk7)
    }

    @Test
    fun test_classifyRisk_vendorOemIsMedium() {
        val (riskSamsung, descSamsung) = AccessibilityAuditor.classifyRisk("com.samsung.accessibility", isSystemApp = false)
        assertEquals(AccessibilityRiskLevel.MEDIUM, riskSamsung)
        assertTrue(descSamsung.contains("производителя") || descSamsung.contains("Системный"))

        val (riskMiui, _) = AccessibilityAuditor.classifyRisk("com.miui.accessibility", isSystemApp = false)
        assertEquals(AccessibilityRiskLevel.MEDIUM, riskMiui)

        val (riskGenericSystem, descGenericSystem) = AccessibilityAuditor.classifyRisk("com.oem.custom.access", isSystemApp = true)
        assertEquals(AccessibilityRiskLevel.MEDIUM, riskGenericSystem)
        assertTrue(descGenericSystem.contains("производителя"))
    }

    @Test
    fun test_classifyRisk_thirdPartyAppsAreCriticalRisk() {
        // Commercial superapp / telemetry aggregator trying to capture screen content
        val (riskVk, descVk) = AccessibilityAuditor.classifyRisk("ru.vk.max", isSystemApp = false)
        assertEquals(AccessibilityRiskLevel.CRITICAL, riskVk)
        assertTrue(descVk.contains("КРИТИЧЕСКАЯ УГРОЗА"))
        assertTrue(descVk.contains("Telegram") || descVk.contains("экрану"))

        // Sketchy screen recorder or banking trojan
        val (riskSpy, descSpy) = AccessibilityAuditor.classifyRisk("com.trojan.bankbot", isSystemApp = false)
        assertEquals(AccessibilityRiskLevel.CRITICAL, riskSpy)
        assertTrue(descSpy.contains("КРИТИЧЕСКАЯ УГРОЗА"))

        // Known password manager
        val (riskBitwarden, descBitwarden) = AccessibilityAuditor.classifyRisk("com.x8bit.bitwarden", isSystemApp = false)
        assertEquals(AccessibilityRiskLevel.CRITICAL, riskBitwarden)
        assertTrue(descBitwarden.contains("Менеджер паролей"))
    }
}
