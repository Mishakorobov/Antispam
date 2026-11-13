package ru.antispam.app.util

import android.content.Context

/**
 * KazakhstanBlockManager:
 * Manages the blocking toggle for foreign +7 telephone ranges (Kazakhstan 6xx, 7xx).
 *
 * Russia and Kazakhstan share the international country calling code +7.
 * Russian national numbers use codes 300..599 and 800..999.
 * When enabled, any unrecognized incoming call from Kazakhstan / +7 6xx/7xx is dropped immediately,
 * protecting against cross-border fraud while unconditionally passing saved contacts and whitelisted numbers.
 */
object KazakhstanBlockManager {

    private const val PREFS_NAME = "antispam_kazakhstan_prefs"
    private const val KEY_BLOCK_FOREIGN_PLUS7 = "block_foreign_plus7_active"

    fun isBlockForeignPlus7Active(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_FOREIGN_PLUS7, false)
    }

    fun setBlockForeignPlus7Active(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCK_FOREIGN_PLUS7, active)
            .apply()
    }
}
