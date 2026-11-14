package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.RussianAppCategory
import ru.antispam.app.privacy.model.RussianAppRegistry

class VpnMaskingHelperTest {

    @Test
    fun test_registry_containsEssentialRussianBanks() {
        val packages = RussianAppRegistry.ALL_PACKAGE_NAMES
        assertTrue(packages.contains("ru.sberbankmobile"))
        assertTrue(packages.contains("ru.tcsbank.mbank"))
        assertTrue(packages.contains("ru.vtb24.mobilebanking"))
        assertTrue(packages.contains("ru.alfabank.mobile.android"))
        assertTrue(packages.contains("ru.nspk.mirpay"))

        val sber = RussianAppRegistry.TARGET_SERVICES.first { it.packageName == "ru.sberbankmobile" }
        assertEquals(RussianAppCategory.BANKING, sber.category)
        assertTrue(sber.reason.contains("VPN"))
    }

    @Test
    fun test_registry_containsGovernmentAndSuperapps() {
        val packages = RussianAppRegistry.ALL_PACKAGE_NAMES
        assertTrue(packages.contains("com.gosuslugi"))
        assertTrue(packages.contains("ru.mos.app"))
        assertTrue(packages.contains("ru.nalog.fl"))
        assertTrue(packages.contains("ru.vk.max"))
        assertTrue(packages.contains("com.vkontakte.android"))
    }

    @Test
    fun test_generateSplitTunnelConfig_amneziaFormat() {
        val input = listOf("ru.sberbankmobile", "ru.tcsbank.mbank", "com.gosuslugi")
        val config = VpnMaskingHelper.generateSplitTunnelConfig(input, VpnClientFormat.AMNEZIA_VPN)

        assertTrue(config.contains("AmneziaVPN"))
        assertTrue(config.contains("ru.sberbankmobile"))
        assertTrue(config.contains("ru.tcsbank.mbank"))
        assertTrue(config.contains("com.gosuslugi"))
    }

    @Test
    fun test_generateSplitTunnelConfig_v2rayNgFormat() {
        val input = listOf("ru.sberbankmobile", "ru.vtb24.mobilebanking")
        val config = VpnMaskingHelper.generateSplitTunnelConfig(input, VpnClientFormat.V2RAYNG_NEKOBOX)

        assertTrue(config.contains("\"outboundTag\": \"direct\""))
        assertTrue(config.contains("\"app\": ["))
        assertTrue(config.contains("\"ru.sberbankmobile\""))
        assertTrue(config.contains("\"ru.vtb24.mobilebanking\""))
    }

    @Test
    fun test_generateSplitTunnelConfig_rawFormat() {
        val input = listOf("ru.sberbankmobile", "ru.tcsbank.mbank")
        val config = VpnMaskingHelper.generateSplitTunnelConfig(input, VpnClientFormat.RAW_PACKAGE_LIST)

        val lines = config.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        assertTrue(lines.contains("ru.sberbankmobile"))
        assertTrue(lines.contains("ru.tcsbank.mbank"))
    }

    @Test
    fun test_generateSplitTunnelConfig_emptyInputReturnsEmpty() {
        assertEquals("", VpnMaskingHelper.generateSplitTunnelConfig(emptyList(), VpnClientFormat.AMNEZIA_VPN))
        assertEquals("", VpnMaskingHelper.generateSplitTunnelConfig(emptyList(), VpnClientFormat.V2RAYNG_NEKOBOX))
        assertEquals("", VpnMaskingHelper.generateSplitTunnelConfig(emptyList(), VpnClientFormat.RAW_PACKAGE_LIST))
    }

    @Test
    fun test_getSetupGuide_returnsValidSteps() {
        val amneziaGuide = VpnMaskingHelper.getSetupGuide(VpnClientFormat.AMNEZIA_VPN)
        assertEquals(5, amneziaGuide.size)
        assertTrue(amneziaGuide[0].contains("AmneziaVPN"))

        val v2rayGuide = VpnMaskingHelper.getSetupGuide(VpnClientFormat.V2RAYNG_NEKOBOX)
        assertEquals(5, v2rayGuide.size)
        assertTrue(v2rayGuide[0].contains("v2rayNG"))

        val rawGuide = VpnMaskingHelper.getSetupGuide(VpnClientFormat.RAW_PACKAGE_LIST)
        assertEquals(4, rawGuide.size)
    }
}
