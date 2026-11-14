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
}
