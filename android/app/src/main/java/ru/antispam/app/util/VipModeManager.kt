package ru.antispam.app.util

import android.content.Context

/**
 * VipModeManager: "Only Contacts" / "Night Dome" VIP profile.
 * When enabled, silences incoming calls from numbers not present in the user's contacts book,
 * while guaranteeing unconditional pass-through for emergency services (112) and whitelisted banks.
 */
object VipModeManager {

    private const val PREFS_NAME = "antispam_vip_prefs"
    private const val KEY_VIP_ACTIVE = "vip_only_contacts_active"

    fun isVipModeActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIP_ACTIVE, false)
    }

    fun setVipModeActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIP_ACTIVE, active)
            .apply()
    }
}
