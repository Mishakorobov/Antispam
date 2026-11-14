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
import ru.antispam.app.privacy.AdIdManager
import ru.antispam.app.privacy.CertificateGuardManager
import ru.antispam.app.privacy.GeoTriangulationAuditor
import ru.antispam.app.privacy.NotificationListenerAuditor
import ru.antispam.app.privacy.PrivacyCategory
import ru.antispam.app.privacy.PrivacyRiskLevel
import ru.antispam.app.privacy.PrivacyScannerHelper
import ru.antispam.app.privacy.PrivateCallLogVaultManager
import ru.antispam.app.privacy.SecurityScoreEngine
import ru.antispam.app.privacy.SpeakerphoneMonitor
import ru.antispam.app.privacy.SuperAppInspector
import ru.antispam.app.privacy.TrustedAppManager
import ru.antispam.app.privacy.VpnClientFormat
import ru.antispam.app.privacy.VpnMaskingHelper
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.AdIdPrivacyProfile
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.MaskingTargetApp
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityIssueType
import ru.antispam.app.privacy.model.SecurityScoreReport
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.privacy.model.TriangulationRiskApp
import ru.antispam.app.security.AntiTamperHelper
import ru.antispam.app.privacy.model.TriangulationRiskLevel
import ru.antispam.app.privacy.model.UserInstalledCertificate
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
    val shreddedOtpCount: Int = 0,
    val accessibilityServices: List<AccessibilityServiceApp> = emptyList(),
    val criticalAccessibilityCount: Int = 0,
    val superAppProfiles: List<PackagePrivacyProfile> = emptyList(),
    val superAppCriticalCount: Int = 0,
    val superAppHighCount: Int = 0,
    val installedVpnClients: List<String> = emptyList(),
    val isVpnInventoryExposed: Boolean = false,
    val installedTargetApps: List<MaskingTargetApp> = emptyList(),
    val detectedVpnClient: Pair<VpnClientFormat, String>? = null,
    val selectedVpnFormat: VpnClientFormat = VpnClientFormat.AMNEZIA_VPN,
    val isVpnConfigCopied: Boolean = false,
    val copiedAppCount: Int = 0,
    val showVpnGuideDialog: Boolean = false,
    val triangulationApps: List<TriangulationRiskApp> = emptyList(),
    val triangulationCriticalCount: Int = 0,
    val triangulationHighCount: Int = 0,
    val userCertificates: List<UserInstalledCertificate> = emptyList(),
    val certMitmCriticalCount: Int = 0,
    val certWarningCount: Int = 0,
    val hasMitmThreat: Boolean = false,
    val adIdProfile: AdIdPrivacyProfile? = null,
    val securityScoreReport: SecurityScoreReport? = null
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
            isClipboardGuardEnabled = prefs.getBoolean(ClipboardGuardManager.KEY_CLIPBOARD_GUARD_ENABLED, true),
            shreddedOtpCount = ClipboardGuardManager.getShreddedOtpCount(application)
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
        // Phase 8.2 & 11.4: Observe 2FA ClipboardGuard status and shredded OTP counter
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
        viewModelScope.launch {
            ClipboardGuardManager.shreddedOtpCount.collect { count ->
                _uiState.update { it.copy(shreddedOtpCount = count) }
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

            // Phase 8.1 & 11.3: Audit apps with active NotificationListener access
            val trustedListeners = TrustedAppManager.getTrustedNotificationListeners(getApplication())
            val listeners = NotificationListenerAuditor.getEnabledNotificationListeners(getApplication(), trustedListeners)
            val highRiskListeners = listeners.count { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted }

            // Phase 8.3 & 11.3: Audit apps with active AccessibilityService access
            val trustedAccessibility = TrustedAppManager.getTrustedAccessibilityServices(getApplication())
            val accessibility = AccessibilityAuditor.getActiveAccessibilityServices(getApplication(), trustedAccessibility)
            val criticalAccess = accessibility.count { it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted }

            // Phase 9.1: Audit apps with QUERY_ALL_PACKAGES & inventory of VPN clients
            val superAppAudit = SuperAppInspector.auditSuperApps(getApplication())

            // Phase 9.2: Target Russian apps requiring Split-Tunneling and VPN detection
            val targetApps = VpnMaskingHelper.getInstalledTargetApps(getApplication())
            val detectedVpn = VpnMaskingHelper.detectInstalledVpnClient(getApplication())

            // Phase 9.3: Audit stealth geo-triangulation without GPS
            val triangulationAudit = GeoTriangulationAuditor.auditSummary(getApplication())

            // Phase 9.4: Audit user-installed Root CA certificates for MITM risks
            val certAudit = CertificateGuardManager.auditCertificates(getApplication())

            // Phase 9.5: Inspect Cross-App Advertising ID (GAID / OAID) status
            val adProfile = AdIdManager.getPrivacyProfile(getApplication())

            // Phase 10.1: Unified Security & Privacy Index Scoring (0–100%)
            val poolCount = try { db.blockedPoolDao().getCount() } catch (_: Exception) { 0 }
            val lastSync = try { db.syncMetadataDao().getValueLong("last_sync_timestamp") ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
            val isRegistryFresh = poolCount > 0 && (System.currentTimeMillis() - lastSync < 48 * 3600 * 1000L)

            // Phase 11.1: Environment integrity check (Soft Advisory for Root/Frida)
            val envReport = AntiTamperHelper.checkEnvironmentIntegrity(getApplication())

            val auditInput = PrivacyAuditInput(
                isRegistryUpToDate = isRegistryFresh,
                isMicGuardEnabled = _uiState.value.isMicGuardEnabled,
                hasNotificationSpies = highRiskListeners > 0,
                isClipboardGuardEnabled = _uiState.value.isClipboardGuardEnabled,
                hasMitmRootCa = certAudit.criticalCount > 0,
                hasCriticalAccessibility = criticalAccess > 0,
                hasVpnInventoryExposed = superAppAudit.isVpnInventoryExposed,
                hasStealthGeoTrackers = triangulationAudit.criticalCount > 0,
                hasRootOrHooks = envReport.hasPrivilegeOrHookExposure
            )
            val scoreReport = SecurityScoreEngine.calculateScore(auditInput)

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
                    isVpnInventoryExposed = superAppAudit.isVpnInventoryExposed,
                    installedTargetApps = targetApps,
                    detectedVpnClient = detectedVpn,
                    selectedVpnFormat = detectedVpn?.first ?: it.selectedVpnFormat,
                    triangulationApps = triangulationAudit.apps,
                    triangulationCriticalCount = triangulationAudit.criticalCount,
                    triangulationHighCount = triangulationAudit.highCount,
                    userCertificates = certAudit.userCertificates,
                    certMitmCriticalCount = certAudit.criticalCount,
                    certWarningCount = certAudit.warningCount,
                    hasMitmThreat = certAudit.hasMitmThreat,
                    adIdProfile = adProfile,
                    securityScoreReport = scoreReport
                )
            }
        }
    }

    fun resolveSecurityIssue(issue: SecurityIssue) {
        when (issue.type) {
            SecurityIssueType.REGISTRY_OUTDATED -> {
                refreshScan()
            }
            SecurityIssueType.MIC_GUARD_DISABLED -> {
                toggleMicGuard(true)
                refreshScan()
            }
            SecurityIssueType.NOTIFICATION_SPY_DETECTED -> {
                openNotificationListenerSettings()
            }
            SecurityIssueType.CLIPBOARD_GUARD_DISABLED -> {
                toggleClipboardGuard(true)
                refreshScan()
            }
            SecurityIssueType.MITM_ROOT_CA_DETECTED -> {
                openSecuritySettings()
            }
            SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED -> {
                openAccessibilitySettings()
            }
            SecurityIssueType.SUPERAPP_VPN_EXPOSED -> {
                copyVpnMaskingConfig(_uiState.value.selectedVpnFormat)
            }
            SecurityIssueType.STEALTH_GEO_DETECTED -> {
                openLocationSettings()
            }
            SecurityIssueType.ROOT_OR_HOOK_ENVIRONMENT -> {
                AntiTamperHelper.openRootManagerOrSecuritySettings(getApplication())
            }
        }
    }

    fun openNotificationListenerSettings() {
        NotificationListenerAuditor.openNotificationListenerSettings(getApplication())
    }

    fun openAccessibilitySettings() {
        AccessibilityAuditor.openAccessibilitySettings(getApplication())
    }

    fun openLocationSettings() {
        GeoTriangulationAuditor.openLocationSettings(getApplication())
    }

    fun openSecuritySettings() {
        CertificateGuardManager.openSecuritySettings(getApplication())
    }

    fun openAdvertisingSettings(): Boolean {
        return AdIdManager.openAdvertisingSettings(getApplication())
    }

    fun openAppDetails(packageName: String) {
        SuperAppInspector.openAppDetailsSettings(getApplication(), packageName)
    }

    fun selectVpnFormat(format: VpnClientFormat) {
        _uiState.update { it.copy(selectedVpnFormat = format, isVpnConfigCopied = false) }
    }

    fun copyVpnMaskingConfig(format: VpnClientFormat) {
        val count = VpnMaskingHelper.copyConfigToClipboard(getApplication(), format)
        _uiState.update {
            it.copy(
                isVpnConfigCopied = true,
                copiedAppCount = count,
                selectedVpnFormat = format,
                showVpnGuideDialog = true
            )
        }
    }

    fun showVpnGuide() {
        _uiState.update { it.copy(showVpnGuideDialog = true) }
    }

    fun dismissVpnGuideDialog() {
        _uiState.update { it.copy(showVpnGuideDialog = false) }
    }

    fun openVpnSettings() {
        VpnMaskingHelper.openVpnSettings(getApplication())
    }

    fun launchVpnClient(format: VpnClientFormat): Boolean {
        return VpnMaskingHelper.launchVpnClient(getApplication(), format)
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

    /**
     * Phase 11.4: Resets the shredded OTP counter.
     */
    fun resetShreddedOtpCounter() {
        ClipboardGuardManager.resetShreddedOtpCount(getApplication())
        _uiState.update { it.copy(shreddedOtpCount = 0) }
    }

    /**
     * Phase 11.3: Toggles trust status for a NotificationListener application.
     * Exempts user-approved smartwatch companions from security penalties, enabling 100% score.
     */
    fun toggleTrustNotificationListener(packageName: String) {
        val currentTrusted = TrustedAppManager.isNotificationListenerTrusted(getApplication(), packageName)
        TrustedAppManager.setNotificationListenerTrusted(getApplication(), packageName, !currentTrusted)

        val trustedSet = TrustedAppManager.getTrustedNotificationListeners(getApplication())
        val updatedListeners = _uiState.value.notificationListeners.map {
            if (it.packageName.equals(packageName, ignoreCase = true)) {
                it.copy(isTrusted = !currentTrusted)
            } else {
                it.copy(isTrusted = trustedSet.contains(it.packageName.lowercase()))
            }
        }
        val highRiskCount = updatedListeners.count { it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted }

        _uiState.update { state ->
            val hasRootAdvisory = state.securityScoreReport?.issues?.any { it.type == SecurityIssueType.ROOT_OR_HOOK_ENVIRONMENT } == true
            val isRegOk = (state.securityScoreReport?.categoryBreakdown?.get("Реестр Минцифры") ?: 0) > 0
            val updatedInput = PrivacyAuditInput(
                isRegistryUpToDate = isRegOk,
                isMicGuardEnabled = state.isMicGuardEnabled,
                hasNotificationSpies = highRiskCount > 0,
                isClipboardGuardEnabled = state.isClipboardGuardEnabled,
                hasMitmRootCa = state.hasMitmThreat,
                hasCriticalAccessibility = state.criticalAccessibilityCount > 0,
                hasVpnInventoryExposed = state.isVpnInventoryExposed,
                hasStealthGeoTrackers = state.triangulationCriticalCount > 0,
                hasRootOrHooks = hasRootAdvisory
            )
            state.copy(
                notificationListeners = updatedListeners,
                highRiskListenersCount = highRiskCount,
                securityScoreReport = SecurityScoreEngine.calculateScore(updatedInput)
            )
        }
    }

    /**
     * Phase 11.3: Toggles trust status for an Accessibility Service application.
     * Exempts user-approved password managers (Bitwarden, KeePassDX) from security penalties, enabling 100% score.
     */
    fun toggleTrustAccessibilityService(packageName: String) {
        val currentTrusted = TrustedAppManager.isAccessibilityServiceTrusted(getApplication(), packageName)
        TrustedAppManager.setAccessibilityServiceTrusted(getApplication(), packageName, !currentTrusted)

        val trustedSet = TrustedAppManager.getTrustedAccessibilityServices(getApplication())
        val updatedServices = _uiState.value.accessibilityServices.map {
            if (it.packageName.equals(packageName, ignoreCase = true)) {
                it.copy(isTrusted = !currentTrusted)
            } else {
                it.copy(isTrusted = trustedSet.contains(it.packageName.lowercase()))
            }
        }
        val criticalCount = updatedServices.count { it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted }

        _uiState.update { state ->
            val hasRootAdvisory = state.securityScoreReport?.issues?.any { it.type == SecurityIssueType.ROOT_OR_HOOK_ENVIRONMENT } == true
            val isRegOk = (state.securityScoreReport?.categoryBreakdown?.get("Реестр Минцифры") ?: 0) > 0
            val updatedInput = PrivacyAuditInput(
                isRegistryUpToDate = isRegOk,
                isMicGuardEnabled = state.isMicGuardEnabled,
                hasNotificationSpies = state.highRiskListenersCount > 0,
                isClipboardGuardEnabled = state.isClipboardGuardEnabled,
                hasMitmRootCa = state.hasMitmThreat,
                hasCriticalAccessibility = criticalCount > 0,
                hasVpnInventoryExposed = state.isVpnInventoryExposed,
                hasStealthGeoTrackers = state.triangulationCriticalCount > 0,
                hasRootOrHooks = hasRootAdvisory
            )
            state.copy(
                accessibilityServices = updatedServices,
                criticalAccessibilityCount = criticalCount,
                securityScoreReport = SecurityScoreEngine.calculateScore(updatedInput)
            )
        }
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
