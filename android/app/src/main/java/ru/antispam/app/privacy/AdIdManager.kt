package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.AdIdPlatformType
import ru.antispam.app.privacy.model.AdIdPrivacyProfile

/**
 * AdIdManager: Manages and facilitates the reset/deletion of the Android Advertising ID (GAID / OAID).
 *
 * Threat Model:
 * Commercial telemetry SDKs (AppsFlyer, MyTracker, Yandex AppMetrica, Google Analytics) and
 * invasive superapps query the Google Advertising ID (GAID) or Huawei OAID as a persistent
 * pseudonymous cross-application identifier.
 *
 * Even if a user clears app data, logs in with separate accounts, or browses in private mode,
 * trackers continuously correlate the user's financial habits, social interactions, and daily
 * routine into a unified advertising dossier using this identifier.
 *
 * In modern Android, users have the power to either:
 * 1. "Delete advertising ID" (apps receive only zeros: 00000000-0000-0000-0000-000000000000).
 * 2. "Reset advertising ID" (generates a fresh random UUID, severing historical tracking).
 */
object AdIdManager {

    private const val TAG = "AdIdManager"

    const val ACTION_GMS_ADS_PRIVACY = "com.google.android.gms.settings.ADS_PRIVACY"
    const val ACTION_GMS_PRIVACY = "com.google.android.gms.settings.PRIVACY"
    const val ACTION_HMS_ADS_PRIVACY = "com.huawei.hms.settings.ADS_PRIVACY"
    const val ACTION_ANDROID_PRIVACY = "android.settings.PRIVACY_SETTINGS"

    /**
     * Determines whether Google Play Services or Huawei HMS powers the device ecosystem.
     */
    fun detectPlatform(context: Context): AdIdPlatformType {
        val pm = context.packageManager

        // Check Google Play Services
        try {
            pm.getPackageInfo("com.google.android.gms", 0)
            return AdIdPlatformType.GOOGLE_PLAY_SERVICES
        } catch (_: PackageManager.NameNotFoundException) {}

        // Check Huawei HMS
        try {
            pm.getPackageInfo("com.huawei.hwid", 0)
            return AdIdPlatformType.HUAWEI_HMS
        } catch (_: PackageManager.NameNotFoundException) {}

        try {
            pm.getPackageInfo("com.huawei.hms", 0)
            return AdIdPlatformType.HUAWEI_HMS
        } catch (_: PackageManager.NameNotFoundException) {}

        return AdIdPlatformType.AOSP_GENERIC
    }

    /**
     * Builds list of candidate Intents in priority order to open Advertising ID settings.
     */
    fun getCandidateIntents(): List<Intent> {
        val list = listOf(
            Intent(ACTION_GMS_ADS_PRIVACY),
            Intent(ACTION_GMS_PRIVACY),
            Intent(ACTION_ANDROID_PRIVACY),
            Intent(ACTION_HMS_ADS_PRIVACY),
            Intent(Settings.ACTION_PRIVACY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in list) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } catch (_: Exception) {}
        }
        return list
    }

    /**
     * Attempts to open the platform's native Advertising Privacy screen using candidate intents.
     * Returns true if successfully launched, false otherwise.
     */
    fun openAdvertisingSettings(context: Context): Boolean {
        val candidates = getCandidateIntents()
        val pm = context.packageManager

        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed intent ${intent.action}: ${e.message}")
            }
        }

        // Ultimate fallback to generic system settings
        return try {
            val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
            true
        } catch (e: Exception) {
            Log.e(TAG, "All advertising settings intents failed: ${e.message}")
            false
        }
    }

    /**
     * Generates a descriptive privacy profile with actionable steps for the current device.
     */
    fun getPrivacyProfile(context: Context): AdIdPrivacyProfile {
        val platform = detectPlatform(context)

        val threat = "Сквозной рекламный ID позволяет трекерам (MyTracker, AppMetrica, Google, Яндекс) склеивать все ваши действия в разных приложениях в один цифровой профиль."

        val steps = when (platform) {
            AdIdPlatformType.GOOGLE_PLAY_SERVICES -> listOf(
                "1. В открывшемся экране Google нажмите «Реклама» (или «Сброс рекламного идентификатора»).",
                "2. Нажмите «Удалить рекламный идентификатор» (рекомендуется) либо «Сбросить».",
                "3. При удалении все приложения получат пустую строку нулей (00000000-0000-0000-0000-000000000000), и сбор цифрового досье прекратится."
            )

            AdIdPlatformType.HUAWEI_HMS -> listOf(
                "1. В настройках конфиденциальности Huawei выберите «Реклама и конфиденциальность».",
                "2. Включите опцию «Ограничить отслеживание рекламы» (Disable personalized ads).",
                "3. Нажмите «Сбросить идентификатор OAID», чтобы стереть историю профилирования."
            )

            AdIdPlatformType.AOSP_GENERIC -> listOf(
                "1. В системных настройках откройте раздел «Конфиденциальность».",
                "2. Найдите пункт «Реклама» или «Идентификаторы устройств».",
                "3. Сбросьте или отключите персонализированный идентификатор рекламы."
            )
        }

        return AdIdPrivacyProfile(
            platformType = platform,
            trackingPlatformName = platform.displayName,
            threatSummary = threat,
            recommendedAction = "Удалить или сбросить рекламный ID",
            resetSteps = steps
        )
    }
}
