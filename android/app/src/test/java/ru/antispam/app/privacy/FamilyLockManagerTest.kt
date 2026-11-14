package ru.antispam.app.privacy

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FamilyLockManagerTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
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
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
    }

    private class FakeSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = storage[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = storage[key] as? Float ?: defValue
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
        FamilyLockManager.sharedPreferencesProvider = { fakePrefs }
    }

    @After
    fun tearDown() {
        FamilyLockManager.sharedPreferencesProvider = null
    }

    @Test
    fun test_familyLock_defaultsToFalse() {
        assertFalse(FamilyLockManager.isFamilyLockEnabled(dummyContext))
    }

    @Test
    fun test_familyLock_togglesAndPersists() {
        FamilyLockManager.setFamilyLockEnabled(dummyContext, true)
        assertTrue(FamilyLockManager.isFamilyLockEnabled(dummyContext))
        assertEquals(true, fakePrefs.storage[FamilyLockManager.KEY_FAMILY_LOCK_ENABLED])

        FamilyLockManager.setFamilyLockEnabled(dummyContext, false)
        assertFalse(FamilyLockManager.isFamilyLockEnabled(dummyContext))
        assertEquals(false, fakePrefs.storage[FamilyLockManager.KEY_FAMILY_LOCK_ENABLED])
    }

    @Test
    fun test_familyLock_constantsAreWellFormed() {
        assertEquals("antispam_settings", FamilyLockManager.PREFS_NAME)
        assertEquals("family_lock_enabled", FamilyLockManager.KEY_FAMILY_LOCK_ENABLED)
    }
}
