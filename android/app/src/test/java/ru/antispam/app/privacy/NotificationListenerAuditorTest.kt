package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.ListenerRiskLevel

class NotificationListenerAuditorTest {

    @Test
    fun test_parseEnabledListeners_emptyOrNull_returnsEmptyList() {
        assertTrue(NotificationListenerAuditor.parseEnabledListeners(null).isEmpty())
        assertTrue(NotificationListenerAuditor.parseEnabledListeners("").isEmpty())
        assertTrue(NotificationListenerAuditor.parseEnabledListeners("   ").isEmpty())
    }

    @Test
    fun test_parseEnabledListeners_colonSeparatedComponents() {
        val raw = "com.google.android.wearable.app/com.google.android.clockwork.stream.NotificationCollectorService:com.sec.android.app.sbrowser/com.sec.android.app.sbrowser.notification.BrowserNotificationListener"
        val parsed = NotificationListenerAuditor.parseEnabledListeners(raw)

        assertEquals(2, parsed.size)
        assertEquals("com.google.android.wearable.app", parsed[0].first)
        assertEquals("com.google.android.clockwork.stream.NotificationCollectorService", parsed[0].second)

        assertEquals("com.sec.android.app.sbrowser", parsed[1].first)
        assertEquals("com.sec.android.app.sbrowser.notification.BrowserNotificationListener", parsed[1].second)
    }

    @Test
    fun test_parseEnabledListeners_singleComponent() {
        val raw = "ru.vk.max/ru.vk.max.push.NotificationSpyService"
        val parsed = NotificationListenerAuditor.parseEnabledListeners(raw)

        assertEquals(1, parsed.size)
        assertEquals("ru.vk.max", parsed[0].first)
        assertEquals("ru.vk.max.push.NotificationSpyService", parsed[0].second)
    }

    @Test
    fun test_classifyRisk_systemAospIsSafe() {
        val (risk1, desc1) = NotificationListenerAuditor.classifyRisk("com.android.systemui", isSystemApp = true)
        assertEquals(ListenerRiskLevel.SAFE, risk1)
        assertTrue(desc1.contains("безопасен") || desc1.contains("Системный"))

        val (risk2, desc2) = NotificationListenerAuditor.classifyRisk("android", isSystemApp = true)
        assertEquals(ListenerRiskLevel.SAFE, risk2)

        val (risk3, desc3) = NotificationListenerAuditor.classifyRisk("com.google.android.gms", isSystemApp = true)
        assertEquals(ListenerRiskLevel.SAFE, risk3)
    }

    @Test
    fun test_classifyRisk_wearableCompanionsAreMedium() {
        val (riskSamsung, descSamsung) = NotificationListenerAuditor.classifyRisk(
            "com.samsung.android.app.watchmanager",
            isSystemApp = false
        )
        assertEquals(ListenerRiskLevel.MEDIUM, riskSamsung)
        assertTrue(descSamsung.contains("носимых") || descSamsung.contains("часы"))

        val (riskHuawei, _) = NotificationListenerAuditor.classifyRisk("com.huawei.health", isSystemApp = false)
        assertEquals(ListenerRiskLevel.MEDIUM, riskHuawei)

        val (riskMi, _) = NotificationListenerAuditor.classifyRisk("com.mi.health", isSystemApp = false)
        assertEquals(ListenerRiskLevel.MEDIUM, riskMi)
    }

    @Test
    fun test_classifyRisk_thirdPartyAppsAreHighRisk() {
        // Commercial superapp / telemetry aggregator
        val (riskVk, descVk) = NotificationListenerAuditor.classifyRisk("ru.vk.max", isSystemApp = false)
        assertEquals(ListenerRiskLevel.HIGH, riskVk)
        assertTrue(descVk.contains("2FA") || descVk.contains("СМС"))

        // Sketchy cleaner / flashlight
        val (riskCleaner, descCleaner) = NotificationListenerAuditor.classifyRisk("com.cleaner.speedbooster", isSystemApp = false)
        assertEquals(ListenerRiskLevel.HIGH, riskCleaner)
        assertTrue(descCleaner.contains("перехватывать") || descCleaner.contains("СМС"))
    }

    @Test
    fun test_classifyRisk_vendorSystemAppIsMedium() {
        val (riskVendor, descVendor) = NotificationListenerAuditor.classifyRisk(
            "com.miui.securitycenter",
            isSystemApp = true
        )
        assertEquals(ListenerRiskLevel.MEDIUM, riskVendor)
    }
}
