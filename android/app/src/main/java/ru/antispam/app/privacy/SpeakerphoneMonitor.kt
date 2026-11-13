package ru.antispam.app.privacy

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SpeakerphoneMonitor: Monitors speakerphone state during active phone calls.
 * When the user switches to Speakerphone, incoming voice is acoustically amplified
 * through device speakers, making the caller's voice physically accessible to any
 * microphone listener in user-space.
 */
object SpeakerphoneMonitor {

    private const val TAG = "SpeakerphoneMonitor"
    private const val SPEAKER_ALERT_NOTIFICATION_ID = 2003
    private const val CHANNEL_SPEAKER_ALERT_ID = "channel_mic_guard_alert"

    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()

    private var deviceCallback: AudioDeviceCallback? = null
    private var pollingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun startMonitoring(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        // 1. Check initial state
        checkSpeakerphoneState(context, audioManager)

        // 2. Register hardware device change callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkSpeakerphoneState(context, audioManager)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkSpeakerphoneState(context, audioManager)
                }
            }
            deviceCallback = callback
            audioManager.registerAudioDeviceCallback(callback, mainHandler)
        }

        // 3. Lightweight polling loop during the call to capture toggles in the system dialer
        pollingJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1500)
                checkSpeakerphoneState(context, audioManager)
            }
        }
        Log.d(TAG, "SpeakerphoneMonitor started")
    }

    private fun checkSpeakerphoneState(context: Context, audioManager: AudioManager) {
        val isSpeaker = audioManager.isSpeakerphoneOn
        if (_isSpeakerphoneOn.value != isSpeaker) {
            _isSpeakerphoneOn.value = isSpeaker
            if (isSpeaker) {
                val msg = "Включена громкая связь: голос собеседника звучит через внешний динамик и доступен микрофонам сторонних приложений!"
                _warningMessage.value = msg
                Log.w(TAG, "SPEAKERPHONE ON during call: acoustic eavesdropping vulnerability active.")
                showSpeakerWarningNotification(context, msg)
            } else {
                _warningMessage.value = null
                Log.i(TAG, "SPEAKERPHONE OFF: acoustic line secured.")
                dismissSpeakerWarningNotification(context)
            }
        }
    }

    private fun showSpeakerWarningNotification(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SPEAKER_ALERT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Внимание: Громкая связь активна")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SPEAKER_ALERT_NOTIFICATION_ID, notification)
    }

    private fun dismissSpeakerWarningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SPEAKER_ALERT_NOTIFICATION_ID)
    }

    @Synchronized
    fun stopMonitoring(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null && audioManager != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering audioDeviceCallback: ${e.message}")
            }
            deviceCallback = null
        }
        pollingJob?.cancel()
        pollingJob = null
        _isSpeakerphoneOn.value = false
        _warningMessage.value = null
        dismissSpeakerWarningNotification(context)
        Log.d(TAG, "SpeakerphoneMonitor stopped")
    }
}
