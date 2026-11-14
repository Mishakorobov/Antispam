package ru.antispam.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.antispam.app.privacy.MicJammerManager
import ru.antispam.app.privacy.ProtectionPresetManager
import ru.antispam.app.privacy.model.ProtectionPreset

/**
 * Types of high-urgency alerts pushed to connected smartwatches.
 */
enum class WearableAlertType {
    ANTIFRAUD_SMS_OTP,
    MIC_EAVESDROPPING
}

/**
 * WearableAlertManager:
 * Dispatches high-priority interactive alerts to ALL connected smartwatches
 * (Huawei, Garmin, Amazfit/Zepp, Mi Band/Fitness, Galaxy Watch, Wear OS, Pixel Watch)
 * via `NotificationCompat.WearableExtender` without requiring a standalone watch APK.
 *
 * Provides a dedicated 1-click wrist action: [🛡️ Включить Бункер и Сбросить]
 * to instantly terminate an active compromised call and apply MAX_BUNKER security.
 */
object WearableAlertManager {

    private const val TAG = "WearableAlertManager"
    const val CHANNEL_ID = "channel_wearable_emergency"
    const val WEARABLE_NOTIFICATION_ID = 8888
    const val ACTION_BUNKER_AND_DROP = "ru.antispam.app.action.WEARABLE_BUNKER_AND_DROP"

    private val _lastAlert = MutableStateFlow<String?>(null)
    val lastAlert: StateFlow<String?> = _lastAlert.asStateFlow()

    private val _bunkerTriggeredFromWatch = MutableStateFlow(false)
    val bunkerTriggeredFromWatch: StateFlow<Boolean> = _bunkerTriggeredFromWatch.asStateFlow()

    /**
     * Sends an interactive emergency alert to connected wearables and heads-up display.
     */
    fun sendWearableAlert(
        context: Context,
        alertType: WearableAlertType,
        callerNumber: String? = null,
        detailText: String
    ) {
        try {
            createNotificationChannel(context)

            val (title, body) = when (alertType) {
                WearableAlertType.ANTIFRAUD_SMS_OTP -> {
                    val num = callerNumber ?: "неизвестного номера"
                    "🛑 ВНИМАНИЕ! Код взлома во время звонка!" to
                            "Во время звонка с $num пришел проверочный код: $detailText.\nНе называйте его собеседнику!"
                }
                WearableAlertType.MIC_EAVESDROPPING -> {
                    "🛑 ВНИМАНИЕ! Попытка прослушки!" to
                            "Обнаружен несанкционированный захват микрофона во время разговора: $detailText"
                }
            }

            _lastAlert.value = body

            // Intent to trigger Bunker profile and drop active call
            val bunkerIntent = Intent(context, WearableBunkerReceiver::class.java).apply {
                action = ACTION_BUNKER_AND_DROP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WEARABLE_NOTIFICATION_ID,
                bunkerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            // Wearable Action Button: [🛡️ Включить Бункер и Сбросить]
            val bunkerAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_lock_lock,
                "🛡️ Включить Бункер и Сбросить",
                pendingIntent
            ).build()

            // Wearable Extender: delivers priority alert and dedicated watch buttons to Huawei, Garmin, Amazfit, Wear OS
            val wearableExtender = NotificationCompat.WearableExtender()
                .addAction(bunkerAction)
                .setHintShowBackgroundOnly(false)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
                .addAction(bunkerAction) // Visible on phone heads-up
                .extend(wearableExtender) // Visible and interactive on all smartwatches
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(WEARABLE_NOTIFICATION_ID, notification)
            Log.i(TAG, "Sent Wearable Emergency Alert to watches: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending wearable alert: ${e.message}", e)
        }
    }

    /**
     * Dismisses any active wearable emergency alert.
     */
    fun dismissAlert(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(WEARABLE_NOTIFICATION_ID)
            _lastAlert.value = null
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing wearable alert: ${e.message}")
        }
    }

    /**
     * Executes the emergency Bunker & Drop Call command triggered from watch or phone.
     */
    fun onBunkerAndDropTriggered(context: Context) {
        Log.i(TAG, "⚡ EMERGENCY ACTION: Activating MAX_BUNKER and terminating call from watch!")
        _bunkerTriggeredFromWatch.value = true

        // 1. Terminate active call
        endCallSafely(context)

        // 2. Immediate fail-safe microphone jamming (blocks audio even if OS denies call termination)
        try {
            MicJammerManager.startJamming(context)
        } catch (e: Exception) {
            Log.w(TAG, "MicJammerManager.startJamming failed: ${e.message}")
        }

        // 3. Activate MAX_BUNKER preset (all shields up, incognito vault active, notifications silenced)
        try {
            ProtectionPresetManager.applyPreset(context, ProtectionPreset.MAX_BUNKER)
        } catch (e: Exception) {
            Log.w(TAG, "ProtectionPresetManager.applyPreset failed: ${e.message}")
        }

        // 4. Dismiss wearable alert
        dismissAlert(context)
    }

    /**
     * Attempts to terminate the active call across Android API versions.
     */
    fun endCallSafely(context: Context): Boolean {
        var callEnded = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null && context.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    callEnded = telecomManager.endCall()
                    Log.i(TAG, "Call terminated via TelecomManager.endCall(): $callEnded")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.endCall failed: ${e.message}")
        }

        if (!callEnded) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val getITelephony = telephonyManager?.javaClass?.getDeclaredMethod("getITelephony")
                getITelephony?.isAccessible = true
                val iTelephony = getITelephony?.invoke(telephonyManager)
                val endCallMethod = iTelephony?.javaClass?.getDeclaredMethod("endCall")
                endCallMethod?.isAccessible = true
                callEnded = endCallMethod?.invoke(iTelephony) as? Boolean ?: true
                Log.i(TAG, "Call terminated via ITelephony reflection: $callEnded")
            } catch (e: Exception) {
                Log.w(TAG, "ITelephony endCall failed: ${e.message}")
            }
        }
        return callEnded
    }

    fun resetStateForTesting() {
        _lastAlert.value = null
        _bunkerTriggeredFromWatch.value = false
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Алерты на смарт-часы"
            val descriptionText = "Высокоприоритетные предупреждения о прослушке и мошенничестве на подключенные часы"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }
}

/**
 * BroadcastReceiver triggered by the interactive [🛡️ Включить Бункер и Сбросить]
 * action on connected smartwatches or phone notification banner.
 */
class WearableBunkerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == null || action == WearableAlertManager.ACTION_BUNKER_AND_DROP) {
            WearableAlertManager.onBunkerAndDropTriggered(context)
        }
    }
}
