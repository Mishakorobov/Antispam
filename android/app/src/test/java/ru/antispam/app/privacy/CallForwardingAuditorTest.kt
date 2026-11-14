package ru.antispam.app.privacy

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.privacy.model.CallForwardingType
import ru.antispam.app.privacy.model.ForwardingRiskLevel
import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType

class CallForwardingAuditorTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

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
    }

    private class FakeSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return storage[key] as? Boolean ?: defValue
        }

        override fun getLong(key: String, defValue: Long): Long {
            return storage[key] as? Long ?: defValue
        }

        override fun getString(key: String, defValue: String?): String? {
            return storage[key] as? String ?: defValue
        }

        override fun getInt(key: String, defValue: Int): Int {
            return storage[key] as? Int ?: defValue
        }

        override fun getFloat(key: String, defValue: Float): Float {
            return storage[key] as? Float ?: defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            return (storage[key] as? Set<String>)?.toMutableSet() ?: defValues
        }

        override fun getAll(): MutableMap<String, *> = storage
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun contains(key: String): Boolean = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var dummyContext: Context

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        dummyContext = object : ContextWrapper(null) {}
        CallForwardingAuditor.sharedPreferencesProvider = { fakePrefs }
    }

    @After
    fun tearDown() {
        CallForwardingAuditor.sharedPreferencesProvider = null
    }

    @Test
    fun test_ussdCodes_conformTo3gppGsmStandards() {
        // Universal clear
        assertEquals("##002#", CallForwardingAuditor.USSD_RESET_ALL)
        assertEquals("##002#", CallForwardingType.ALL_FORWARDING.ussdCancelCode)

        // Unconditional
        assertEquals("*#21#", CallForwardingAuditor.USSD_QUERY_UNCONDITIONAL)
        assertEquals("*#21#", CallForwardingType.UNCONDITIONAL.ussdQueryCode)
        assertEquals("##21#", CallForwardingType.UNCONDITIONAL.ussdCancelCode)

        // All conditional
        assertEquals("*#004#", CallForwardingAuditor.USSD_QUERY_ALL_CONDITIONAL)
        assertEquals("*#004#", CallForwardingType.ALL_CONDITIONAL.ussdQueryCode)

        // Individual conditional
        assertEquals("*#61#", CallForwardingAuditor.USSD_QUERY_NO_REPLY)
        assertEquals("*#62#", CallForwardingAuditor.USSD_QUERY_UNREACHABLE)
        assertEquals("*#67#", CallForwardingAuditor.USSD_QUERY_BUSY)
    }

    @Test
    fun test_createResetForwardingIntent_generatesValidDialerIntent() {
        val intent = CallForwardingAuditor.createResetForwardingIntent()
        assertNotNull(intent)
    }

    @Test
    fun test_createQueryForwardingIntent_generatesValidDialerIntent() {
        val intent = CallForwardingAuditor.createQueryForwardingIntent(CallForwardingType.UNCONDITIONAL)
        assertNotNull(intent)
    }

    @Test
    fun test_auditCallForwarding_defaultCleanState() {
        val report = CallForwardingAuditor.auditCallForwarding(dummyContext)

        assertFalse(report.hasActiveForwarding)
        assertFalse(report.isCarrierIndicatorActive)
        assertEquals(ForwardingRiskLevel.SAFE, report.riskLevel)
        assertTrue(report.summary.contains("Скрытая безусловная переадресация не зафиксирована"))
    }

    @Test
    fun test_auditCallForwarding_carrierIndicatorActive_triggersCriticalThreat() {
        // Simulate carrier indicator reported active forwarding
        CallForwardingAuditor.updateCarrierIndicator(dummyContext, true)

        val report = CallForwardingAuditor.auditCallForwarding(dummyContext)

        assertTrue(report.hasActiveForwarding)
        assertTrue(report.isCarrierIndicatorActive)
        assertEquals(ForwardingRiskLevel.CRITICAL, report.riskLevel)
        assertEquals(1, report.activeRules.size)
        assertEquals(CallForwardingType.UNCONDITIONAL, report.activeRules[0].type)
        assertTrue(report.summary.contains("Оператор связи сообщает об активной безусловной переадресации"))
    }

    @Test
    fun test_markAllForwardingCleared_resetsIndicatorAndSetsSafeState() {
        // Activate threat first
        CallForwardingAuditor.updateCarrierIndicator(dummyContext, true)
        var report = CallForwardingAuditor.auditCallForwarding(dummyContext)
        assertTrue(report.hasActiveForwarding)

        // Clear via ##002#
        CallForwardingAuditor.markAllForwardingCleared(dummyContext)

        report = CallForwardingAuditor.auditCallForwarding(dummyContext)
        assertFalse(report.hasActiveForwarding)
        assertFalse(report.isCarrierIndicatorActive)
        assertEquals(ForwardingRiskLevel.SAFE, report.riskLevel)
        assertTrue(report.lastResetTimestamp > 0L)
        assertTrue(report.summary.contains("Все переадресации успешно аннулированы кодом ##002#"))
    }

    @Test
    fun test_securityScoreEngine_activeCallForwardingDeductsPenalty() {
        val cleanInput = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasCallForwarding = false
        )
        val cleanReport = SecurityScoreEngine.calculateScore(cleanInput)
        assertEquals(100, cleanReport.score)

        val threatInput = cleanInput.copy(hasCallForwarding = true)
        val threatReport = SecurityScoreEngine.calculateScore(threatInput)

        assertEquals(85, threatReport.score) // 100 - 15 = 85
        val cfIssue = threatReport.issues.find { it.type == SecurityIssueType.CALL_FORWARDING_ACTIVE }
        assertNotNull(cfIssue)
        assertEquals(SecurityIssueSeverity.CRITICAL, cfIssue?.severity)
        assertEquals("Сбросить (##002#)", cfIssue?.actionLabel)
    }
}
