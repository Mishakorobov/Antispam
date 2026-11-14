package ru.antispam.app.antifraud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.antispam.app.ui.antifraud.AntiFraudAlertActivity

/**
 * Data model for a detected real-time anti-fraud emergency event.
 */
data class AntiFraudAlertEvent(
    val callerNumber: String,
    val messageSnippet: String,
    val sender: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AntiFraudCallSmsMonitor:
 * Real-time Anti-Fraud Detector protecting users against social engineering during active calls.
 *
 * Threat:
 * The dominant telephone scam vector: scammers call victims from clean/unflagged numbers or spoofed DIDs
 * claiming to be from "Gosuslugi", "Telecom Provider", or "Bank Security". During the call, the attacker
 * triggers a 2FA login code and pressures the victim into reading the incoming SMS verification code.
 *
 * Solution:
 * When an incoming call from an unknown number is answered (EXTRA_STATE_OFFHOOK):
 * 1. Active call monitoring engages.
 * 2. If an incoming SMS or verification notification containing authentication patterns
 *    (code, Gosuslugi, 900, password, do not disclose) arrives while the call is ongoing:
 * 3. Triggers immediate pulsed emergency vibration and launches a full-screen HUD warning:
 *    "🛑 СТОП! ВАМ ЗВОНЯТ МОШЕННИКИ! НИ В КОЕМ СЛУЧАЕ НЕ НАЗЫВАЙТЕ КОД ИЗ СМС — ПОЛОЖИТЕ ТРУБКУ!"
 */
object AntiFraudCallSmsMonitor {

    private const val TAG = "AntiFraudMonitor"
    const val NOTIFICATION_CHANNEL_ID = "antifraud_emergency_channel"
    private const val NOTIFICATION_ID = 9001

    private val _isCallWithUnknownActive = MutableStateFlow(false)
    val isCallWithUnknownActive: StateFlow<Boolean> = _isCallWithUnknownActive.asStateFlow()

    private val _activeCallerNumber = MutableStateFlow<String?>(null)
    val activeCallerNumber: StateFlow<String?> = _activeCallerNumber.asStateFlow()

    private val _lastAlertEvent = MutableStateFlow<AntiFraudAlertEvent?>(null)
    val lastAlertEvent: StateFlow<AntiFraudAlertEvent?> = _lastAlertEvent.asStateFlow()

    // Sensitive context keywords indicative of 2FA login or transaction confirmation
    val FRAUD_KEYWORDS = setOf(
        "код", "code", "госуслуг", "gosuslugi", "900", "пароль", "password",
        "никому не сообщайте", "не называйте", "авторизац", "подтвержд",
        "сбер", "sber", "тинькофф", "tinkoff", "т-банк", "t-bank",
        "втб", "vtb", "альфа", "alfa", "яндекс", "yandex", "вход"
    )

    // Regex finding 4 to 8 digit verification codes
    val EMBEDDED_OTP_REGEX = Regex("\\b\\d{4,8}\\b")

    /**
     * Starts tracking an active conversation with an unknown caller.
     */
    fun startActiveCallMonitoring(callerNumber: String) {
        _activeCallerNumber.value = callerNumber
        _isCallWithUnknownActive.value = true
        Log.i(TAG, "Active call with unknown number started: $callerNumber. Anti-Fraud Monitor armed.")
    }

    /**
     * Stops tracking when the call terminates.
     */
    fun stopActiveCallMonitoring() {
        Log.i(TAG, "Active call ended. Disarming Anti-Fraud Monitor.")
        _isCallWithUnknownActive.value = false
        _activeCallerNumber.value = null
    }

    /**
     * Inspects message text and sender to detect whether it contains a sensitive verification code.
     */
    fun isFraudVerificationMessage(text: String?, sender: String? = null): Boolean {
        if (text.isNullOrBlank()) return false
        val lowerText = text.lowercase()
        val lowerSender = sender?.lowercase() ?: ""

        val dynamicKeywords = ru.antispam.app.security.DynamicConfigManager.getDynamicAntifraudKeywords()
        val hasKeyword = FRAUD_KEYWORDS.any { lowerText.contains(it) || lowerSender.contains(it) } ||
                dynamicKeywords.any { lowerText.contains(it.lowercase()) || lowerSender.contains(it.lowercase()) }
        val hasOtpCode = EMBEDDED_OTP_REGEX.containsMatchIn(text)

        return hasKeyword && hasOtpCode
    }

    /**
     * Processes an incoming SMS. If an active call with an unknown number is underway and
     * the SMS contains a verification code, triggers the emergency HUD alert.
     */
    fun processIncomingSms(context: Context, text: String?, sender: String?): Boolean {
        if (!_isCallWithUnknownActive.value) {
            return false // No active call with unknown number
        }

        if (!isFraudVerificationMessage(text, sender)) {
            return false // Ordinary non-verification message
        }

        val caller = _activeCallerNumber.value ?: "Неизвестный номер"
        val snippet = text?.trim()?.take(200) ?: ""

        Log.w(TAG, "🚨 CRITICAL FRAUD ATTEMPT DETECTED! Call in progress with $caller and verification code received from $sender!")
        triggerEmergencyAlert(context, caller, snippet, sender)
        return true
    }

    /**
     * Triggers pulsed haptic alert, high-priority heads-up notification, and full-screen HUD Activity.
     */
    fun triggerEmergencyAlert(
        context: Context,
        callerNumber: String,
        messageSnippet: String,
        sender: String?
    ) {
        val event = AntiFraudAlertEvent(
            callerNumber = callerNumber,
            messageSnippet = messageSnippet,
            sender = sender
        )
        _lastAlertEvent.value = event

        // 1. Intense intermittent emergency vibration
        vibrateEmergency(context)

        // 2. High-priority notification with fullScreenIntent and direct HUD Activity launch
        try {
            createNotificationChannel(context)

            val fullScreenIntent = Intent(context, AntiFraudAlertActivity::class.java).apply {
                putExtra(AntiFraudAlertActivity.EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(AntiFraudAlertActivity.EXTRA_MESSAGE_SNIPPET, messageSnippet)
                putExtra(AntiFraudAlertActivity.EXTRA_SENDER, sender)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🛑 СТОП! ВАМ ЗВОНЯТ МОШЕННИКИ!")
                .setContentText("Во время разговора пришел код подтверждения! Не называйте его собеседнику!")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Во время разговора с неизвестным номером ($callerNumber) пришел код: \"$messageSnippet\".\n\nНи в коем случае не называйте его собеседнику — положите трубку!"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .apply {
                    if (pendingIntent != null) {
                        setFullScreenIntent(pendingIntent, true)
                    }
                }
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, notification)

            // 3. Launch HUD Activity directly
            context.startActivity(fullScreenIntent)

            // 4. Phase 13.2: Send interactive alert to connected smartwatches via WearableExtender
            ru.antispam.app.service.WearableAlertManager.sendWearableAlert(
                context = context,
                alertType = ru.antispam.app.service.WearableAlertType.ANTIFRAUD_SMS_OTP,
                callerNumber = callerNumber,
                detailText = messageSnippet
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error launching emergency notification/HUD: ${e.message}")
        }
    }

    private fun vibrateEmergency(context: Context) {
        try {
            val pattern = longArrayOf(0, 450, 150, 450, 150, 750)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vm?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Экстренные предупреждения Анти-Фрод"
            val descriptionText = "Уведомления реального времени при попытке выманивания SMS-кодов во время звонка"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 450, 150, 450, 150, 750)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun clearAlert() {
        _lastAlertEvent.value = null
    }
}
