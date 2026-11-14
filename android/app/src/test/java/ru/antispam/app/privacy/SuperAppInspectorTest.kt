package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.SuperAppCategory
import ru.antispam.app.privacy.model.SuperAppRiskLevel

class SuperAppInspectorTest {

    @Test
    fun test_classifyRisk_noQueryAll_isSafe() {
        val perms = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        val (risk, category, desc) = SuperAppInspector.classifyRisk("com.example.normalapp", perms, isSystemApp = false)

        assertEquals(SuperAppRiskLevel.SAFE, risk)
        assertEquals(SuperAppCategory.REGULAR_APP, category)
        assertTrue(desc.contains("Нет доступа"))
    }

    @Test
    fun test_classifyRisk_knownSurveillanceSuperapps_areCritical() {
        val perms = setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES, "android.permission.INTERNET")

        // VK / MAX messenger
        val (riskMax, catMax, descMax) = SuperAppInspector.classifyRisk("ru.vk.max", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.CRITICAL, riskMax)
        assertEquals(SuperAppCategory.SURVEILLANCE_SUPERAPP, catMax)
        assertTrue(descMax.contains("КРИТИЧЕСКАЯ УГРОЗА"))
        assertTrue(descMax.contains("VPN") || descMax.contains("профилирования"))

        // VK App
        val (riskVk, catVk, _) = SuperAppInspector.classifyRisk("com.vkontakte.android", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.CRITICAL, riskVk)
        assertEquals(SuperAppCategory.SURVEILLANCE_SUPERAPP, catVk)

        // Banking tracker
        val (riskSber, catSber, _) = SuperAppInspector.classifyRisk("ru.sberbankmobile", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.CRITICAL, riskSber)
        assertEquals(SuperAppCategory.SURVEILLANCE_SUPERAPP, catSber)

        // Yandex Search / Telemetry
        val (riskYandex, catYandex, _) = SuperAppInspector.classifyRisk("ru.yandex.searchplugin", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.CRITICAL, riskYandex)
        assertEquals(SuperAppCategory.SURVEILLANCE_SUPERAPP, catYandex)
    }

    @Test
    fun test_classifyRisk_systemCoreApps_areSafe() {
        val perms = setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES)

        val (riskAndroid, catAndroid, _) = SuperAppInspector.classifyRisk("android", perms, isSystemApp = true)
        assertEquals(SuperAppRiskLevel.SAFE, riskAndroid)
        assertEquals(SuperAppCategory.SYSTEM_COMPONENT, catAndroid)

        val (riskSystemUi, catSystemUi, _) = SuperAppInspector.classifyRisk("com.android.systemui", perms, isSystemApp = true)
        assertEquals(SuperAppRiskLevel.SAFE, riskSystemUi)
        assertEquals(SuperAppCategory.SYSTEM_COMPONENT, catSystemUi)

        val (riskGms, catGms, _) = SuperAppInspector.classifyRisk("com.google.android.gms", perms, isSystemApp = true)
        assertEquals(SuperAppRiskLevel.SAFE, riskGms)
        assertEquals(SuperAppCategory.SYSTEM_COMPONENT, catGms)
    }

    @Test
    fun test_classifyRisk_launchersAndStores_areMedium() {
        val perms = setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES)

        // Nova Launcher
        val (riskNova, catNova, descNova) = SuperAppInspector.classifyRisk("com.teslacoilsw.launcher", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.MEDIUM, riskNova)
        assertEquals(SuperAppCategory.APP_STORE_OR_LAUNCHER, catNova)
        assertTrue(descNova.contains("Лаунчер") || descNova.contains("рабочего стола"))

        // Google Play Store
        val (riskPlay, catPlay, _) = SuperAppInspector.classifyRisk("com.android.vending", perms, isSystemApp = true)
        assertEquals(SuperAppRiskLevel.MEDIUM, riskPlay)
        assertEquals(SuperAppCategory.APP_STORE_OR_LAUNCHER, catPlay)

        // Custom third-party launcher
        val (riskCustom, catCustom, _) = SuperAppInspector.classifyRisk("org.custom.home.launcher", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.MEDIUM, riskCustom)
        assertEquals(SuperAppCategory.APP_STORE_OR_LAUNCHER, catCustom)
    }

    @Test
    fun test_classifyRisk_genericThirdPartyApps_areHigh() {
        val perms = setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES, "android.permission.INTERNET")

        val (risk, category, desc) = SuperAppInspector.classifyRisk("com.sketchy.cleaner", perms, isSystemApp = false)
        assertEquals(SuperAppRiskLevel.HIGH, risk)
        assertEquals(SuperAppCategory.COMMERCIAL_TRACKER, category)
        assertTrue(desc.contains("ВЫСОКИЙ РИСК"))
    }

    @Test
    fun test_buildCapabilities_identifiesAllThreatBadges() {
        val fullPerms = setOf(
            SuperAppInspector.PERM_QUERY_ALL_PACKAGES,
            "android.permission.INTERNET",
            "android.permission.READ_PHONE_STATE",
            "android.permission.ACCESS_FINE_LOCATION"
        )

        val caps = SuperAppInspector.buildCapabilities(fullPerms, hasInstalledVpn = true)
        assertEquals(5, caps.size)
        assertTrue(caps.any { it.contains("QUERY_ALL_PACKAGES") })
        assertTrue(caps.any { it.contains("VPN") })
        assertTrue(caps.any { it.contains("INTERNET") })
        assertTrue(caps.any { it.contains("PHONE_STATE") })
        assertTrue(caps.any { it.contains("LOCATION") })

        val minimalCaps = SuperAppInspector.buildCapabilities(
            setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES),
            hasInstalledVpn = false
        )
        assertEquals(1, minimalCaps.size)
        assertTrue(minimalCaps[0].contains("QUERY_ALL_PACKAGES"))
    }

    @Test
    fun test_knownVpnRegistry_containsEssentialCircumventionTools() {
        val vpns = SuperAppInspector.KNOWN_VPN_PACKAGES
        assertTrue(vpns.containsKey("org.amnezia.vpn"))
        assertTrue(vpns.containsKey("com.v2ray.ang"))
        assertTrue(vpns.containsKey("moe.nb4a"))
        assertTrue(vpns.containsKey("org.outline.android.client"))
        assertTrue(vpns.containsKey("com.adguard.android"))
        assertTrue(vpns.containsKey("ch.protonvpn.android"))
        assertTrue(vpns.containsKey("io.github.dovecoteescapee.byedpi"))
        assertTrue(vpns.containsKey("com.wireguard.android"))
    }
}
