package ru.antispam.app.privacy

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PrivacyAuditInput

class TrustedAppManagerTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            temp[key] = values?.toSet()
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            storage.clear()
            temp.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            for ((k, v) in temp) {
                if (v == null) storage.remove(k) else storage[k] = v
            }
            temp.clear()
        }

        override fun putString(key: String, value: String?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
    }

    private class FakeSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            return (storage[key] as? Set<String>)?.toMutableSet() ?: defValues
        }

        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = storage[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        dummyContext = object : android.content.ContextWrapper(null) {}
        TrustedAppManager.sharedPreferencesProvider = { fakePrefs }
    }

    @After
    fun tearDown() {
        TrustedAppManager.sharedPreferencesProvider = null
    }

    @Test
    fun test_notificationListener_trustToggleAndQuery() {
        val pkg = "com.garmin.android.apps.connectmobile"

        assertFalse(TrustedAppManager.isNotificationListenerTrusted(dummyContext, pkg))

        TrustedAppManager.setNotificationListenerTrusted(dummyContext, pkg, isTrusted = true)
        assertTrue(TrustedAppManager.isNotificationListenerTrusted(dummyContext, pkg))
        assertTrue(TrustedAppManager.isNotificationListenerTrusted(dummyContext, "COM.GARMIN.ANDROID.APPS.CONNECTMOBILE"))

        // Revoke trust
        TrustedAppManager.setNotificationListenerTrusted(dummyContext, pkg, isTrusted = false)
        assertFalse(TrustedAppManager.isNotificationListenerTrusted(dummyContext, pkg))
    }

    @Test
    fun test_accessibilityService_trustToggleAndQuery() {
        val bitwarden = "com.x8bit.bitwarden"

        assertFalse(TrustedAppManager.isAccessibilityServiceTrusted(dummyContext, bitwarden))

        TrustedAppManager.setAccessibilityServiceTrusted(dummyContext, bitwarden, isTrusted = true)
        assertTrue(TrustedAppManager.isAccessibilityServiceTrusted(dummyContext, bitwarden))

        // Revoke trust
        TrustedAppManager.setAccessibilityServiceTrusted(dummyContext, bitwarden, isTrusted = false)
        assertFalse(TrustedAppManager.isAccessibilityServiceTrusted(dummyContext, bitwarden))
    }

    @Test
    fun test_recognizedPopularApps() {
        assertTrue(TrustedAppManager.isRecognizedPasswordManager("com.x8bit.bitwarden"))
        assertTrue(TrustedAppManager.isRecognizedPasswordManager("com.kunzisoft.keepass.free"))
        assertTrue(TrustedAppManager.isRecognizedPasswordManager("me.proton.pass"))
        assertFalse(TrustedAppManager.isRecognizedPasswordManager("com.malicious.trojan"))

        assertTrue(TrustedAppManager.isRecognizedWearableCompanion("com.garmin.android.apps.connectmobile"))
        assertTrue(TrustedAppManager.isRecognizedWearableCompanion("com.huawei.health"))
        assertTrue(TrustedAppManager.isRecognizedWearableCompanion("com.xiaomi.wearable"))
        assertFalse(TrustedAppManager.isRecognizedWearableCompanion("ru.vk.max"))
    }

    @Test
    fun test_perpetual80PercentDilemma_resolvedByWhitelisting() {
        // Base conditions: All core modules active
        val isRegistryFresh = true
        val isMicGuardOn = true
        val isClipboardGuardOn = true

        // 1. User has Bitwarden (Accessibility) and Garmin Connect (NotificationListener).
        // Before whitelist:
        val appsListeners = listOf(
            NotificationListenerApp(
                packageName = "com.garmin.android.apps.connectmobile",
                appName = "Garmin Connect",
                serviceName = "GarminListenerService",
                isSystemApp = false,
                riskLevel = ListenerRiskLevel.HIGH,
                riskDescription = "Стороннее приложение",
                isTrusted = false
            )
        )
        val appsAccessibility = listOf(
            AccessibilityServiceApp(
                packageName = "com.x8bit.bitwarden",
                appName = "Bitwarden",
                serviceName = "BitwardenAccessibilityService",
                isSystemApp = false,
                riskLevel = AccessibilityRiskLevel.CRITICAL,
                riskDescription = "Менеджер паролей",
                isTrusted = false
            )
        )

        val unwhitelistedInput = PrivacyAuditInput(
            isRegistryUpToDate = isRegistryFresh,
            isMicGuardEnabled = isMicGuardOn,
            hasNotificationSpies = appsListeners.any { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted },
            isClipboardGuardEnabled = isClipboardGuardOn,
            hasMitmRootCa = false,
            hasCriticalAccessibility = appsAccessibility.any { it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted }
        )

        val penaltyReport = SecurityScoreEngine.calculateScore(unwhitelistedInput)
        // Deductions: -20 (Notification Spies) and -10 (Accessibility) -> 100 - 30 = 70 points
        assertEquals(70, penaltyReport.score)
        assertTrue(penaltyReport.issues.any { it.id == "notification_spy_detected" })
        assertTrue(penaltyReport.issues.any { it.id == "critical_accessibility" })

        // 2. User marks Bitwarden and Garmin as Trusted:
        val whitelistedListeners = appsListeners.map { it.copy(isTrusted = true) }
        val whitelistedAccessibility = appsAccessibility.map { it.copy(isTrusted = true) }

        val whitelistedInput = PrivacyAuditInput(
            isRegistryUpToDate = isRegistryFresh,
            isMicGuardEnabled = isMicGuardOn,
            hasNotificationSpies = whitelistedListeners.any { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted },
            isClipboardGuardEnabled = isClipboardGuardOn,
            hasMitmRootCa = false,
            hasCriticalAccessibility = whitelistedAccessibility.any { it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted }
        )

        val successReport = SecurityScoreEngine.calculateScore(whitelistedInput)
        // With trusted whitelist: score reaches a perfect 100% (Green Ring)!
        assertEquals(100, successReport.score)
        assertTrue(successReport.issues.isEmpty())

        // 3. New untrusted surveillance app appears (e.g. ru.vk.max):
        val withRogueApp = whitelistedListeners + NotificationListenerApp(
            packageName = "ru.vk.max",
            appName = "MAX Superapp",
            serviceName = "TelemetryService",
            isSystemApp = false,
            riskLevel = ListenerRiskLevel.HIGH,
            riskDescription = "Шпион",
            isTrusted = false
        )

        val rogueInput = PrivacyAuditInput(
            isRegistryUpToDate = isRegistryFresh,
            isMicGuardEnabled = isMicGuardOn,
            hasNotificationSpies = withRogueApp.any { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted },
            isClipboardGuardEnabled = isClipboardGuardOn,
            hasMitmRootCa = false,
            hasCriticalAccessibility = false
        )

        val rogueReport = SecurityScoreEngine.calculateScore(rogueInput)
        // Score immediately drops from 100 to 80, proving active protection
        assertEquals(80, rogueReport.score)
        assertTrue(rogueReport.issues.any { it.id == "notification_spy_detected" })
    }
}
