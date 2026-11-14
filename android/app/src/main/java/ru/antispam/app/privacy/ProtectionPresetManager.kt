package ru.antispam.app.privacy

import android.content.Context
import android.util.Log
import ru.antispam.app.privacy.model.ProtectionPreset
import ru.antispam.app.util.DeliveryModeManager
import ru.antispam.app.util.KazakhstanBlockManager

/**
 * ProtectionPresetManager: Orchestrator for 1-click paranoid protection presets.
 * Applies configuration in atomic SharedPreferences transactions across settings and privacy scopes.
 */
object ProtectionPresetManager {

    private const val TAG = "ProtectionPresetManager"

    const val PREFS_SETTINGS = "antispam_settings"
    const val PREFS_PRIVACY = "antispam_privacy_prefs"

    const val KEY_ACTIVE_PRESET = "active_protection_preset"
    const val KEY_BLOCK_SURVEYS = "block_surveys"
    const val KEY_BLOCK_FINANCIAL = "block_financial"
    const val KEY_BLOCK_SILENT = "block_silent"
    const val KEY_POST_CALL_ENABLED = "post_call_enabled"

    const val KEY_MIC_GUARD_ENABLED = "mic_guard_enabled"
    const val KEY_JAMMER_AUTO_ENABLED = "mic_jammer_auto_enabled"
    const val KEY_VAULT_ENABLED = "call_incognito_vault_enabled"

    fun getActivePreset(context: Context): ProtectionPreset {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_ACTIVE_PRESET, null)
        return ProtectionPreset.fromString(name)
    }

    /**
     * Applies the chosen preset in unified, coordinated SharedPreferences transactions
     * and synchronizes associated background guardians.
     */
    fun applyPreset(context: Context, preset: ProtectionPreset) {
        val config = preset.getConfiguration()
        val settingsPrefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val privacyPrefs = context.getSharedPreferences(PREFS_PRIVACY, Context.MODE_PRIVATE)

        // 1. SharedPreferences: antispam_settings transaction
        settingsPrefs.edit()
            .putString(KEY_ACTIVE_PRESET, preset.name)
            .putBoolean(KEY_BLOCK_SURVEYS, config.blockSurveys)
            .putBoolean(KEY_BLOCK_FINANCIAL, config.blockFinancial)
            .putBoolean(KEY_BLOCK_SILENT, config.blockSilentCalls)
            .putBoolean(KEY_POST_CALL_ENABLED, config.postCallEnabled)
            .apply()

        // 2. SharedPreferences: antispam_privacy_prefs transaction
        privacyPrefs.edit()
            .putBoolean(KEY_MIC_GUARD_ENABLED, config.micGuardEnabled)
            .putBoolean(KEY_JAMMER_AUTO_ENABLED, config.jammerAutoEnabled)
            .putBoolean(ClipboardGuardManager.KEY_CLIPBOARD_GUARD_ENABLED, config.clipboardGuardEnabled)
            .putBoolean(KEY_VAULT_ENABLED, config.callVaultEnabled)
            .apply()

        // 3. Coordinate background services safely
        try {
            DeliveryModeManager.setDeliveryMode(context, config.deliveryModeActive, durationHours = 2)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating delivery mode: ${e.message}")
        }

        try {
            KazakhstanBlockManager.setBlockForeignPlus7Active(context, config.blockForeignPlus7Active)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating Kazakhstan filter: ${e.message}")
        }

        try {
            ClipboardGuardManager.updateStateFromPreferences(context)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating clipboard guard: ${e.message}")
        }

        if (!config.micGuardEnabled) {
            try {
                MicGuardService.stopGuard(context)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping mic guard: ${e.message}")
            }
        }

        Log.i(TAG, "Applied protection preset: ${preset.name}")
    }
}
