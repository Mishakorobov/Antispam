package ru.antispam.app.antifraud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntiFraudCallSmsMonitorTest {

    @Before
    fun setUp() {
        AntiFraudCallSmsMonitor.stopActiveCallMonitoring()
        AntiFraudCallSmsMonitor.clearAlert()
    }

    @After
    fun tearDown() {
        AntiFraudCallSmsMonitor.stopActiveCallMonitoring()
        AntiFraudCallSmsMonitor.clearAlert()
    }

    @Test
    fun test_callMonitoringLifecycle() {
        assertFalse(AntiFraudCallSmsMonitor.isCallWithUnknownActive.value)
        assertNull(AntiFraudCallSmsMonitor.activeCallerNumber.value)

        val testNumber = "+79991234567"
        AntiFraudCallSmsMonitor.startActiveCallMonitoring(testNumber)

        assertTrue(AntiFraudCallSmsMonitor.isCallWithUnknownActive.value)
        assertEquals(testNumber, AntiFraudCallSmsMonitor.activeCallerNumber.value)

        AntiFraudCallSmsMonitor.stopActiveCallMonitoring()
        assertFalse(AntiFraudCallSmsMonitor.isCallWithUnknownActive.value)
        assertNull(AntiFraudCallSmsMonitor.activeCallerNumber.value)
    }

    @Test
    fun test_isFraudVerificationMessage_gosuslugiPatterns() {
        val msg1 = "Ваш код подтверждения для входа в Госуслуги: 849201"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(msg1, sender = "Gosuslugi"))

        val msg2 = "Gosuslugi: никому не сообщайте пароль 592018"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(msg2, sender = "109"))

        val msg3 = "Код подтверждения заявки на Госуслугах: 3341"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(msg3))
    }

    @Test
    fun test_isFraudVerificationMessage_bankPatterns() {
        val sberMsg = "900: Пароль для входа в Сбербанк Онлайн 48291. Никому не называйте!"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(sberMsg, sender = "900"))

        val tbankMsg = "Т-Банк: код авторизации 29401"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(tbankMsg, sender = "T-Bank"))

        val vtbMsg = "ВТБ: Код подтверждения перевода 7401. Не сообщайте его никому"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(vtbMsg, sender = "VTB"))

        val alfaMsg = "Альфа-Банк: код входа в приложение 6192"
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(alfaMsg, sender = "Alfa-Bank"))
    }

    @Test
    fun test_isFraudVerificationMessage_benignMessagesAreIgnored() {
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage("Привет, как дела? Перезвони мне"))
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage("Заказ 4921 доставлен в пункт выдачи CDEK"))
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage("Баланс вашего счета пополнен на 500 руб"))
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage(null))
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage(""))
        assertFalse(AntiFraudCallSmsMonitor.isFraudVerificationMessage("   "))
    }

    @Test
    fun test_processIncomingSms_inactiveCallDoesNotTriggerAlert() {
        val dummyContext = object : android.content.ContextWrapper(null) {}

        // Monitoring is NOT active
        val triggered = AntiFraudCallSmsMonitor.processIncomingSms(
            context = dummyContext,
            text = "Ваш код Госуслуги: 849201",
            sender = "Gosuslugi"
        )

        assertFalse("Should not trigger when no active call is underway", triggered)
        assertNull(AntiFraudCallSmsMonitor.lastAlertEvent.value)
    }

    @Test
    fun test_processIncomingSms_benignMessageDuringCallDoesNotTriggerAlert() {
        val dummyContext = object : android.content.ContextWrapper(null) {}

        AntiFraudCallSmsMonitor.startActiveCallMonitoring("+79998887766")

        val triggered = AntiFraudCallSmsMonitor.processIncomingSms(
            context = dummyContext,
            text = "Привет, я у подъезда",
            sender = "+79161112233"
        )

        assertFalse("Benign SMS should not trigger fraud alert", triggered)
        assertNull(AntiFraudCallSmsMonitor.lastAlertEvent.value)
    }

    @Test
    fun test_processIncomingSms_criticalFraudDetectedDuringCall() {
        val dummyContext = object : android.content.ContextWrapper(null) {}
        val callerNumber = "+79998887766"

        AntiFraudCallSmsMonitor.startActiveCallMonitoring(callerNumber)

        val fraudText = "Никому не сообщайте! Код для входа в Госуслуги: 582910"
        val sender = "Gosuslugi"

        val triggered = AntiFraudCallSmsMonitor.processIncomingSms(
            context = dummyContext,
            text = fraudText,
            sender = sender
        )

        assertTrue("Emergency fraud alert must trigger!", triggered)

        val event = AntiFraudCallSmsMonitor.lastAlertEvent.value
        assertNotNull(event)
        assertEquals(callerNumber, event?.callerNumber)
        assertEquals(fraudText, event?.messageSnippet)
        assertEquals(sender, event?.sender)
    }
}
