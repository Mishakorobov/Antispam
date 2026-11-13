package ru.antispam.app.privacy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.antispam.app.MainActivity

class MicGuardService : Service() {

    companion object {
        private const val TAG = "MicGuardService"
        private const val NOTIFICATION_ID = 2001
        private const val ALERT_NOTIFICATION_ID = 2002
        private const val CHANNEL_MONITOR_ID = "channel_mic_guard_monitor"
        private const val CHANNEL_ALERT_ID = "channel_mic_guard_alert"

        private val _isMonitoring = MutableStateFlow(false)
        val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

        private val _lastAlert = MutableStateFlow<String?>(null)
        val lastAlert: StateFlow<String?> = _lastAlert.asStateFlow()

        fun startGuard(context: Context) {
            val intent = Intent(context, MicGuardService::class.java).apply {
                action = ACTION_START_GUARD
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MicGuardService: ${e.message}", e)
            }
        }

        fun stopGuard(context: Context) {
            val intent = Intent(context, MicGuardService::class.java).apply {
                action = ACTION_STOP_GUARD
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop MicGuardService: ${e.message}", e)
            }
        }

        private const val ACTION_START_GUARD = "ru.antispam.app.action.START_MIC_GUARD"
        private const val ACTION_STOP_GUARD = "ru.antispam.app.action.STOP_MIC_GUARD"
    }

    private lateinit var audioManager: AudioManager
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GUARD -> startMonitoring()
            ACTION_STOP_GUARD -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (_isMonitoring.value) return

        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        _isMonitoring.value = true
        Log.i(TAG, "Call Privacy Shield: MicGuard monitoring started")

        // Phase 5.3: Activate Speakerphone monitor during call
        SpeakerphoneMonitor.startMonitoring(this)

        // Phase 5.3: Check if automatic preemptive jamming is configured
        val privacyPrefs = getSharedPreferences("antispam_privacy_prefs", Context.MODE_PRIVATE)
        if (privacyPrefs.getBoolean("mic_jammer_auto_enabled", true)) {
            MicJammerManager.startJamming(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            recordingCallback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
                    super.onRecordingConfigChanged(configs)
                    inspectRecordingConfigurations(configs ?: emptyList())
                }
            }
            audioManager.registerAudioRecordingCallback(recordingCallback!!, mainHandler)
        }
    }

    private fun inspectRecordingConfigurations(configs: List<AudioRecordingConfiguration>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        var suspiciousAppDetected = false
        var clientSourceDesc = ""

        for (config in configs) {
            val source = config.clientAudioSource
            // Audio sources that indicate active microphone capturing by apps:
            val isMicCapture = source == MediaRecorder.AudioSource.MIC ||
                    source == MediaRecorder.AudioSource.VOICE_RECOGNITION ||
                    source == MediaRecorder.AudioSource.VOICE_COMMUNICATION

            // Exclude telephony modem source if defined
            val isVoiceCallModem = source == MediaRecorder.AudioSource.VOICE_CALL ||
                    source == MediaRecorder.AudioSource.VOICE_DOWNLINK ||
                    source == MediaRecorder.AudioSource.VOICE_UPLINK

            if (isMicCapture && !isVoiceCallModem) {
                suspiciousAppDetected = true
                clientSourceDesc = when (source) {
                    MediaRecorder.AudioSource.MIC -> "Микрофон (MIC)"
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "Распознавание речи (VOICE_RECOGNITION)"
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "Голосовая связь (VOICE_COMMUNICATION)"
                    else -> "AudioSource ($source)"
                }
                break
            }
        }

        if (suspiciousAppDetected) {
            val message = "Обнаружен фоновый захват микрофона ($clientSourceDesc) во время телефонного разговора!"
            Log.w(TAG, "ALERT: $message")
            _lastAlert.value = message

            // Phase 5.3: Instantly activate Heuristic Mic Jammer to silence the eavesdropping app
            MicJammerManager.startJamming(this)

            triggerEavesdroppingAlert(message)
        }
    }

    private fun triggerEavesdroppingAlert(alertText: String) {
        // 1. Tactile alarm (vibration)
        triggerVibration()

        // 2. High Priority Heads-Up Alert Notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Внимание! Попытка прослушивания")
            .setContentText(alertText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertText + "\n\nСтороннее приложение пытается слушать микрофон во время вашего разговора."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun triggerVibration() {
        try {
            val pattern = longArrayOf(0, 350, 150, 350)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun stopMonitoring() {
        // Phase 5.3: Stop Jammer and Speakerphone monitors
        MicJammerManager.stopJamming(this)
        SpeakerphoneMonitor.stopMonitoring(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recordingCallback != null) {
            try {
                audioManager.unregisterAudioRecordingCallback(recordingCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering recording callback: ${e.message}")
            }
            recordingCallback = null
        }
        _isMonitoring.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Call Privacy Shield: MicGuard monitoring stopped")
    }

    private fun createForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Защита линии активна")
            .setContentText("Микрофон защищен от прослушивания сторонними приложениями")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Background Monitoring Channel (Silent)
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "Мониторинг приватности микрофона",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление об активной защите микрофона во время разговора"
            }

            // High Priority Alert Channel (Heads-Up)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Предупреждения о перехвате микрофона",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Срочные оповещения о попытках фоновой записи звука"
                enableVibration(true)
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
