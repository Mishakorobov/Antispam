package ru.antispam.app.privacy

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkSanitizerHelperTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
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

        override fun putFloat(key: String, value: Float) = this
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun commit() = true
        override fun apply() {
            for ((k, v) in temp) {
                storage[k] = v
            }
            temp.clear()
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return storage[key] as? Boolean ?: defValue
        }

        override fun getInt(key: String, defValue: Int): Int {
            return storage[key] as? Int ?: defValue
        }

        override fun getLong(key: String, defValue: Long): Long {
            return storage[key] as? Long ?: defValue
        }

        override fun getString(key: String, defValue: String?): String? {
            return storage[key] as? String ?: defValue
        }

        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getAll() = storage
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun contains(key: String) = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        dummyContext = object : ContextWrapper(null) {}
        ClipboardGuardManager.sharedPreferencesProvider = { fakePrefs }
    }

    @After
    fun tearDown() {
        ClipboardGuardManager.sharedPreferencesProvider = null
    }

    @Test
    fun test_sanitizeText_stripsAllMarketingAndSpywareParameters() {
        val dirtyUrl = "https://gosuslugi.ru/service/123?utm_source=telegram&utm_medium=cpc&utm_campaign=spring&yclid=123456&gclid=789&vk_ref=feed&fbclid=xyz&igshid=abc&_openstat=direct&id=42#step2"

        val result = LinkSanitizerHelper.sanitizeText(dirtyUrl)

        assertTrue(result.wasSanitized)
        assertEquals("https://gosuslugi.ru/service/123?id=42#step2", result.sanitizedText)
        assertTrue(result.removedParams.contains("utm_source"))
        assertTrue(result.removedParams.contains("yclid"))
        assertTrue(result.removedParams.contains("gclid"))
        assertTrue(result.removedParams.contains("fbclid"))
        assertTrue(result.removedParams.contains("vk_ref"))
        assertTrue(result.removedParams.contains("igshid"))
        assertTrue(result.removedParams.contains("_openstat"))
        assertFalse(result.isPhishingThreat)
    }

    @Test
    fun test_sanitizeText_legitimateUrlsWithoutTrackingParams_remainUnchanged() {
        val cleanUrl = "https://gosuslugi.ru/service/123?id=42&category=passport#info"

        val result = LinkSanitizerHelper.sanitizeText(cleanUrl)

        assertFalse(result.wasSanitized)
        assertEquals(cleanUrl, result.sanitizedText)
        assertTrue(result.removedParams.isEmpty())
        assertFalse(result.isPhishingThreat)
    }

    @Test
    fun test_sanitizeText_plainTextWithoutUrl_returnsUnchanged() {
        val text = "Привет, встретимся в 18:00 у метро"

        val result = LinkSanitizerHelper.sanitizeText(text)

        assertFalse(result.wasSanitized)
        assertEquals(text, result.sanitizedText)
        assertFalse(result.isPhishingThreat)
    }

    @Test
    fun test_sanitizeText_embeddedUrlInsideTextMessage_stripsUrlParamsInsideText() {
        val message = "Привет! Зацени товар: https://market.yandex.ru/item/100?utm_source=tg&from=chat&id=55 спасибо"

        val result = LinkSanitizerHelper.sanitizeText(message)

        assertTrue(result.wasSanitized)
        assertTrue(result.sanitizedText.contains("https://market.yandex.ru/item/100?id=55"))
        assertTrue(result.sanitizedText.startsWith("Привет! Зацени товар:"))
        assertTrue(result.sanitizedText.endsWith("спасибо"))
        assertFalse(result.sanitizedText.contains("utm_source"))
        assertFalse(result.sanitizedText.contains("from=chat"))
    }

    @Test
    fun test_checkPhishing_homoglyphAttack_mixedLatinAndCyrillic() {
        // 'e' in sber is Cyrillic \u0435
        val homoglyphUrl = "https://sb\u0435r.ru/login"

        val result = LinkSanitizerHelper.sanitizeText(homoglyphUrl)

        assertTrue(result.isPhishingThreat)
        assertNotNull(result.phishingReason)
        assertTrue(result.phishingReason!!.contains("гомоглифы"))
    }

    @Test
    fun test_checkPhishing_brandTyposquatting() {
        val targets = listOf(
            "https://g0suslugi.ru/login" to "Госуслуг",
            "https://gos-uslugi.ru/cabinet" to "Госуслуг",
            "https://sber-online-login.ru/auth" to "Сбербанк",
            "https://avito-pay-safe.ru/pay" to "Авито",
            "https://cdek-track-pay.ru/order" to "СДЭК",
            "https://t-bank-login.ru/auth" to "Т-Банк",
            "https://vtb-online-login.ru/auth" to "ВТБ"
        )

        for ((url, brand) in targets) {
            val result = LinkSanitizerHelper.sanitizeText(url)
            assertTrue("Expected phishing threat for $url", result.isPhishingThreat)
            assertTrue("Expected reason to mention $brand for $url", result.phishingReason!!.contains(brand))
        }
    }

    @Test
    fun test_checkPhishing_officialDomains_passAsSafe() {
        val officialUrls = listOf(
            "https://gosuslugi.ru",
            "https://esia.gosuslugi.ru/service",
            "https://sberbank.ru/ru/person",
            "https://online.sberbank.ru",
            "https://tbank.ru/mybank",
            "https://avito.ru/moskva",
            "https://cdek.ru/tracking",
            "https://google.com/search?q=kotlin"
        )

        for (url in officialUrls) {
            val result = LinkSanitizerHelper.sanitizeText(url)
            assertFalse("Expected safe for $url", result.isPhishingThreat)
        }
    }

    @Test
    fun test_clipboardGuardManager_processClipText_withTrackableUrl_sanitizesAndIncrementsCounter() {
        var sanitizedClipResult: String? = null
        val dirtyUrl = "https://shop.ru/item?utm_source=vk&yclid=555&id=99"

        val processed = ClipboardGuardManager.processClipText(
            context = dummyContext,
            text = dirtyUrl,
            onSanitizeLink = { sanitizedUrl ->
                sanitizedClipResult = sanitizedUrl
            }
        )

        assertTrue(processed)
        assertEquals("https://shop.ru/item?id=99", sanitizedClipResult)
        assertEquals(1, ClipboardGuardManager.getSanitizedLinksCount(dummyContext))
    }
}
