package ru.antispam.app.privacy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MicJammerManager: Heuristic Microphone Jammer & Monopolistic Audio Focus Enforcer.
 * Under Android Microphone Sharing Policy (Android 10+):
 * When our communication foreground service holds monopolistic Audio Focus
 * (AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) and an active AudioRecord session,
 * the operating system prioritizes our stream and feeds background spy apps (e.g. MAX)
 * with empty audio buffers (zeros / absolute silence).
 * The cellular call (MODE_IN_CALL) at the hardware modem DSP level continues unaffected.
 */
object MicJammerManager {

    private const val TAG = "MicJammerManager"

    private val _isJamming = MutableStateFlow(false)
    val isJamming: StateFlow<Boolean> = _isJamming.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var jammingJob: Job? = null

    /**
     * Build protective AudioAttributes with ALLOW_CAPTURE_BY_NONE
     */
    fun buildProtectiveAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                }
            }
            .build()
    }

    /**
     * Activates monopolistic audio focus and dummy AudioRecord capture to force-silence background listeners.
     */
    @Synchronized
    fun startJamming(context: Context): Boolean {
        if (_isJamming.value) {
            Log.d(TAG, "MicJammer is already active.")
            return true
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val audioAttributes = buildProtectiveAudioAttributes()

        // 1. Request Exclusive Transient Audio Focus
        val focusGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { change ->
                    Log.d(TAG, "AudioFocus changed: $change")
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        if (!focusGranted) {
            Log.w(TAG, "Could not acquire exclusive audio focus for mic jamming.")
        }

        // 2. Start Monopolistic AudioRecord in VOICE_COMMUNICATION mode
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 2048)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record.startRecording()
                audioRecord = record
                _isJamming.value = true
                Log.i(TAG, "MicJammer ACTIVE: Foreground monopoly established, background listeners receiving silence.")

                // Lightweight drain loop: reading and discarding PCM stream
                jammingJob = CoroutineScope(Dispatchers.IO).launch {
                    val dummyBuffer = ByteArray(1024)
                    while (isActive && _isJamming.value) {
                        val read = record.read(dummyBuffer, 0, dummyBuffer.size)
                        if (read <= 0) break
                    }
                }
                return true
            } else {
                Log.w(TAG, "AudioRecord state was not INITIALIZED (state=${record.state})")
                record.release()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "RECORD_AUDIO permission not granted for MicJammer: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord for jamming: ${e.message}", e)
        }

        return false
    }

    /**
     * Stops jamming session and abandons audio focus.
     */
    @Synchronized
    fun stopJamming(context: Context) {
        if (!_isJamming.value && audioRecord == null) return

        Log.i(TAG, "Stopping MicJammer...")
        jammingJob?.cancel()
        jammingJob = null

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord: ${e.message}")
        }
        audioRecord = null

        // Abandon audio focus
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
        }

        _isJamming.value = false
        Log.i(TAG, "MicJammer deactivated.")
    }
}
