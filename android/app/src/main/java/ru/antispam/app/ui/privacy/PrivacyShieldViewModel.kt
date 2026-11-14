package ru.antispam.app.ui.privacy

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.local.entity.VaultCallRecordEntity
import ru.antispam.app.privacy.AppPrivacyRisk
import ru.antispam.app.privacy.MicGuardService
import ru.antispam.app.privacy.MicJammerManager
import ru.antispam.app.privacy.AccessibilityAuditor
import ru.antispam.app.privacy.ClipboardGuardManager
import ru.antispam.app.privacy.NotificationListenerAuditor
import ru.antispam.app.privacy.PrivacyCategory
import ru.antispam.app.privacy.PrivacyRiskLevel
import ru.antispam.app.privacy.PrivacyScannerHelper
import ru.antispam.app.privacy.PrivateCallLogVaultManager
import ru.antispam.app.privacy.SpeakerphoneMonitor
import ru.antispam.app.privacy.SuperAppInspector
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.util.VipModeManager

data class PrivacyShieldUiState(
    val isMicGuardEnabled: Boolean = true,
    val isMonitoringActive: Boolean = false,
    val lastAlertMessage: String? = null,
    val scannedApps: List<AppPrivacyRisk> = emptyList(),
    val isScanning: Boolean = false,
    val criticalAppsCount: Int = 0,
    val highAppsCount: Int = 0,
    val superappsCount: Int = 0,
    val messengersCount: Int = 0,
    val systemAppsCount: Int = 0,
    val isJammerActive: Boolean = false,
    val isJammerAutoEnabled: Boolean = true,
    val isSpeakerphoneOn: Boolean = false,
    val speakerphoneWarning: String? = null,
    val isVaultEnabled: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val vaultCalls: List<VaultCallRecordEntity> = emptyList(),
    val isVipModeActive: Boolean = false,
    val isBlockForeignPlus7Active: Boolean = false,
    val pendingRevokePkg: String? = null,
    val pendingRevokeAppName: String? = null,
    val revocationSuccessMessage: String? = null,
    val notificationListeners: List<NotificationListenerApp> = emptyList(),
    val highRiskListenersCount: Int = 0,
    val isClipboardGuardEnabled: Boolean = true,
    val isClipboardGuarding: Boolean = false,
    val clipboardShreddedAlert: String? = null,
    val accessibilityServices: List<AccessibilityServiceApp> = emptyList(),
    val criticalAccessibilityCount: Int = 0,
    val superAppProfiles: List<PackagePrivacyProfile> = emptyList(),
    val superAppCriticalCount: Int = 0,
    val superAppHighCount: Int = 0,
    val installedVpnClients: List<String> = emptyList(),
    val isVpnInventoryExposed: Boolean = false
)

class PrivacyShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("antispam_privacy_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(
        PrivacyShieldUiState(
            isMicGuardEnabled = prefs.getBoolean(KEY_MIC_GUARD_ENABLED, true),
            isJammerAutoEnabled = prefs.getBoolean(KEY_JAMMER_AUTO_ENABLED, true),
            isVaultEnabled = PrivateCallLogVaultManager.isVaultEnabled(application),
            isVipModeActive = VipModeManager.isVipModeActive(application),
            isBlockForeignPlus7Active = ru.antispam.app.util.KazakhstanBlockManager.isBlockForeignPlus7Active(application),
            isClipboardGuardEnabled = prefs.getBoolean(ClipboardGuardManager.KEY_CLIPBOARD_GUARD_ENABLED, true)
        )
    )
    val uiState: StateFlow<PrivacyShieldUiState> = _uiState.asStateFlow()

    init {
        // Observe live monitoring status and alerts from MicGuardService
        viewModelScope.launch {
            MicGuardService.isMonitoring.collect { monitoring ->
                _uiState.update { it.copy(isMonitoringActive = monitoring) }
            }
        }
        viewModelScope.launch {
            MicGuardService.lastAlert.collect { alert ->
                _uiState.update { it.copy(lastAlertMessage = alert) }
            }
        }
        // Observe MicJammer active status
        viewModelScope.launch {
            MicJammerManager.isJamming.collect { jamming ->
                _uiState.update { it.copy(isJammerActive = jamming) }
            }
        }
        // Observe Speakerphone monitor status
        viewModelScope.launch {
            SpeakerphoneMonitor.isSpeakerphoneOn.collect { speakerOn ->
                _uiState.update { it.copy(isSpeakerphoneOn = speakerOn) }
            }
        }
        viewModelScope.launch {
            SpeakerphoneMonitor.warningMessage.collect { warning ->
                _uiState.update { it.copy(speakerphoneWarning = warning) }
            }
        }
        // Observe private call vault entries
        viewModelScope.launch {
            db.privateCallLogDao().getAllRecordsFlow().collect { records ->
                _uiState.update { it.copy(vaultCalls = records) }
            }
        }
        // Phase 8.2: Observe 2FA ClipboardGuard status and shred events
        viewModelScope.launch {
            ClipboardGuardManager.isGuarding.collect { guarding ->
                _uiState.update { it.copy(isClipboardGuarding = guarding) }
            }
        }
        viewModelScope.launch {
            ClipboardGuardManager.lastShreddedEvent.collect { event ->
                _uiState.update { it.copy(clipboardShreddedAlert = event) }
            }
        }
        refreshScan()
    }

    fun refreshScan() {
        _uiState.update { it.copy(isScanning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val apps = PrivacyScannerHelper.scanInstalledApps(getApplication())
            val criticalCount = apps.count { it.riskLevel == PrivacyRiskLevel.CRITICAL }
            val highCount = apps.count { it.riskLevel == PrivacyRiskLevel.HIGH }
            val superapps = apps.count { it.category == PrivacyCategory.SUPERAPP_SURVEILLANCE }
            val messengers = apps.count { it.category == PrivacyCategory.TRUSTED_MESSENGER }
            val systems = apps.count { it.category == PrivacyCategory.SYSTEM_DIALER }

            // Phase 8.1: Audit apps with active NotificationListener access
            val listeners = NotificationListenerAuditor.getEnabledNotificationListeners(getApplication())
            val highRiskListeners = listeners.count { it.riskLevel == ListenerRiskLevel.HIGH }

            // Phase 8.3: Audit apps with active AccessibilityService access
            val accessibility = AccessibilityAuditor.getActiveAccessibilityServices(getApplication())
            val criticalAccess = accessibility.count { it.riskLevel == AccessibilityRiskLevel.CRITICAL }

            // Phase 9.1: Audit apps with QUERY_ALL_PACKAGES & inventory of VPN clients
            val superAppAudit = SuperAppInspector.auditSuperApps(getApplication())

            _uiState.update {
                it.copy(
                    scannedApps = apps,
                    isScanning = false,
                    criticalAppsCount = criticalCount,
                    highAppsCount = highCount,
                    superappsCount = superapps,
                    messengersCount = messengers,
                    systemAppsCount = systems,
                    notificationListeners = listeners,
                    highRiskListenersCount = highRiskListeners,
                    accessibilityServices = accessibility,
                    criticalAccessibilityCount = criticalAccess,
                    superAppProfiles = superAppAudit.appsWithQueryAll,
                    superAppCriticalCount = superAppAudit.criticalCount,
                    superAppHighCount = superAppAudit.highCount,
                    installedVpnClients = superAppAudit.installedVpnClients,
                    isVpnInventoryExposed = superAppAudit.isVpnInventoryExposed
                )
            }
        }
    }

    fun openNotificationListenerSettings() {
        NotificationListenerAuditor.openNotificationListenerSettings(getApplication())
    }

    fun openAccessibilitySettings() {
        AccessibilityAuditor.openAccessibilitySettings(getApplication())
    }

    fun openAppDetails(packageName: String) {
        SuperAppInspector.openAppDetailsSettings(getApplication(), packageName)
    }

    fun toggleMicGuard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIC_GUARD_ENABLED, enabled).apply()
        _uiState.update { it.copy(isMicGuardEnabled = enabled) }
    }

    fun toggleJammerAutoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_JAMMER_AUTO_ENABLED, enabled).apply()
        _uiState.update { it.copy(isJammerAutoEnabled = enabled) }
    }

    fun toggleClipboardGuard(enabled: Boolean) {
        prefs.edit().putBoolean(ClipboardGuardManager.KEY_CLIPBOARD_GUARD_ENABLED, enabled).apply()
        _uiState.update { it.copy(isClipboardGuardEnabled = enabled) }
        ClipboardGuardManager.updateStateFromPreferences(getApplication())
    }

    fun clearClipboardAlert() {
        ClipboardGuardManager.clearAlert()
        _uiState.update { it.copy(clipboardShreddedAlert = null) }
    }

    fun toggleCallVault(enabled: Boolean) {
        PrivateCallLogVaultManager.setVaultEnabled(getApplication(), enabled)
        _uiState.update { it.copy(isVaultEnabled = enabled) }
    }

    fun unlockVault() {
        _uiState.update { it.copy(isVaultUnlocked = true) }
    }

    fun lockVault() {
        _uiState.update { it.copy(isVaultUnlocked = false) }
    }

    fun toggleVipMode(enabled: Boolean) {
        VipModeManager.setVipModeActive(getApplication(), enabled)
        _uiState.update { it.copy(isVipModeActive = enabled) }
    }

    fun toggleBlockForeignPlus7(enabled: Boolean) {
        ru.antispam.app.util.KazakhstanBlockManager.setBlockForeignPlus7Active(getApplication(), enabled)
        _uiState.update { it.copy(isBlockForeignPlus7Active = enabled) }
    }

    fun clearVault() {
        viewModelScope.launch(Dispatchers.IO) {
            db.privateCallLogDao().clearAll()
        }
    }

    fun clearAlert() {
        _uiState.update { it.copy(lastAlertMessage = null) }
    }

    fun clearRevocationBanner() {
        _uiState.update { it.copy(revocationSuccessMessage = null) }
    }

    fun preparePermissionRevocation(appName: String, packageName: String) {
        _uiState.update {
            it.copy(
                pendingRevokeAppName = appName,
                pendingRevokePkg = packageName
            )
        }
        PrivacyScannerHelper.openAppSettings(getApplication(), packageName)
    }

    /**
     * Called on Activity ON_RESUME (Magnetic Onboarding):
     * Checks if the user revoked READ_PHONE_STATE in system settings.
     */
    fun checkPendingRevocationOnResume() {
        val pkg = _uiState.value.pendingRevokePkg ?: return
        val appName = _uiState.value.pendingRevokeAppName ?: pkg

        viewModelScope.launch(Dispatchers.IO) {
            val hasPhoneState = PrivacyScannerHelper.isPermissionGranted(
                getApplication(),
                pkg,
                "android.permission.READ_PHONE_STATE"
            )

            if (!hasPhoneState) {
                // Success: permission was revoked!
                _uiState.update {
                    it.copy(
                        pendingRevokePkg = null,
                        pendingRevokeAppName = null,
                        revocationSuccessMessage = "Отлично! Доступ к звонкам для $appName успешно ограничен."
                    )
                }
            } else {
                _uiState.update { it.copy(pendingRevokePkg = null, pendingRevokeAppName = null) }
            }
            refreshScan()
        }
    }

    companion object {
        private const val KEY_MIC_GUARD_ENABLED = "mic_guard_enabled"
        private const val KEY_JAMMER_AUTO_ENABLED = "mic_jammer_auto_enabled"
    }
}
