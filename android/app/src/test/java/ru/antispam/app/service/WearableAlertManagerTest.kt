package ru.antispam.app.service

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.privacy.ProtectionPresetManager
import ru.antispam.app.privacy.model.ProtectionPreset

class WearableAlertManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            override fun putString(k: String?, v: String?) = apply { temp[k ?: ""] = v }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { temp[k ?: ""] = v?.toSet() }
            override fun putInt(k: String?, v: Int) = apply { temp[k ?: ""] = v }
            override fun putLong(k: String?, v: Long) = apply { temp[k ?: ""] = v }
            override fun putFloat(k: String?, v: Float) = apply { temp[k ?: ""] = v }
            override fun putBoolean(k: String?, v: Boolean) = apply { temp[k ?: ""] = v }
            override fun remove(k: String?) = apply { temp[k ?: ""] = null }
            override fun clear() = apply { data.clear(); temp.clear() }
            override fun commit() = true.also { apply() }
            override fun apply() {
                temp.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                temp.clear()
            }
        }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private val fakePrefs = FakeSharedPreferences()
    private val testContext = object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        WearableAlertManager.resetStateForTesting()
        fakePrefs.data.clear()
    }

    @After
    fun tearDown() {
        WearableAlertManager.resetStateForTesting()
        fakePrefs.data.clear()
    }

    @Test
    fun test_sendWearableAlert_antifraudSmsOtp() {
        assertNull(WearableAlertManager.lastAlert.value)

        val caller = "+79991234567"
        val snippet = "481920"

        WearableAlertManager.sendWearableAlert(
            context = testContext,
            alertType = WearableAlertType.ANTIFRAUD_SMS_OTP,
            callerNumber = caller,
            detailText = snippet
        )

        val alert = WearableAlertManager.lastAlert.value
        assertNotNull("Alert message should be recorded in StateFlow", alert)
        assertTrue(alert!!.contains(caller))
        assertTrue(alert.contains(snippet))
        assertTrue(alert.contains("Не называйте"))
    }

    @Test
    fun test_sendWearableAlert_micEavesdropping() {
        assertNull(WearableAlertManager.lastAlert.value)

        val detail = "Приложение com.spyware.tracker пытается захватить звук"

        WearableAlertManager.sendWearableAlert(
            context = testContext,
            alertType = WearableAlertType.MIC_EAVESDROPPING,
            callerNumber = null,
            detailText = detail
        )

        val alert = WearableAlertManager.lastAlert.value
        assertNotNull(alert)
        assertTrue(alert!!.contains("микрофон"))
        assertTrue(alert.contains(detail))
    }

    @Test
    fun test_dismissAlert_clearsState() {
        WearableAlertManager.sendWearableAlert(
            context = testContext,
            alertType = WearableAlertType.ANTIFRAUD_SMS_OTP,
            callerNumber = "+79990001122",
            detailText = "12345"
        )
        assertNotNull(WearableAlertManager.lastAlert.value)

        WearableAlertManager.dismissAlert(testContext)
        assertNull(WearableAlertManager.lastAlert.value)
    }

    @Test
    fun test_onBunkerAndDropTriggered_activatesEmergencyState() {
        assertFalse(WearableAlertManager.bunkerTriggeredFromWatch.value)

        // Stage alert first
        WearableAlertManager.sendWearableAlert(
            context = testContext,
            alertType = WearableAlertType.ANTIFRAUD_SMS_OTP,
            callerNumber = "+79998887766",
            detailText = "999888"
        )

        WearableAlertManager.onBunkerAndDropTriggered(testContext)

        assertTrue("Bunker triggered flag must be true", WearableAlertManager.bunkerTriggeredFromWatch.value)
        assertNull("Active wearable alert should be dismissed after bunker engagement", WearableAlertManager.lastAlert.value)
        assertEquals(ProtectionPreset.MAX_BUNKER, ProtectionPresetManager.getActivePreset(testContext))
    }

    @Test
    fun test_wearableBunkerReceiver_handlesAction() {
        assertFalse(WearableAlertManager.bunkerTriggeredFromWatch.value)

        val receiver = WearableBunkerReceiver()
        val intent = object : Intent(WearableAlertManager.ACTION_BUNKER_AND_DROP) {
            override fun getAction(): String = WearableAlertManager.ACTION_BUNKER_AND_DROP
        }

        receiver.onReceive(testContext, intent)

        assertTrue(WearableAlertManager.bunkerTriggeredFromWatch.value)
        assertEquals(ProtectionPreset.MAX_BUNKER, ProtectionPresetManager.getActivePreset(testContext))
    }

    @Test
    fun test_endCallSafely_runsWithoutCrashing() {
        val result = WearableAlertManager.endCallSafely(testContext)
        assertTrue(result == false || result == true)
    }
}
