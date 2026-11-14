package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.TriangulationRiskLevel

class GeoTriangulationAuditorTest {

    @Test
    fun test_classifyRisk_noLocation_isSafe() {
        // App has Wi-Fi and Phone state, but no location permissions
        val perms = setOf(
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE
        )

        val result = GeoTriangulationAuditor.classifyRisk(
            packageName = "com.example.nowifi",
            appName = "Safe App",
            requestedPermissions = perms,
            isSystemApp = false
        )

        assertEquals(TriangulationRiskLevel.SAFE, result.riskLevel)
        assertTrue(result.riskDescription.contains("Нет разрешений геолокации"))
    }

    @Test
    fun test_classifyRisk_multiVectorTriangulation_isCritical() {
        // Third-party app holding Location + Wi-Fi BSSID + Cell ID
        val fullVectors = setOf(
            GeoTriangulationAuditor.PERM_FINE_LOCATION,
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE
        )

        val result = GeoTriangulationAuditor.classifyRisk(
            packageName = "com.sketchy.tracker",
            appName = "Flashlight Spy",
            requestedPermissions = fullVectors,
            isSystemApp = false
        )

        assertEquals(TriangulationRiskLevel.CRITICAL, result.riskLevel)
        assertTrue(result.riskDescription.contains("КРИТИЧЕСКАЯ УГРОЗА"))
        assertTrue(result.riskDescription.contains("квартиры") || result.riskDescription.contains("GPS"))
        assertTrue(result.warningBadges.any { it.contains("BSSID") })
        assertTrue(result.warningBadges.any { it.contains("Cell ID") })
    }

    @Test
    fun test_classifyRisk_knownSurveillanceSuperapp_isCritical() {
        val superappPerms = setOf(
            GeoTriangulationAuditor.PERM_COARSE_LOCATION,
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE
        )

        val result = GeoTriangulationAuditor.classifyRisk(
            packageName = "ru.vk.max",
            appName = "MAX Messenger",
            requestedPermissions = superappPerms,
            isSystemApp = false
        )

        assertEquals(TriangulationRiskLevel.CRITICAL, result.riskLevel)
        assertTrue(result.riskDescription.contains("суперапп"))
    }

    @Test
    fun test_classifyRisk_singleVectorPlusLocation_isHigh() {
        // Location + Wi-Fi BSSID only
        val wifiOnly = setOf(
            GeoTriangulationAuditor.PERM_FINE_LOCATION,
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE
        )
        val resultWifi = GeoTriangulationAuditor.classifyRisk(
            packageName = "com.ad.network",
            appName = "AdSDK App",
            requestedPermissions = wifiOnly,
            isSystemApp = false
        )
        assertEquals(TriangulationRiskLevel.HIGH, resultWifi.riskLevel)
        assertTrue(resultWifi.riskDescription.contains("Wi-Fi"))

        // Location + Cell ID only
        val cellOnly = setOf(
            GeoTriangulationAuditor.PERM_COARSE_LOCATION,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE
        )
        val resultCell = GeoTriangulationAuditor.classifyRisk(
            packageName = "com.telemetry.collector",
            appName = "Collector",
            requestedPermissions = cellOnly,
            isSystemApp = false
        )
        assertEquals(TriangulationRiskLevel.HIGH, resultCell.riskLevel)
        assertTrue(resultCell.riskDescription.contains("вышки"))
    }

    @Test
    fun test_classifyRisk_legitimateNavigator_isMedium() {
        // Yandex Navigator with full capabilities
        val naviPerms = setOf(
            GeoTriangulationAuditor.PERM_FINE_LOCATION,
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE,
            GeoTriangulationAuditor.PERM_BLUETOOTH_SCAN
        )

        val result = GeoTriangulationAuditor.classifyRisk(
            packageName = "ru.yandex.yandexnavi",
            appName = "Яндекс Навигатор",
            requestedPermissions = naviPerms,
            isSystemApp = false
        )

        assertEquals(TriangulationRiskLevel.MEDIUM, result.riskLevel)
        assertTrue(result.riskDescription.contains("Официальный навигатор"))
    }

    @Test
    fun test_classifyRisk_coreSystemApp_isSafe() {
        val systemPerms = setOf(
            GeoTriangulationAuditor.PERM_FINE_LOCATION,
            GeoTriangulationAuditor.PERM_ACCESS_WIFI_STATE,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE
        )

        val result = GeoTriangulationAuditor.classifyRisk(
            packageName = "com.android.phone",
            appName = "Phone System",
            requestedPermissions = systemPerms,
            isSystemApp = true
        )

        assertEquals(TriangulationRiskLevel.SAFE, result.riskLevel)
        assertTrue(result.riskDescription.contains("Системный"))
    }

    @Test
    fun test_extractCapabilities_identifiesAllVectors() {
        val perms = setOf(
            GeoTriangulationAuditor.PERM_FINE_LOCATION,
            GeoTriangulationAuditor.PERM_NEARBY_WIFI,
            GeoTriangulationAuditor.PERM_READ_PHONE_STATE,
            GeoTriangulationAuditor.PERM_BLUETOOTH_SCAN
        )

        val caps = GeoTriangulationAuditor.extractCapabilities(perms)
        assertTrue(caps.hasGpsLocation)
        assertFalse(caps.hasCoarseLocation)
        assertTrue(caps.hasWifiBssidAccess)
        assertTrue(caps.hasCellIdAccess)
        assertTrue(caps.hasBluetoothBeacon)
    }
}
