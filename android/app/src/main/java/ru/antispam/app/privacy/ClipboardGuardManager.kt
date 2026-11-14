package ru.antispam.app.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ClipboardGuardManager: Automated 2FA OTP and sensitive credentials shredder.
 *
 * Threat:
 * Malicious background apps and commercial superapps (e.g. VK, MAX, cleaners) with foreground
 * access periodically poll the clipboard or scrape it upon opening to harvest bank OTP codes,
 * Gosuslugi verification pins, and passwords copied by the user.
 *
 * Solution:
 * Monitors clipboard changes, recognizes OTP patterns (4-8 digits) and verification keywords,
 * and automatically purges the clipboard after 30 seconds (`clearPrimaryClip()`),
 * leaving zero residue for snooping apps.
 */
object ClipboardGuardManager {

    private const val TAG = "ClipboardGuardManager"
    const val PREFS_NAME = "antispam_privacy_prefs"
    const val KEY_CLIPBOARD_GUARD_ENABLED = "clipboard_guard_enabled"
    const val KEY_SHREDDED_OTP_COUNT = "shredded_otp_count"
    const val DEFAULT_SHRED_DELAY_MS = 30_000L // 30 seconds

    private val _isGuarding = MutableStateFlow(false)
    val isGuarding: StateFlow<Boolean> = _isGuarding.asStateFlow()

    private val _lastShreddedEvent = MutableStateFlow<String?>(null)
    val lastShreddedEvent: StateFlow<String?> = _lastShreddedEvent.asStateFlow()

    private val _shreddedOtpCount = MutableStateFlow(0)
    val shreddedOtpCount: StateFlow<Int> = _shreddedOtpCount.asStateFlow()

    internal var sharedPreferencesProvider: ((Context) -> SharedPreferences)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return sharedPreferencesProvider?.invoke(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Phase 11.4: Retrieves total number of shredded 2FA OTP codes.
     */
    fun getShreddedOtpCount(context: Context): Int {
        val count = getPrefs(context).getInt(KEY_SHREDDED_OTP_COUNT, 0)
        _shreddedOtpCount.value = count
        return count
    }

    /**
     * Phase 11.4: Silently increments the shredded OTP counter in SharedPreferences.
     */
    fun incrementShreddedOtpCount(context: Context): Int {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_SHREDDED_OTP_COUNT, 0)
        val next = current + 1
        prefs.edit().putInt(KEY_SHREDDED_OTP_COUNT, next).apply()
        _shreddedOtpCount.value = next
        return next
    }

    /**
     * Phase 11.4: Resets the shredded OTP counter.
     */
    fun resetShreddedOtpCount(context: Context) {
        getPrefs(context).edit().putInt(KEY_SHREDDED_OTP_COUNT, 0).apply()
        _shreddedOtpCount.value = 0
    }

    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    internal var activeShredJob: Job? = null
    private val guardScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Direct standalone OTP code: 4 to 8 digits
    private val STANDALONE_OTP_REGEX = Regex("^\\d{4,8}$")

    // Sensitive context keywords in SMS or copied text
    private val SENSITIVE_KEYWORDS = listOf(
        "код", "code", "пароль", "password", "otp", "2fa",
        "госуслуг", "подтвержд", "авторизац", "вход", "pin", "пин"
    )

    private val EMBEDDED_OTP_REGEX = Regex("\\b\\d{4,8}\\b")

    /**
     * Determines whether text represents a short-lived 2FA OTP code or verification secret.
     * Excludes telephone numbers (10..15 digits, or starting with +7, 89, 79) to avoid
     * disturbing normal phone copying workflows.
     */
    fun isOtpOrSecret(rawText: String?): Boolean {
        if (rawText.isNullOrBlank()) return false
        val trimmed = rawText.trim()

        // 1. Direct standalone code of 4..8 digits
        if (STANDALONE_OTP_REGEX.matches(trimmed)) {
            return true
        }

        // 2. Reject if it clearly looks like a Russian mobile phone number
        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
        if (digitsOnly.length in 10..12 && (digitsOnly.startsWith("79") || digitsOnly.startsWith("89") || digitsOnly.startsWith("9"))) {
            return false
        }

        // 3. Text containing sensitive keywords alongside an embedded 4..8 digit code
        val lower = trimmed.lowercase()
        val hasKeyword = SENSITIVE_KEYWORDS.any { lower.contains(it) }
        if (hasKeyword && EMBEDDED_OTP_REGEX.containsMatchIn(trimmed)) {
            return true
        }

        return false
    }

    /**
     * Core inspection and shred scheduling separated for deterministic unit testing.
     */
    fun inspectAndScheduleShred(
        clipText: String?,
        delayMs: Long = DEFAULT_SHRED_DELAY_MS,
        onShred: () -> Unit
    ): Job? {
        if (!isOtpOrSecret(clipText)) {
            return null
        }

        activeShredJob?.cancel()
        val job = guardScope.launch {
            Log.i(TAG, "OTP/2FA code detected in clipboard. Scheduling auto-shred in ${delayMs / 1000}s...")
            delay(delayMs)
            try {
                onShred()
                Log.i(TAG, "Clipboard successfully shredded! Primary clip cleared.")
                _lastShreddedEvent.value = "🔒 Одноразовый код стерт из буфера обмена в целях безопасности."
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear clipboard: ${e.message}", e)
            }
        }
        activeShredJob = job
        return job
    }

    /**
     * Starts clipboard monitoring.
     */
    fun startGuard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (clipListener != null) return

        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    inspectAndScheduleShred(text) {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    clipboard.clearPrimaryClip()
                                } else {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                                }
                                // Phase 11.4: Silent increment of shredded OTP counter (no noisy Toast popup)
                                val count = incrementShreddedOtpCount(context)
                                Log.i(TAG, "Silent shred completed. Total shredded 2FA OTPs: $count")
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error inspecting primary clip: ${e.message}")
            }
        }

        clipboard.addPrimaryClipChangedListener(listener)
        clipListener = listener
        _isGuarding.value = true
        Log.i(TAG, "ClipboardGuard started. 2FA auto-shredder active.")
    }

    /**
     * Stops clipboard monitoring and cancels pending shred jobs.
     */
    fun stopGuard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipListener?.let {
            clipboard?.removePrimaryClipChangedListener(it)
            clipListener = null
        }
        activeShredJob?.cancel()
        activeShredJob = null
        _isGuarding.value = false
        Log.i(TAG, "ClipboardGuard stopped.")
    }

    /**
     * Checks user preference and initializes or stops guard accordingly.
     */
    fun updateStateFromPreferences(context: Context) {
        getShreddedOtpCount(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_CLIPBOARD_GUARD_ENABLED, true)
        if (enabled) {
            startGuard(context)
        } else {
            stopGuard(context)
        }
    }

    fun clearAlert() {
        _lastShreddedEvent.value = null
    }
}
