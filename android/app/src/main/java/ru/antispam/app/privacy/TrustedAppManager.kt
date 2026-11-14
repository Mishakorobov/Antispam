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
    const val KEY_TRUSTED_SUPERAPPS = "trusted_superapps"
    const val KEY_TRUSTED_AUDIT_APPS = "trusted_audit_apps"

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
        "com.safeincloud.free",
        "com.nordpass.android",
        "com.lastpass.lpandroid",
        "com.keepersecurity.android",
        "com.samsung.android.samsungpass",
        "com.samsung.android.samsungpassautofill",
        "com.samsung.android.authfw",
        "com.kaspersky.passwordmanager",
        "com.yandex.passwords",
        "com.google.android.gms"
    )

    /**
     * Recognized core system and OEM services (Samsung One UI, Microsoft Link, Android Auto)
     * that legitimately access system notification hooks and system UI.
     */
    val KNOWN_SYSTEM_OEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.microsoft.appmanager",
        "com.google.android.projection.gearhead",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.modes",
        "com.samsung.android.app.routines",
        "com.samsung.android.smartmirroring"
    )

    /**
     * Recognized wearable companions, car integration and official system accessories
     * that legitimately mirror notifications or sync with external hardware.
     */
    val KNOWN_WEARABLE_COMPANIONS = setOf(
        // Samsung Wearables, Plugins and One UI
        "com.samsung.android.app.watchmanager",
        "com.samsung.android.geargplugin",
        "com.samsung.android.gearrplugin",
        "com.samsung.android.gearnplugin",
        "com.samsung.android.waterplugin",
        "com.samsung.android.neatplugin",
        "com.samsung.android.modenplugin",
        "com.samsung.wearable.fit3plugin",
        "com.samsung.android.wearable.fit3plugin",
        "com.samsung.accessory",
        "com.samsung.accessory.wmanager",
        "com.google.android.wearable.app",
        // Huawei & Honor
        "com.huawei.health",
        "com.huawei.health.oversea",
        "com.honor.health",
        // Xiaomi, Amazfit & Zepp
        "com.mi.health",
        "com.huami.midong",
        "com.xiaomi.wearable",
        "com.xiaomi.hm.health",
        "com.zepp.main",
        // Garmin, Fitbit & Polar
        "com.garmin.android.apps.connectmobile",
        "com.fitbit.FitbitMobile",
        "com.polar.polarflow",
        // Oppo, Vivo & others
        "com.oppo.wearable",
        "com.heytap.wearable.health",
        "com.suunto.companion",
        "com.withings.wiscale2",
        "com.coros.android",
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
     * Returns the set of user-whitelisted SuperApp package names.
     */
    fun getTrustedSuperApps(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_TRUSTED_SUPERAPPS, emptySet())
            ?.map { it.lowercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Adds or removes an app from the trusted SuperApp whitelist.
     */
    fun setSuperAppTrusted(context: Context, packageName: String, isTrusted: Boolean) {
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_TRUSTED_SUPERAPPS, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        val normalized = packageName.lowercase().trim()
        if (isTrusted) {
            current.add(normalized)
        } else {
            current.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_TRUSTED_SUPERAPPS, current).apply()
    }

    fun isSuperAppTrusted(context: Context, packageName: String): Boolean {
        val trusted = getTrustedSuperApps(context)
        return trusted.contains(packageName.lowercase().trim())
    }

    /**
     * Returns the set of user-whitelisted apps in the general installed apps audit.
     */
    fun getTrustedAuditApps(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return prefs.getStringSet(KEY_TRUSTED_AUDIT_APPS, emptySet())
            ?.map { it.lowercase().trim() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Marks an app as trusted by the user, synchronizing across all audit vectors
     * (SuperApp, Notification Listener, Accessibility Service, and Installed Apps Audit).
     */
    fun setAppTrusted(context: Context, packageName: String, isTrusted: Boolean) {
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_TRUSTED_AUDIT_APPS, emptySet())
            ?.toMutableSet() ?: mutableSetOf()
        val normalized = packageName.lowercase().trim()
        if (isTrusted) {
            current.add(normalized)
        } else {
            current.remove(normalized)
        }
        prefs.edit().putStringSet(KEY_TRUSTED_AUDIT_APPS, current).apply()

        // Also synchronize with SuperApp, Notification Listener and Accessibility trust
        setSuperAppTrusted(context, packageName, isTrusted)
        setNotificationListenerTrusted(context, packageName, isTrusted)
        setAccessibilityServiceTrusted(context, packageName, isTrusted)
    }

    /**
     * Checks if an app is trusted across any of the privacy audit vectors.
     */
    fun isAppTrusted(context: Context, packageName: String): Boolean {
        val norm = packageName.lowercase().trim()
        val auditTrusted = getTrustedAuditApps(context)
        return auditTrusted.contains(norm) ||
                isSuperAppTrusted(context, norm) ||
                isNotificationListenerTrusted(context, norm) ||
                isAccessibilityServiceTrusted(context, norm)
    }

    /**
     * Returns the union of all user-trusted package names across all vectors.
     */
    fun getAllTrustedApps(context: Context): Set<String> {
        val audit = getTrustedAuditApps(context)
        val superApps = getTrustedSuperApps(context)
        val listeners = getTrustedNotificationListeners(context)
        val a11y = getTrustedAccessibilityServices(context)
        return (audit + superApps + listeners + a11y)
    }

    /**
     * Clears all trusted apps.
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_TRUSTED_NOTIFICATION_LISTENERS)
            .remove(KEY_TRUSTED_ACCESSIBILITY_SERVICES)
            .remove(KEY_TRUSTED_SUPERAPPS)
            .remove(KEY_TRUSTED_AUDIT_APPS)
            .apply()
    }
}
