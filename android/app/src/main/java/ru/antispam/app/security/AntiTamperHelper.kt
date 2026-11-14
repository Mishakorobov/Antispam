package ru.antispam.app.security

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.antispam.app.BuildConfig
import java.security.MessageDigest

/**
 * Status of the application package signature.
 */
enum class SignatureStatus {
    /**
     * Genuine release build signed with official production AntiSpam key.
     */
    GENUINE_RELEASE,

    /**
     * Development/Debug build signed with standard Android debug keystore.
     */
    DEBUG_BUILD,

    /**
     * Tampered / pirated build re-signed with an unauthorized third-party key.
     */
    TAMPERED
}

/**
 * Result of the package integrity and signature verification.
 */
data class SignatureVerificationResult(
    val status: SignatureStatus,
    val sha256Fingerprint: String?,
    val message: String
) {
    val isSafe: Boolean
        get() = status == SignatureStatus.GENUINE_RELEASE || status == SignatureStatus.DEBUG_BUILD
}

/**
 * AntiTamperHelper: Defense against APK re-packaging, trojanization, and memory/screen eavesdropping.
 *
 * Implements:
 * 1. Runtime signature verification against official AntiSpam certificate fingerprint.
 * 2. Window security enforcement (FLAG_SECURE) to block screenshot capture and screen recording
 *    via MediaProjection or accessibility scrapers.
 * 3. In-memory state tracking to display security alerts if an unauthorized build is detected.
 */
object AntiTamperHelper {

    private const val TAG = "AntiTamperHelper"

    /**
     * Official production certificate SHA-256 fingerprint (generated 2026-11-14, valid to 2054).
     */
    const val EXPECTED_RELEASE_SHA256 = "DBC812B2091C2ABE35753572534D9669348D074C88508C1409EA48F10DADD1C8"
    const val EXPECTED_RELEASE_SHA256_COLON = "DB:C8:12:B2:09:1C:2A:BE:35:75:35:72:53:4D:96:69:34:8D:07:4C:88:50:8C:14:09:EA:48:F1:0D:AD:D1:C8"

    private val _verificationFlow = MutableStateFlow<SignatureVerificationResult?>(null)
    val verificationFlow: StateFlow<SignatureVerificationResult?> = _verificationFlow.asStateFlow()

    var lastVerificationResult: SignatureVerificationResult? = null
        private set

    val isTampered: Boolean
        get() = lastVerificationResult?.status == SignatureStatus.TAMPERED

    /**
     * Visible for testing: inject mock signatures without invoking PackageManager.
     */
    internal var mockSignaturesForTest: List<ByteArray>? = null

    /**
     * Strips colons, spaces and normalizes to uppercase for robust hex comparison.
     */
    fun normalizeFingerprint(raw: String): String {
        return raw.replace(":", "").replace(" ", "").trim().uppercase()
    }

    /**
     * Computes formatted SHA-256 fingerprint (XX:XX:XX...) from certificate raw bytes.
     */
    fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Verifies the digital signature of the current APK.
     * Compares against the official AntiSpam certificate SHA-256.
     * In debug builds, permits standard debug certificates without throwing alerts.
     */
    @Synchronized
    fun verifyAppSignature(context: Context): SignatureVerificationResult {
        val certByteList: List<ByteArray> = mockSignaturesForTest ?: getPackageSignatureBytes(context)
        val isDebuggable = isAppDebuggable(context)
        val result = verifySignatureInternal(certByteList, isDebuggable)
        _verificationFlow.value = result
        lastVerificationResult = result
        return result
    }

    /**
     * Core verification logic separated for deterministic unit testing.
     */
    fun verifySignatureInternal(certByteList: List<ByteArray>, isDebuggable: Boolean): SignatureVerificationResult {
        if (certByteList.isEmpty()) {
            return SignatureVerificationResult(
                status = SignatureStatus.TAMPERED,
                sha256Fingerprint = null,
                message = "Не удалось извлечь сертификат подписи приложения."
            )
        }

        val normalizedExpected = normalizeFingerprint(EXPECTED_RELEASE_SHA256)
        var matchedFingerprint: String? = null
        var isMatch = false

        for (bytes in certByteList) {
            val formattedSha256 = computeSha256(bytes)
            val normalizedSha256 = normalizeFingerprint(formattedSha256)
            if (normalizedSha256 == normalizedExpected) {
                isMatch = true
                matchedFingerprint = formattedSha256
                break
            } else if (matchedFingerprint == null) {
                matchedFingerprint = formattedSha256
            }
        }

        return when {
            isMatch -> {
                Log.i(TAG, "Signature Verified: Official genuine release build ($matchedFingerprint).")
                SignatureVerificationResult(
                    status = SignatureStatus.GENUINE_RELEASE,
                    sha256Fingerprint = matchedFingerprint,
                    message = "Официальная подлинная сборка AntiSpam."
                )
            }
            isDebuggable -> {
                Log.w(TAG, "Debug build detected with non-production signature ($matchedFingerprint). Allowed for development.")
                SignatureVerificationResult(
                    status = SignatureStatus.DEBUG_BUILD,
                    sha256Fingerprint = matchedFingerprint,
                    message = "Сборка для разработки (Debug)."
                )
            }
            else -> {
                Log.e(TAG, "TAMPER DETECTED! Expected $EXPECTED_RELEASE_SHA256_COLON but found $matchedFingerprint")
                SignatureVerificationResult(
                    status = SignatureStatus.TAMPERED,
                    sha256Fingerprint = matchedFingerprint,
                    message = "Внимание! Обнаружена модифицированная версия приложения. Цифровая подпись не совпадает с официальным релизом AntiSpam."
                )
            }
        }
    }

    /**
     * Extracts certificate byte arrays from PackageManager using Android modern signing API.
     */
    private fun getPackageSignatureBytes(context: Context): List<ByteArray> {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    val signatures: Array<Signature> = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    signatures.map { it.toByteArray() }
                } else {
                    emptyList()
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying package signatures: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Checks if the package is in debug mode via ApplicationInfo flags or BuildConfig.
     */
    fun isAppDebuggable(context: Context): Boolean {
        return try {
            val flags = context.applicationInfo.flags
            (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 || BuildConfig.DEBUG
        } catch (e: Exception) {
            BuildConfig.DEBUG
        }
    }

    /**
     * Enforces FLAG_SECURE on the given Activity window.
     * Prevents screenshots, screen recording, and display mirroring by background spyware.
     */
    fun enforceSecureWindow(activity: Activity) {
        try {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            Log.d(TAG, "FLAG_SECURE enforced on ${activity.localClassName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set FLAG_SECURE: ${e.message}")
        }
    }

    /**
     * Clears FLAG_SECURE from the given Activity window.
     */
    fun clearSecureWindow(activity: Activity) {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            Log.d(TAG, "FLAG_SECURE cleared on ${activity.localClassName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear FLAG_SECURE: ${e.message}")
        }
    }

    /**
     * Toggles FLAG_SECURE on the Activity window.
     */
    fun setSecureWindow(activity: Activity, secure: Boolean) {
        if (secure) {
            enforceSecureWindow(activity)
        } else {
            clearSecureWindow(activity)
        }
    }
}
