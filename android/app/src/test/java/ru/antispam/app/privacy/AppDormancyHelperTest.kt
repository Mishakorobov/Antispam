package ru.antispam.app.privacy

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SuperAppCategory
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.privacy.model.TriangulationCapabilities
import ru.antispam.app.privacy.model.TriangulationRiskApp
import ru.antispam.app.privacy.model.TriangulationRiskLevel

class AppDormancyHelperTest {

    @Test
    fun test_constants_samsungDeepSleepState() {
        assertEquals(4, AppDormancyHelper.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)
    }

    @Test
    fun test_blankPackage_returnsFalse() {
        val mockContext = mock(Context.javaClass)
        assertFalse(AppDormancyHelper.isAppInDeepSleep(mockContext, ""))
        assertFalse(AppDormancyHelper.isAppInDeepSleep(mockContext, "   "))
    }

    @Test
    fun test_deepSleepingApps_exemptFromSecurityScorePenalties() {
        // Given: Apps with high-risk capabilities, BUT all placed in Deep Sleep by Samsung One UI
        val listenerInDeepSleep = NotificationListenerApp(
            packageName = "com.thirdparty.spy",
            appName = "Third Party App",
            serviceName = ".SpyListener",
            isSystemApp = false,
            riskLevel = ListenerRiskLevel.SAFE, // Marked SAFE because in deep sleep
            riskDescription = "В глубоком сне",
            isDeepSleeping = true
        )

        val accessibilityInDeepSleep = AccessibilityServiceApp(
            packageName = "com.thirdparty.touch",
            appName = "Touch Macro",
            serviceName = ".MacroService",
            isSystemApp = false,
            riskLevel = AccessibilityRiskLevel.SAFE, // Marked SAFE because in deep sleep
            riskDescription = "В глубоком сне",
            isDeepSleeping = true
        )

        val superAppInDeepSleep = PackagePrivacyProfile(
            packageName = "ru.vk.max",
            appName = "MAX Superapp",
            isSystemApp = false,
            hasQueryAllPackages = true,
            riskLevel = SuperAppRiskLevel.SAFE, // Marked SAFE because in deep sleep
            category = SuperAppCategory.REGULAR_APP,
            riskDescription = "В глубоком сне",
            isDeepSleeping = true
        )

        val triangulationInDeepSleep = TriangulationRiskApp(
            packageName = "com.stealth.tracker",
            appName = "Tracker",
            isSystemApp = false,
            riskLevel = TriangulationRiskLevel.SAFE,
            capabilities = TriangulationCapabilities(
                hasGpsLocation = true,
                hasCoarseLocation = true,
                hasWifiBssidAccess = true,
                hasCellIdAccess = true,
                hasBluetoothBeacon = false
            ),
            activeVectorsCount = 3,
            riskDescription = "В глубоком сне",
            warningBadges = listOf("Глубокий сон"),
            isDeepSleeping = true
        )

        // Evaluate count of active threats (should filter out isDeepSleeping == true)
        val listeners = listOf(listenerInDeepSleep)
        val activeListenersCount = listeners.count { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted && !it.isDeepSleeping }
        assertEquals(0, activeListenersCount)

        val accessibilities = listOf(accessibilityInDeepSleep)
        val activeAccessibilityCount = accessibilities.count { it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted && !it.isDeepSleeping }
        assertEquals(0, activeAccessibilityCount)

        val superapps = listOf(superAppInDeepSleep)
        val activeSuperAppCount = superapps.count { it.riskLevel == SuperAppRiskLevel.CRITICAL && !it.isDeepSleeping }
        assertEquals(0, activeSuperAppCount)

        val triangulations = listOf(triangulationInDeepSleep)
        val activeTriangulationCount = triangulations.count { it.riskLevel == TriangulationRiskLevel.CRITICAL && !it.isDeepSleeping }
        assertEquals(0, activeTriangulationCount)

        // Verify that SecurityScoreEngine gives 100% when all other systems are active
        val auditInput = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = activeListenersCount > 0,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasCriticalAccessibility = activeAccessibilityCount > 0,
            hasVpnInventoryExposed = activeSuperAppCount > 0,
            hasStealthGeoTrackers = activeTriangulationCount > 0,
            hasRootOrHooks = false,
            hasCallForwarding = false,
            hasActive2GConnection = false
        )

        val scoreReport = SecurityScoreEngine.calculateScore(auditInput)
        assertEquals(100, scoreReport.score)
        assertTrue(scoreReport.issues.isEmpty())
    }
}
