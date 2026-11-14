package ru.antispam.app.ui.onboarding

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingManagerTest {

    private lateinit var testContext: Context
    private val fakePrefs = FakeSharedPreferences()
    private var startedIntent: Intent? = null

    @Before
    fun setUp() {
        fakePrefs.clear()
        startedIntent = null
        testContext = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return fakePrefs
            }

            override fun startActivity(intent: Intent?) {
                startedIntent = intent
            }
        }
    }

    @Test
    fun test_initialState_manifestNotAccepted() {
        assertFalse("Manifest should initially not be accepted", OnboardingManager.isManifestAccepted(testContext))
    }

    @Test
    fun test_setManifestAccepted_persistsTrue() {
        OnboardingManager.setManifestAccepted(testContext, true)
        assertTrue("Manifest should be recorded as accepted", OnboardingManager.isManifestAccepted(testContext))
        assertTrue(fakePrefs.getBoolean("manifest_accepted", false))
        assertTrue(fakePrefs.getLong("manifest_accepted_timestamp", 0L) > 0L)
    }

    @Test
    fun test_setManifestAccepted_false_clearsAcceptance() {
        OnboardingManager.setManifestAccepted(testContext, true)
        assertTrue(OnboardingManager.isManifestAccepted(testContext))

        OnboardingManager.setManifestAccepted(testContext, false)
        assertFalse(OnboardingManager.isManifestAccepted(testContext))
    }

    @Test
    fun test_reset_clearsAllOnboardingPrefs() {
        OnboardingManager.setManifestAccepted(testContext, true)
        assertTrue(OnboardingManager.isManifestAccepted(testContext))

        OnboardingManager.reset(testContext)
        assertFalse(OnboardingManager.isManifestAccepted(testContext))
    }

    @Test
    fun test_getManifestShareText_containsCorePillarsAndProductTitle() {
        val text = OnboardingManager.getManifestShareText()

        assertTrue("Should contain product name", text.contains("AntiSpam Shield"))
        assertTrue("Should contain Manifesto title", text.contains("Манифест Нулевой Телеметрии"))
        assertTrue("Should contain Zero Cloud Upload guarantee", text.contains("Zero Cloud Upload") || text.contains("НИКОГДА не выгружаем"))
        assertTrue("Should mention 1 ms on-device verification", text.contains("1 мс") || text.contains("On-Device"))
        assertTrue("Should mention Ed25519", text.contains("Ed25519"))
        assertTrue("Should mention MicGuard or spy shield", text.contains("Анти-шпионский") || text.contains("MicGuard"))
        assertTrue("Should mention GMS-Free or P2P", text.contains("GMS-Free") || text.contains("P2P"))
    }

    @Test
    fun test_shareManifest_startsActivityWithIntent() {
        val intent = OnboardingManager.createShareIntent()
        assertNotNull("Should create share intent", intent)

        OnboardingManager.shareManifest(testContext)
        assertNotNull("Should start an activity intent", startedIntent)
    }

    // In-memory fake SharedPreferences for deterministic JVM execution
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        fun clear() = data.clear()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val data: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearPending = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearPending = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearPending) data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
