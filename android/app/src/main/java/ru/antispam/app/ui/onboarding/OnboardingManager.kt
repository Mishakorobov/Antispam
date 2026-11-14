package ru.antispam.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/**
 * OnboardingManager:
 * Manages the onboarding lifecycle and the "Zero-Telemetry Manifesto" state.
 *
 * Enforces transparency: guarantees that the user explicitly reviews and accepts
 * the Sovereign Privacy & Local-First computing principles before using the firewall.
 */
object OnboardingManager {

    private const val PREFS_NAME = "antispam_onboarding_prefs"
    private const val KEY_MANIFEST_ACCEPTED = "manifest_accepted"
    private const val KEY_ACCEPTED_TIMESTAMP = "manifest_accepted_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks whether the user has reviewed and accepted the Zero-Telemetry Manifesto.
     */
    fun isManifestAccepted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MANIFEST_ACCEPTED, false)
    }

    /**
     * Records the user's acceptance of the Zero-Telemetry Manifesto.
     */
    fun setManifestAccepted(context: Context, accepted: Boolean = true) {
        getPrefs(context).edit()
            .putBoolean(KEY_MANIFEST_ACCEPTED, accepted)
            .putLong(KEY_ACCEPTED_TIMESTAMP, if (accepted) System.currentTimeMillis() else 0L)
            .apply()
    }

    /**
     * Resets the onboarding state (used for testing or privacy audit re-runs).
     */
    fun reset(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Returns the full text of the Zero-Telemetry Manifesto for sharing.
     */
    fun getManifestShareText(): String {
        return """
            🛡️ Манифест Нулевой Телеметрии | AntiSpam Shield
            
            В отличие от коммерческих определителей номеров (GetContact, Яндекс, Kaspersky WhoCalls, Truecaller), мы НИКОГДА не выгружаем вашу телефонную книгу, историю звонков или геопозицию в облако.
            
            Все 10 000 000+ спам-номеров и пулов колл-центров проверяются прямо внутри процессора вашего смартфона за 1 мс без доступа к интернету.
            
            5 принципов суверенной защиты связи:
            1. Нулевая выгрузка контактов (Zero Cloud Upload) — ваши контакты никогда не покинут чип смартфона.
            2. Локальная обработка на процессоре (On-Device Local-First) — поиск O(log N) в SQLite даже в режиме полета.
            3. Математический Zero-Trust Ed25519 — криптографическая гарантия неизменности базы и Anti-Rollback защита.
            4. Анти-шпионский щит — защита микрофона от прослушки (MicGuard), аудит переадресации (*#21#) и защита от 2G IMSI catchers.
            5. GMS-Free & Офлайн P2P — полная независимость от Google Play Services, обмен базой по Bluetooth/Quick Share.
            
            Персональный суверенный файрвол связи AntiSpam Shield.
        """.trimIndent()
    }

    /**
     * Creates an Intent to share the Zero-Telemetry Manifesto.
     */
    fun createShareIntent(): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Манифест Нулевой Телеметрии | AntiSpam Shield")
            putExtra(Intent.EXTRA_TEXT, getManifestShareText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Поделиться Манифестом Приватности")
        return chooser?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: sendIntent
    }

    /**
     * Launches system ACTION_SEND intent to share the Zero-Telemetry Manifesto.
     */
    fun shareManifest(context: Context) {
        val intent = createShareIntent()
        context.startActivity(intent)
    }
}
