package ru.antispam.app.privacy

import android.content.Context
import android.content.SharedPreferences

/**
 * TrustedAppManager:
 * Manages user-approved white-listed applications for sensitive Android permissions:
 * 1. NotificationListenerService (e.g. smartwatch companions: Garmin Connect, Huawei Health, Amazfit Zepp)
 * 2. AccessibilityService (e.g. password managers: Bitwarden, KeePassDX, 1Password, Proton Pass)
 *
 * Problem ("The Perpetual 80% UX Barrier"):
 * SecurityScoreEngine penalizes non-system apps with NotificationListener (-20 pts)
 * or Accessibility (-10 pts). Users with legitimate smartwatch apps or password managers
 * were permanently locked at 70–80% security score and could never achieve 100% (green ring).
 *
 * Solution:
 * Users can explicitly mark recognized applications as "Trusted".
 * SecurityScoreEngine exempts trusted applications from penalties, allowing the score to reach 100%.
 * If an unknown or rogue application acquires these permissions, penalties immediately re-engage.
 */
object TrustedAppManager {

    const val PREFS_NAME = "antispam_privacy_prefs"
    const val KEY_TRUSTED_NOTIFICATION_LISTENERS = "trusted_notification_listeners"
    const val KEY_TRUSTED_ACCESSIBILITY_SERVICES = "trusted_accessibility_services"

    /**
     * Recognized password managers and autofill accessibility services.
     */
    val KNOWN_PASSWORD_MANAGERS = setOf(
        "com.x8bit.bitwarden",
        "keepass2android.keepass2android",
        "keepass2android.keepass2android_nonet",
        "com.kunzisoft.keepass.free",
        "com.onepassword.android",
        "com.agilebits.onepassword",
        "me.proton.pass",
        "com.enpass.app",
        "com.dashlane",
        "com.safeincloud",
        "com.nordpass.android"
    )

    /**
     * Recognized wearable companions that mirror notifications to external accessories.
     */
    val KNOWN_WEARABLE_COMPANIONS = setOf(
        "com.samsung.android.app.watchmanager",
        "com.samsung.android.geargplugin",
        "com.huawei.health",
        "com.mi.health",
        "com.huami.midong",
        "com.xiaomi.wearable",
        "com.garmin.android.apps.connectmobile",
        "com.fitbit.FitbitMobile",
        "com.polar.polarflow",
        "com.oppo.wearable",
        "com.heytap.wearable.health",
        "com.google.android.wearable.app",
        "com.honor.health",
        "com.fitpro.app"
    )

    // Visible for testing / dependency injection
    internal var sharedPreferencesProvider: ((Context) -> SharedPreferences)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return sharedPreferencesProvider?.invoke(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the set of trusted NotificationListener package names (in lowercase).
     */
    fun getTrustedNotificationListeners(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_TRUSTED_NOTIFICATION_LISTENERS, emptySet())
            ?.map { it.lowercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Adds or removes a package name from the trusted NotificationListener whitelist.
     */
    fun setNotificationListenerTrusted(context: Context, packageName: String, isTrusted: Boolean) {
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_TRUSTED_NOTIFICATION_LISTENERS, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        val normalized = packageName.lowercase().trim()
        if (isTrusted) {
            current.add(normalized)
        } else {
            current.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_TRUSTED_NOTIFICATION_LISTENERS, current).apply()
    }

    fun isNotificationListenerTrusted(context: Context, packageName: String): Boolean {
        val trusted = getTrustedNotificationListeners(context)
        return trusted.contains(packageName.lowercase().trim())
    }

    /**
     * Returns the set of trusted Accessibility Service package names (in lowercase).
     */
    fun getTrustedAccessibilityServices(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_TRUSTED_ACCESSIBILITY_SERVICES, emptySet())
            ?.map { it.lowercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Adds or removes a package name from the trusted Accessibility Service whitelist.
     */
    fun setAccessibilityServiceTrusted(context: Context, packageName: String, isTrusted: Boolean) {
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_TRUSTED_ACCESSIBILITY_SERVICES, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        val normalized = packageName.lowercase().trim()
        if (isTrusted) {
            current.add(normalized)
        } else {
            current.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_TRUSTED_ACCESSIBILITY_SERVICES, current).apply()
    }

    fun isAccessibilityServiceTrusted(context: Context, packageName: String): Boolean {
        val trusted = getTrustedAccessibilityServices(context)
        return trusted.contains(packageName.lowercase().trim())
    }

    fun isRecognizedPasswordManager(packageName: String): Boolean {
        return KNOWN_PASSWORD_MANAGERS.contains(packageName.lowercase().trim())
    }

    fun isRecognizedWearableCompanion(packageName: String): Boolean {
        return KNOWN_WEARABLE_COMPANIONS.contains(packageName.lowercase().trim())
    }

    /**
     * Clears all trusted apps.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_TRUSTED_NOTIFICATION_LISTENERS)
            .remove(KEY_TRUSTED_ACCESSIBILITY_SERVICES)
            .apply()
    }
}
