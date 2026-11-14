package ru.antispam.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemOnboardingHelperTest {

    @Test
    fun test_detectOem_xiaomiFamily() {
        assertEquals(OemVendor.XIAOMI_HYPEROS, OemOnboardingHelper.detectOem("Xiaomi", "Xiaomi"))
        assertEquals(OemVendor.XIAOMI_HYPEROS, OemOnboardingHelper.detectOem("Xiaomi", "Redmi"))
        assertEquals(OemVendor.XIAOMI_HYPEROS, OemOnboardingHelper.detectOem("POCO", "POCO"))
        assertEquals(OemVendor.XIAOMI_HYPEROS, OemOnboardingHelper.detectOem("redmi", "phone"))
    }

    @Test
    fun test_detectOem_huaweiFamily() {
        assertEquals(OemVendor.HUAWEI_EMUI, OemOnboardingHelper.detectOem("HUAWEI", "HUAWEI"))
        assertEquals(OemVendor.HUAWEI_EMUI, OemOnboardingHelper.detectOem("HONOR", "HONOR"))
        assertEquals(OemVendor.HUAWEI_EMUI, OemOnboardingHelper.detectOem("Huawei Technologies Co.", "P30"))
    }

    @Test
    fun test_detectOem_samsung() {
        assertEquals(OemVendor.SAMSUNG_ONEUI, OemOnboardingHelper.detectOem("samsung", "samsung"))
        assertEquals(OemVendor.SAMSUNG_ONEUI, OemOnboardingHelper.detectOem("Samsung Electronics", "Galaxy"))
    }

    @Test
    fun test_detectOem_vivoFamily() {
        assertEquals(OemVendor.VIVO_ORIGINOS, OemOnboardingHelper.detectOem("vivo", "vivo"))
        assertEquals(OemVendor.VIVO_ORIGINOS, OemOnboardingHelper.detectOem("iQOO", "iQOO"))
    }

    @Test
    fun test_detectOem_oppoRealmeOnePlus() {
        assertEquals(OemVendor.OPPO_COLOROS, OemOnboardingHelper.detectOem("OPPO", "OPPO"))
        assertEquals(OemVendor.OPPO_COLOROS, OemOnboardingHelper.detectOem("realme", "realme"))
        assertEquals(OemVendor.OPPO_COLOROS, OemOnboardingHelper.detectOem("OnePlus", "OnePlus"))
    }

    @Test
    fun test_detectOem_genericAosp() {
        assertEquals(OemVendor.GENERIC_AOSP, OemOnboardingHelper.detectOem("Google", "Pixel"))
        assertEquals(OemVendor.GENERIC_AOSP, OemOnboardingHelper.detectOem("Motorola", "moto"))
        assertEquals(OemVendor.GENERIC_AOSP, OemOnboardingHelper.detectOem("Nokia", "HMD"))
        assertEquals(OemVendor.GENERIC_AOSP, OemOnboardingHelper.detectOem("UnknownBrand", "GenericDevice"))
    }

    @Test
    fun test_vendorInstructionsAndMetadata_areValid() {
        for (vendor in OemVendor.entries) {
            assertTrue("Brand name should not be blank for ${vendor.name}", vendor.brandName.isNotBlank())
            assertTrue("Skin name should not be blank for ${vendor.name}", vendor.uiSkinName.isNotBlank())
            assertTrue("Instructions should not be empty for ${vendor.name}", vendor.instructions.isNotEmpty())
            for (step in vendor.instructions) {
                assertTrue("Instruction step should not be blank", step.isNotBlank())
            }
        }
    }

    @Test
    fun test_oemAggressiveKillerFlags() {
        assertTrue(OemVendor.XIAOMI_HYPEROS.isAggressiveKiller)
        assertTrue(OemVendor.HUAWEI_EMUI.isAggressiveKiller)
        assertTrue(OemVendor.SAMSUNG_ONEUI.isAggressiveKiller)
        assertTrue(OemVendor.VIVO_ORIGINOS.isAggressiveKiller)
        assertTrue(OemVendor.OPPO_COLOROS.isAggressiveKiller)
        assertFalse(OemVendor.GENERIC_AOSP.isAggressiveKiller)
    }
}
