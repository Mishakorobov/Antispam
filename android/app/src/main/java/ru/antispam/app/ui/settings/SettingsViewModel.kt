package ru.antispam.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.privacy.FamilyLockManager
import ru.antispam.app.privacy.ProtectionPresetManager
import ru.antispam.app.privacy.model.ProtectionPreset
import ru.antispam.app.util.OemOnboardingHelper
import ru.antispam.app.util.OemVendor
import ru.antispam.app.worker.SyncPoolsWorker

data class SettingsUiState(
    val blockSurveys: Boolean = true,
    val blockFinancial: Boolean = true,
    val blockSilentCalls: Boolean = true,
    val postCallEnabled: Boolean = true,
    val postCallThresholdSeconds: Int = 30, // < 30 seconds
    val pendingReportsCount: Int = 0,
    val currentDatabaseVersion: Long = 0L,
    val isSyncing: Boolean = false,
    val activePreset: ProtectionPreset = ProtectionPreset.ANTI_EAVESDROPPING,
    val oemVendor: OemVendor = OemVendor.GENERIC_AOSP,
    val isBatteryOptimizationIgnored: Boolean = true,
    val showOemBanner: Boolean = false,
    val isFamilyLockEnabled: Boolean = false,
    val isDeviceSecure: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val prefs = application.getSharedPreferences("antispam_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val pendingCount = db.pendingReportDao().getCount()
            val ver = db.syncMetadataDao().getValueLong("last_delta_version") ?: 0L
            val activePreset = ProtectionPresetManager.getActivePreset(getApplication())

            val vendor = OemOnboardingHelper.detectOem()
            val isIgnored = OemOnboardingHelper.isIgnoringBatteryOptimizations(getApplication())
            val isBannerDismissed = prefs.getBoolean("oem_banner_dismissed", false)
            val shouldShowBanner = !isBannerDismissed && (vendor.isAggressiveKiller || !isIgnored)
            val isFamilyLock = FamilyLockManager.isFamilyLockEnabled(getApplication())
            val isSecure = FamilyLockManager.isDeviceSecure(getApplication())

            _uiState.update {
                it.copy(
                    blockSurveys = prefs.getBoolean("block_surveys", true),
                    blockFinancial = prefs.getBoolean("block_financial", true),
                    blockSilentCalls = prefs.getBoolean("block_silent", true),
                    postCallEnabled = prefs.getBoolean("post_call_enabled", true),
                    postCallThresholdSeconds = prefs.getInt("post_call_threshold", 30),
                    pendingReportsCount = pendingCount,
                    currentDatabaseVersion = ver,
                    isSyncing = false,
                    activePreset = activePreset,
                    oemVendor = vendor,
                    isBatteryOptimizationIgnored = isIgnored,
                    showOemBanner = shouldShowBanner,
                    isFamilyLockEnabled = isFamilyLock,
                    isDeviceSecure = isSecure
                )
            }
        }
    }

    /**
     * Phase 11.5: Toggles Family Lock ("Защита родителей").
     */
    fun toggleFamilyLock(enabled: Boolean) {
        FamilyLockManager.setFamilyLockEnabled(getApplication(), enabled)
        loadSettings()
    }

    /**
     * Phase 10.2: 1-Click Protection Presets switcher.
     * Applies the chosen paranoia profile across settings and privacy shields in a single transaction.
     */
    fun applyProtectionPreset(preset: ProtectionPreset) {
        ProtectionPresetManager.applyPreset(getApplication(), preset)
        loadSettings()
    }

    /**
     * Phase 10.3: Opens the OEM-specific autostart or battery management screen.
     */
    fun openOemAutostartSettings() {
        OemOnboardingHelper.openAutostartSettings(getApplication())
        loadSettings()
    }

    /**
     * Phase 10.3: User can dismiss the OEM banner once configured or acknowledged.
     */
    fun dismissOemBanner() {
        prefs.edit().putBoolean("oem_banner_dismissed", true).apply()
        _uiState.update { it.copy(showOemBanner = false) }
    }

    fun setSetting(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        loadSettings()
    }

    fun triggerSyncNow() {
        _uiState.update { it.copy(isSyncing = true) }
        val workRequest = OneTimeWorkRequestBuilder<SyncPoolsWorker>().build()
        WorkManager.getInstance(getApplication()).enqueue(workRequest)
        // Refresh after short delay
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            loadSettings()
        }
    }
}
