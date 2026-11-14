package ru.antispam.app.privacy

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build

/**
 * FamilyLockManager: "Семейный замок / Защита родителей".
 *
 * Threat / UX Scenario:
 * Elderly family members often accidentally tap settings cards, turning off spam protection
 * or lowering paranoia filters, leaving them exposed to aggressive social engineering and phone fraud.
 *
 * Solution:
 * Locks down protection profiles and filter switches.
 * Any attempt to alter settings, downgrade protection, or disable the lock requires device credential
 * confirmation (Biometrics, Device PIN, Pattern, or Password) via Android KeyguardManager.
 */
object FamilyLockManager {

    const val PREFS_NAME = "antispam_settings"
    const val KEY_FAMILY_LOCK_ENABLED = "family_lock_enabled"

    internal var sharedPreferencesProvider: ((Context) -> SharedPreferences)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return sharedPreferencesProvider?.invoke(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if Family Lock is currently enabled.
     */
    fun isFamilyLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FAMILY_LOCK_ENABLED, false)
    }

    /**
     * Toggles Family Lock state.
     */
    fun setFamilyLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FAMILY_LOCK_ENABLED, enabled).apply()
    }

    /**
     * Checks whether the device has a secure lock screen (PIN, pattern, password, or biometrics).
     */
    fun isDeviceSecure(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceSecure
        } else {
            km.isKeyguardSecure
        }
    }

    /**
     * Creates system authentication Intent (PIN, Pattern, Password, Biometrics)
     * using KeyguardManager.createConfirmDeviceCredentialIntent.
     */
    @Suppress("DEPRECATION")
    fun createConfirmDeviceCredentialIntent(
        context: Context,
        title: String = "Семейный замок: подтвердите личность",
        description: String = "Для изменения настроек защиты введите PIN-код устройства или приложите палец"
    ): Intent? {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        return km.createConfirmDeviceCredentialIntent(title, description)
    }
}
