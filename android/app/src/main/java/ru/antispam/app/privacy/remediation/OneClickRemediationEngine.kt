package ru.antispam.app.privacy.remediation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.antispam.app.data.local.AppDatabase
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.privacy.AccessibilityAuditor
import ru.antispam.app.privacy.AppDormancyHelper
import ru.antispam.app.privacy.CallForwardingAuditor
import ru.antispam.app.privacy.CellularSecurityAuditor
import ru.antispam.app.privacy.CertificateGuardManager
import ru.antispam.app.privacy.ClipboardGuardManager
import ru.antispam.app.privacy.GeoTriangulationAuditor
import ru.antispam.app.privacy.MicGuardService
import ru.antispam.app.privacy.NotificationListenerAuditor
import ru.antispam.app.privacy.SuperAppInspector
import ru.antispam.app.privacy.TrustedAppManager
import ru.antispam.app.privacy.VpnMaskingHelper
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.privacy.model.TriangulationRiskLevel
import ru.antispam.app.ui.privacy.PrivacyShieldUiState
import ru.antispam.app.worker.SyncPoolsWorker

/**
 * OneClickRemediationEngine: Orchestrator responsible for planning, executing,
 * and selectively verifying the 1-Click security remediation sequence.
 */
object OneClickRemediationEngine {

    private const val TAG = "OneClickRemediation"

    /**
     * Builds a structured remediation plan by partitioning active security issues
     * into automated zero-interaction in-app tasks (Tier 1) and guided wizard steps (Tier 2).
     */
    fun planRemediation(
        context: Context,
        issues: List<SecurityIssue>,
        state: PrivacyShieldUiState
    ): RemediationPlan {
        val automated = mutableListOf<RemediationStep>()
        val guided = mutableListOf<RemediationStep>()
        val isRoot = PrivilegedRemediationExecutor.isRootAvailable()

        var currentScore = state.securityScoreReport?.score ?: 0
        var scoreGain = 0

        for (issue in issues) {
            when (issue.type) {
                SecurityIssueType.REGISTRY_OUTDATED -> {
                    automated.add(
                        RemediationStep(
                            id = "auto_registry_sync",
                            issueType = issue.type,
                            actionType = RemediationActionType.INTERNAL_SYNC_REGISTRY,
                            title = "Синхронизация спам-базы",
                            description = "Загрузка актуального реестра номеров Минцифры",
                            instruction = "Автоматическое обновление дельта-базы номеров",
                            actionLabel = "Обновить базу",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = true
                        )
                    )
                    scoreGain += issue.scoreDeduction
                }

                SecurityIssueType.MIC_GUARD_DISABLED -> {
                    automated.add(
                        RemediationStep(
                            id = "auto_mic_guard",
                            issueType = issue.type,
                            actionType = RemediationActionType.INTERNAL_ENABLE_MIC_GUARD,
                            title = "Защита микрофона",
                            description = "Активация фонового инспектора аудиопотока",
                            instruction = "Включение сервиса защиты от скрытой записи разговоров",
                            actionLabel = "Включить защиту",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = true
                        )
                    )
                    scoreGain += issue.scoreDeduction
                }

                SecurityIssueType.CLIPBOARD_GUARD_DISABLED -> {
                    automated.add(
                        RemediationStep(
                            id = "auto_clipboard_guard",
                            issueType = issue.type,
                            actionType = RemediationActionType.INTERNAL_ENABLE_CLIPBOARD_GUARD,
                            title = "Шредер буфера 2FA",
                            description = "Авто-очистка паролей и СМС-кодов через 30 секунд",
                            instruction = "Включение безопасной изоляции буфера обмена",
                            actionLabel = "Включить шредер",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = true
                        )
                    )
                    scoreGain += issue.scoreDeduction
                }

                SecurityIssueType.SUPERAPP_VPN_EXPOSED -> {
                    // Auto-fix by generating bypass config and copying to clipboard
                    automated.add(
                        RemediationStep(
                            id = "auto_vpn_masking",
                            issueType = issue.type,
                            actionType = RemediationActionType.INTERNAL_COPY_VPN_CONFIG,
                            title = "Маскировка от сканирования VPN",
                            description = "Генерация правил прямого обхода (Split-Tunneling)",
                            instruction = "Копирование списка российских сервисов в буфер",
                            actionLabel = "Изолировать VPN",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = true
                        )
                    )
                    scoreGain += issue.scoreDeduction
                }

                SecurityIssueType.NOTIFICATION_SPY_DETECTED -> {
                    // Identify non-system, non-trusted, non-sleeping rogue listeners
                    val rogueListeners = state.notificationListeners.filter {
                        it.riskLevel == ListenerRiskLevel.HIGH && !it.isTrusted && !it.isDeepSleeping
                    }

                    if (isRoot) {
                        // Privileged root path: can be silently revoked in background
                        for (listener in rogueListeners) {
                            automated.add(
                                RemediationStep(
                                    id = "root_listener_${listener.packageName}",
                                    issueType = issue.type,
                                    actionType = RemediationActionType.SYSTEM_REVOKE_NOTIFICATION_LISTENER,
                                    title = "Отзыв шпиона шторки (Root)",
                                    description = "Тихое отключение доступа к уведомлениям для ${listener.appName}",
                                    instruction = "Фоновый отзыв прав через Root Shell",
                                    actionLabel = "Отозвать тихо",
                                    severity = issue.severity,
                                    scoreImpact = issue.scoreDeduction,
                                    isAutoFixable = true,
                                    targetPackage = listener.packageName,
                                    targetAppName = listener.appName
                                )
                            )
                        }
                        scoreGain += issue.scoreDeduction
                    } else {
                        // Standard guided wizard path
                        for (listener in rogueListeners) {
                            guided.add(
                                RemediationStep(
                                    id = "guided_listener_${listener.packageName}",
                                    issueType = issue.type,
                                    actionType = RemediationActionType.SYSTEM_REVOKE_NOTIFICATION_LISTENER,
                                    title = "Шпион шторки: ${listener.appName}",
                                    description = "Приложение может читать ваши СМС-коды банков и 2FA",
                                    instruction = "В открывшемся окне One UI найдите '${listener.appName}' и выключите тумблер доступа к уведомлениям.",
                                    actionLabel = "Перейти и выключить",
                                    severity = issue.severity,
                                    scoreImpact = issue.scoreDeduction,
                                    isAutoFixable = false,
                                    targetPackage = listener.packageName,
                                    targetAppName = listener.appName,
                                    intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            )
                        }
                    }
                }

                SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED -> {
                    val rogueServices = state.accessibilityServices.filter {
                        it.riskLevel == AccessibilityRiskLevel.CRITICAL && !it.isTrusted && !it.isDeepSleeping
                    }

                    for (service in rogueServices) {
                        val isPasswordManager = TrustedAppManager.KNOWN_PASSWORD_MANAGERS.contains(service.packageName.lowercase())
                        guided.add(
                            RemediationStep(
                                id = "guided_accessibility_${service.packageName}",
                                issueType = issue.type,
                                actionType = RemediationActionType.SYSTEM_MANAGE_ACCESSIBILITY,
                                title = "Спецвозможности: ${service.appName}",
                                description = "Служба может перехватывать нажатия клавиш и считывать экран",
                                instruction = if (isPasswordManager) {
                                    "Это известный менеджер паролей. Нажмите 'Доверять', чтобы исключить его из штрафов, или выключите в настройках."
                                } else {
                                    "В открывшемся меню Спецвозможностей выключите службу '${service.appName}'."
                                },
                                actionLabel = "Перейти в настройки",
                                severity = issue.severity,
                                scoreImpact = issue.scoreDeduction,
                                isAutoFixable = false,
                                targetPackage = service.packageName,
                                targetAppName = service.appName,
                                canBeWhitelisted = isPasswordManager,
                                intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        )
                    }
                }

                SecurityIssueType.CALL_FORWARDING_ACTIVE -> {
                    guided.add(
                        RemediationStep(
                            id = "guided_call_forwarding",
                            issueType = issue.type,
                            actionType = RemediationActionType.SYSTEM_RESET_CALL_FORWARDING,
                            title = "Сброс переадресации звонков",
                            description = "Активна безусловная переадресация (*21*). Ваши вызовы и 2FA-звонки могут дублироваться.",
                            instruction = "Нажмите кнопку вызова в открывшемся телефоне с кодом ##002# для гарантированного сброса переадресации у оператора.",
                            actionLabel = "Сбросить (##002#)",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = false,
                            intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:##002%23")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    )
                }

                SecurityIssueType.CELLULAR_2G_DOWNGRADE_ACTIVE -> {
                    guided.add(
                        RemediationStep(
                            id = "guided_cellular_2g",
                            issueType = issue.type,
                            actionType = RemediationActionType.SYSTEM_DISABLE_2G,
                            title = "Отключение 2G радиоканала",
                            description = "Сеть 2G уязвима к перехвату ложными базовыми станциями (IMSI-Catcher)",
                            instruction = "В параметрах сети выберите режим 'LTE/3G' или отключите тумблер 'Разрешить 2G' для защиты от радио-шпионажа.",
                            actionLabel = "Настройки сети",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = false,
                            intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    )
                }

                SecurityIssueType.MITM_ROOT_CA_DETECTED -> {
                    guided.add(
                        RemediationStep(
                            id = "guided_mitm_ca",
                            issueType = issue.type,
                            actionType = RemediationActionType.SYSTEM_REMOVE_ROOT_CA,
                            title = "Удаление пользовательского Root CA",
                            description = "Пользовательский сертификат позволяет расшифровывать банковский HTTPS-трафик",
                            instruction = "В разделе 'Сертификаты пользователя' удалите подозрительный сторонний Root-сертификат.",
                            actionLabel = "Управление CA",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = false,
                            intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    )
                }

                SecurityIssueType.STEALTH_GEO_DETECTED -> {
                    val trackers = state.triangulationApps.filter {
                        it.riskLevel == TriangulationRiskLevel.CRITICAL && !it.isDeepSleeping
                    }
                    val targetPkg = trackers.firstOrNull()?.packageName

                    guided.add(
                        RemediationStep(
                            id = "guided_stealth_geo",
                            issueType = issue.type,
                            actionType = RemediationActionType.SYSTEM_RESTRICT_LOCATION,
                            title = "Ограничение скрытой триангуляции",
                            description = "Приложения собирают координаты через Wi-Fi BSSID и вышки связи без включенного GPS",
                            instruction = "Отзовите доступ к местоположению у подозрительных программ или отключите сканирование сетей Wi-Fi в настройках геолокации.",
                            actionLabel = "Настроить геопозицию",
                            severity = issue.severity,
                            scoreImpact = issue.scoreDeduction,
                            isAutoFixable = false,
                            targetPackage = targetPkg,
                            intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    )
                }

                SecurityIssueType.ROOT_OR_HOOK_ENVIRONMENT -> {
                    // Advisory warning: 0 points impact
                }
            }
        }

        // Sort guided steps by severity: CRITICAL first, then HIGH, then MEDIUM
        val sortedGuided = guided.sortedWith(
            compareBy<RemediationStep> { it.severity.ordinal }
                .thenByDescending { it.scoreImpact }
        )

        val projected = (currentScore + scoreGain).coerceIn(0, 100)

        return RemediationPlan(
            totalIssuesCount = issues.size,
            automatedSteps = automated,
            guidedSteps = sortedGuided,
            projectedScore = projected
        )
    }

    /**
     * Executes all automated in-app tasks concurrently or sequentially with progress reporting.
     */
    suspend fun executeBatchAutoFix(
        context: Context,
        automatedSteps: List<RemediationStep>,
        db: AppDatabase,
        onProgress: (stepTitle: String, completed: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val total = automatedSteps.size
        var completed = 0

        for (step in automatedSteps) {
            onProgress(step.title, completed, total)
            try {
                when (step.actionType) {
                    RemediationActionType.INTERNAL_SYNC_REGISTRY -> {
                        val repository = SyncRepository(db = db, context = context)
                        val deviceHash = SyncPoolsWorker.getOrCreateDeviceHash(context)
                        repository.synchronize(deviceHash)
                    }

                    RemediationActionType.INTERNAL_ENABLE_MIC_GUARD -> {
                        val prefs = context.getSharedPreferences("antispam_privacy_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("mic_guard_enabled", true).apply()
                        MicGuardService.startGuard(context)
                    }

                    RemediationActionType.INTERNAL_ENABLE_CLIPBOARD_GUARD -> {
                        val prefs = context.getSharedPreferences("antispam_privacy_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean(ClipboardGuardManager.KEY_CLIPBOARD_GUARD_ENABLED, true).apply()
                        ClipboardGuardManager.startGuard(context)
                        ClipboardGuardManager.updateStateFromPreferences(context)
                    }

                    RemediationActionType.INTERNAL_COPY_VPN_CONFIG -> {
                        val detectedVpn = VpnMaskingHelper.detectInstalledVpnClient(context)
                        val format = detectedVpn?.first ?: ru.antispam.app.privacy.VpnClientFormat.V2RAYTUN
                        VpnMaskingHelper.copyConfigToClipboard(context, format)
                    }

                    RemediationActionType.SYSTEM_REVOKE_NOTIFICATION_LISTENER -> {
                        val pkg = step.targetPackage
                        if (!pkg.isNullOrBlank()) {
                            PrivilegedRemediationExecutor.revokeNotificationListener(pkg)
                        }
                    }

                    RemediationActionType.SYSTEM_RESTRICT_LOCATION -> {
                        val pkg = step.targetPackage
                        if (!pkg.isNullOrBlank()) {
                            PrivilegedRemediationExecutor.revokeLocationPermissions(pkg)
                        }
                    }

                    else -> {}
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed executing automated step ${step.id}: ${e.message}")
            }
            completed++
            onProgress(step.title, completed, total)
        }

        return@withContext true
    }

    /**
     * Ultra-fast targeted verification of an individual guided step upon returning (onResume).
     * Returns true if the targeted vulnerability has been resolved by the user.
     */
    fun quickVerifyStep(context: Context, step: RemediationStep): Boolean {
        return try {
            when (step.actionType) {
                RemediationActionType.SYSTEM_REVOKE_NOTIFICATION_LISTENER -> {
                    val pkg = step.targetPackage ?: return true
                    val activeListeners = NotificationListenerAuditor.getEnabledNotificationListeners(context)
                    val stillActive = activeListeners.any {
                        it.packageName.equals(pkg, ignoreCase = true) &&
                                it.riskLevel == ListenerRiskLevel.HIGH &&
                                !it.isTrusted &&
                                !it.isDeepSleeping
                    }
                    !stillActive
                }

                RemediationActionType.SYSTEM_MANAGE_ACCESSIBILITY -> {
                    val pkg = step.targetPackage ?: return true
                    val activeServices = AccessibilityAuditor.getActiveAccessibilityServices(context)
                    val stillActive = activeServices.any {
                        it.packageName.equals(pkg, ignoreCase = true) &&
                                it.riskLevel == AccessibilityRiskLevel.CRITICAL &&
                                !it.isTrusted &&
                                !it.isDeepSleeping
                    }
                    !stillActive
                }

                RemediationActionType.SYSTEM_DISABLE_2G -> {
                    val cellular = CellularSecurityAuditor.auditCellularSecurity(context)
                    !cellular.is2GActive
                }

                RemediationActionType.SYSTEM_REMOVE_ROOT_CA -> {
                    val certAudit = CertificateGuardManager.auditCertificates(context)
                    certAudit.criticalCount == 0
                }

                RemediationActionType.SYSTEM_RESET_CALL_FORWARDING -> {
                    val forwarding = CallForwardingAuditor.auditCallForwarding(context)
                    !forwarding.hasActiveForwarding
                }

                RemediationActionType.SYSTEM_RESTRICT_LOCATION -> {
                    val summary = GeoTriangulationAuditor.auditSummary(context)
                    summary.criticalCount == 0
                }

                else -> true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error verifying step ${step.id}: ${e.message}")
            false
        }
    }
}
