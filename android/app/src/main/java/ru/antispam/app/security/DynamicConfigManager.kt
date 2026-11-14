package ru.antispam.app.security

import android.util.Log
import kotlinx.serialization.json.Json
import ru.antispam.app.data.local.AppDatabase

/**
 * DynamicConfigManager:
 * Thread-safe singleton managing dynamic flags and security rules delivered via
 * cryptographically signed ASPB v2 snapshots.
 */
object DynamicConfigManager {

    private const val TAG = "DynamicConfigManager"
    const val KEY_DYNAMIC_CONFIG_JSON = "dynamic_config_json"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activeConfig: DynamicConfig = DynamicConfig()

    /**
     * Applies a newly received and verified dynamic configuration, persisting it to Room metadata.
     */
    fun applyConfig(config: DynamicConfig?, db: AppDatabase? = null) {
        if (config == null) return
        activeConfig = config
        Log.i(TAG, "Applied dynamic config: ${config.newSpywarePackages.size} spyware pkgs, ${config.dynamicCdnMirrors.size} CDN mirrors, ${config.antifraudSmsKeywords.size} SMS keywords, ${config.foreignSpoofingPrefixes.size} spoofing prefixes")

        db?.let {
            try {
                val jsonStr = json.encodeToString(DynamicConfig.serializer(), config)
                it.syncMetadataDao().setValueStr(KEY_DYNAMIC_CONFIG_JSON, jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist dynamic config JSON: ${e.message}")
            }
        }
    }

    /**
     * Loads the stored dynamic config from the local database on app initialization.
     */
    fun loadFromDatabase(db: AppDatabase) {
        val jsonStr = try {
            db.syncMetadataDao().getValueStr(KEY_DYNAMIC_CONFIG_JSON)
        } catch (e: Exception) {
            null
        } ?: return

        try {
            activeConfig = json.decodeFromString<DynamicConfig>(jsonStr)
            Log.d(TAG, "Loaded dynamic config from database: $activeConfig")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse stored dynamic config JSON: ${e.message}")
        }
    }

    fun getActiveConfig(): DynamicConfig = activeConfig

    fun getDynamicCdnMirrors(): List<String> = activeConfig.dynamicCdnMirrors

    fun getDynamicSpywarePackages(): List<String> = activeConfig.newSpywarePackages

    fun getDynamicAntifraudKeywords(): List<String> = activeConfig.antifraudSmsKeywords

    fun getDynamicSpoofingPrefixes(): List<String> = activeConfig.foreignSpoofingPrefixes

    fun reset() {
        activeConfig = DynamicConfig()
    }
}
