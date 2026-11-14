package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.AdIdPlatformType

class AdIdManagerTest {

    @Test
    fun test_constants_matchExpectedActions() {
        assertEquals("com.google.android.gms.settings.ADS_PRIVACY", AdIdManager.ACTION_GMS_ADS_PRIVACY)
        assertEquals("com.google.android.gms.settings.PRIVACY", AdIdManager.ACTION_GMS_PRIVACY)
        assertEquals("com.huawei.hms.settings.ADS_PRIVACY", AdIdManager.ACTION_HMS_ADS_PRIVACY)
        assertEquals("android.settings.PRIVACY_SETTINGS", AdIdManager.ACTION_ANDROID_PRIVACY)
    }

    @Test
    fun test_getCandidateIntents_generatesAllRequiredIntents() {
        val intents = AdIdManager.getCandidateIntents()
        assertTrue(intents.size >= 5)

        for (intent in intents) {
            assertNotNull(intent)
        }
    }

    @Test
    fun test_platformEnum_hasDisplayNames() {
        assertEquals("Google Play Services (GAID)", AdIdPlatformType.GOOGLE_PLAY_SERVICES.displayName)
        assertEquals("Huawei Mobile Services (OAID)", AdIdPlatformType.HUAWEI_HMS.displayName)
        assertEquals("Системный Android (AOSP)", AdIdPlatformType.AOSP_GENERIC.displayName)
    }
}
