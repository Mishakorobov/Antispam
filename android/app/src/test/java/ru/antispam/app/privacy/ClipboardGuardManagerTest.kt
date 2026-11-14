package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClipboardGuardManagerTest {

    @Test
    fun test_isOtpOrSecret_standaloneCodes() {
        assertTrue(ClipboardGuardManager.isOtpOrSecret("1234"))
        assertTrue(ClipboardGuardManager.isOtpOrSecret("849201"))
        assertTrue(ClipboardGuardManager.isOtpOrSecret("99999999")) // 8 digits

        // Too short or too long
        assertFalse(ClipboardGuardManager.isOtpOrSecret("123")) // 3 digits
        assertFalse(ClipboardGuardManager.isOtpOrSecret("123456789")) // 9 digits
    }

    @Test
    fun test_isOtpOrSecret_doesNotShredRussianPhoneNumbers() {
        assertFalse(ClipboardGuardManager.isOtpOrSecret("+79001112233"))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("89161234567"))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("79998887766"))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("9001112233"))
    }

    @Test
    fun test_isOtpOrSecret_smsKeywordWithEmbeddedCode() {
        assertTrue(ClipboardGuardManager.isOtpOrSecret("Ваш код подтверждения для входа в Госуслуги: 849201"))
        assertTrue(ClipboardGuardManager.isOtpOrSecret("Sberbank: пароль для входа 5491"))
        assertTrue(ClipboardGuardManager.isOtpOrSecret("Одноразовый SMS OTP код: 204918"))
        assertTrue(ClipboardGuardManager.isOtpOrSecret("Код авторизации: 3321"))

        // Common text without security keywords
        assertFalse(ClipboardGuardManager.isOtpOrSecret("Заказ № 4921 доставлен в пункт выдачи"))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("Поезд 721 прибывает на путь 3"))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("Привет, как дела?"))
    }

    @Test
    fun test_isOtpOrSecret_nullOrBlank() {
        assertFalse(ClipboardGuardManager.isOtpOrSecret(null))
        assertFalse(ClipboardGuardManager.isOtpOrSecret(""))
        assertFalse(ClipboardGuardManager.isOtpOrSecret("   "))
    }

    @Test
    fun test_inspectAndScheduleShred_ignoresNonOtp() {
        var shredded = false
        val job = ClipboardGuardManager.inspectAndScheduleShred("Обычный скопированный текст") {
            shredded = true
        }

        assertNull(job)
        assertFalse(shredded)
    }

    @Test
    fun test_inspectAndScheduleShred_schedulesAndExecutesForOtp() {
        val latch = CountDownLatch(1)
        var shredded = false

        val job = ClipboardGuardManager.inspectAndScheduleShred("849201", delayMs = 50L) {
            shredded = true
            latch.countDown()
        }

        assertNotNull(job)
        val completed = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Shred callback should complete within timeout", completed)
        assertTrue(shredded)
        assertEquals("🔒 Одноразовый код стерт из буфера обмена в целях безопасности.", ClipboardGuardManager.lastShreddedEvent.value)

        // Clear alert
        ClipboardGuardManager.clearAlert()
        assertNull(ClipboardGuardManager.lastShreddedEvent.value)
    }

    @Test
    fun test_silentShreddedOtpCounter() {
        val storage = mutableMapOf<String, Any?>()
        val fakeEditor = object : android.content.SharedPreferences.Editor {
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor {
                storage[key] = value
                return this
            }
            override fun remove(key: String): android.content.SharedPreferences.Editor {
                storage.remove(key)
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                storage.clear()
                return this
            }
            override fun commit() = true
            override fun apply() {}
            override fun putString(key: String, value: String?) = this
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
        }
        val fakePrefs = object : android.content.SharedPreferences {
            override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
            override fun edit(): android.content.SharedPreferences.Editor = fakeEditor
            override fun getAll(): MutableMap<String, *> = storage
            override fun getString(key: String, defValue: String?) = null
            override fun getStringSet(key: String, defValues: MutableSet<String>?) = null
            override fun getLong(key: String, defValue: Long) = 0L
            override fun getFloat(key: String, defValue: Float) = 0f
            override fun getBoolean(key: String, defValue: Boolean) = false
            override fun contains(key: String) = storage.containsKey(key)
            override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        val dummyContext = object : android.content.ContextWrapper(null) {}
        ClipboardGuardManager.sharedPreferencesProvider = { fakePrefs }

        try {
            assertEquals(0, ClipboardGuardManager.getShreddedOtpCount(dummyContext))

            val count1 = ClipboardGuardManager.incrementShreddedOtpCount(dummyContext)
            assertEquals(1, count1)
            assertEquals(1, ClipboardGuardManager.shreddedOtpCount.value)

            val count2 = ClipboardGuardManager.incrementShreddedOtpCount(dummyContext)
            assertEquals(2, count2)
            assertEquals(2, ClipboardGuardManager.getShreddedOtpCount(dummyContext))

            ClipboardGuardManager.resetShreddedOtpCount(dummyContext)
            assertEquals(0, ClipboardGuardManager.getShreddedOtpCount(dummyContext))
            assertEquals(0, ClipboardGuardManager.shreddedOtpCount.value)
        } finally {
            ClipboardGuardManager.sharedPreferencesProvider = null
        }
    }
}
