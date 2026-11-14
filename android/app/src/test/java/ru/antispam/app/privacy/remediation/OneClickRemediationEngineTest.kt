package ru.antispam.app.privacy.remediation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType
import ru.antispam.app.privacy.model.SecurityScoreReport
import ru.antispam.app.ui.privacy.PrivacyShieldUiState

class OneClickRemediationEngineTest {

    private val mockContext = object : android.content.ContextWrapper(null) {}

    @Test
    fun test_planRemediation_purelyInternalIssues_allAutomatedZeroGuided() {
        val issues = listOf(
            SecurityIssue(
                id = "issue_registry",
                type = SecurityIssueType.REGISTRY_OUTDATED,
                title = "База устарела",
                description = "Требуется обновление",
                severity = SecurityIssueSeverity.HIGH,
                scoreDeduction = 30,
                actionLabel = "Обновить"
            ),
            SecurityIssue(
                id = "issue_mic",
                type = SecurityIssueType.MIC_GUARD_DISABLED,
                title = "Микрофон уязвим",
                description = "Защита выключена",
                severity = SecurityIssueSeverity.HIGH,
                scoreDeduction = 20,
                actionLabel = "Включить"
            ),
            SecurityIssue(
                id = "issue_clipboard",
                type = SecurityIssueType.CLIPBOARD_GUARD_DISABLED,
                title = "Буфер обмена",
                description = "Шредер выключен",
                severity = SecurityIssueSeverity.MEDIUM,
                scoreDeduction = 15,
                actionLabel = "Включить"
            ),
            SecurityIssue(
                id = "issue_vpn",
                type = SecurityIssueType.SUPERAPP_VPN_EXPOSED,
                title = "VPN инвентарь",
                description = "Маршрутизация",
                severity = SecurityIssueSeverity.MEDIUM,
                scoreDeduction = 10,
                actionLabel = "Изолировать"
            )
        )

        val state = PrivacyShieldUiState(
            securityScoreReport = SecurityScoreReport(
                score = 25,
                issues = issues,
                statusTitle = "Высокий риск",
                statusDescription = "Опасность",
                categoryBreakdown = emptyMap()
            )
        )

        val plan = OneClickRemediationEngine.planRemediation(mockContext, issues, state)

        assertEquals(4, plan.totalIssuesCount)
        assertEquals(4, plan.automatedSteps.size)
        assertEquals(0, plan.guidedSteps.size)
        assertFalse(plan.hasGuidedSteps)
        assertEquals(100, plan.projectedScore) // 25 + 30 + 20 + 15 + 10 = 100

        assertTrue(plan.automatedSteps.any { it.actionType == RemediationActionType.INTERNAL_SYNC_REGISTRY })
        assertTrue(plan.automatedSteps.any { it.actionType == RemediationActionType.INTERNAL_ENABLE_MIC_GUARD })
        assertTrue(plan.automatedSteps.any { it.actionType == RemediationActionType.INTERNAL_ENABLE_CLIPBOARD_GUARD })
        assertTrue(plan.automatedSteps.any { it.actionType == RemediationActionType.INTERNAL_COPY_VPN_CONFIG })
    }

    @Test
    fun test_planRemediation_guidedSystemIssues_sortedBySeverity() {
        val issues = listOf(
            SecurityIssue(
                id = "issue_notification",
                type = SecurityIssueType.NOTIFICATION_SPY_DETECTED,
                title = "Шпион шторки",
                description = "Чтение СМС",
                severity = SecurityIssueSeverity.CRITICAL,
                scoreDeduction = 20,
                actionLabel = "Отозвать"
            ),
            SecurityIssue(
                id = "issue_cellular",
                type = SecurityIssueType.CELLULAR_2G_DOWNGRADE_ACTIVE,
                title = "2G уязвим",
                description = "IMSI перехват",
                severity = SecurityIssueSeverity.HIGH,
                scoreDeduction = 15,
                actionLabel = "Настроить"
            ),
            SecurityIssue(
                id = "issue_forwarding",
                type = SecurityIssueType.CALL_FORWARDING_ACTIVE,
                title = "Переадресация",
                description = "Сброс ##002#",
                severity = SecurityIssueSeverity.CRITICAL,
                scoreDeduction = 25,
                actionLabel = "Сбросить"
            )
        )

        val rogueListener = NotificationListenerApp(
            packageName = "com.rogue.smsreader",
            appName = "SMS Reader Spy",
            serviceName = ".SmsListener",
            isSystemApp = false,
            riskLevel = ListenerRiskLevel.HIGH,
            riskDescription = "Шпион",
            isTrusted = false,
            isDeepSleeping = false
        )

        val state = PrivacyShieldUiState(
            notificationListeners = listOf(rogueListener),
            securityScoreReport = SecurityScoreReport(
                score = 40,
                issues = issues,
                statusTitle = "Критический риск",
                statusDescription = "Опасность",
                categoryBreakdown = emptyMap()
            )
        )

        val plan = OneClickRemediationEngine.planRemediation(mockContext, issues, state)

        assertTrue(plan.hasGuidedSteps)
        assertEquals(3, plan.guidedSteps.size)

        // Sorted: CRITICAL items first
        val firstStep = plan.guidedSteps[0]
        assertEquals(SecurityIssueSeverity.CRITICAL, firstStep.severity)
        val secondStep = plan.guidedSteps[1]
        assertEquals(SecurityIssueSeverity.CRITICAL, secondStep.severity)
        val thirdStep = plan.guidedSteps[2]
        assertEquals(SecurityIssueSeverity.HIGH, thirdStep.severity)
        assertEquals(RemediationActionType.SYSTEM_DISABLE_2G, thirdStep.actionType)
    }

    @Test
    fun test_planRemediation_deepSleepingAndTrustedApps_excludedFromGuidedSteps() {
        val issues = listOf(
            SecurityIssue(
                id = "issue_notification",
                type = SecurityIssueType.NOTIFICATION_SPY_DETECTED,
                title = "Шпион шторки",
                description = "Чтение СМС",
                severity = SecurityIssueSeverity.CRITICAL,
                scoreDeduction = 20,
                actionLabel = "Отозвать"
            ),
            SecurityIssue(
                id = "issue_accessibility",
                type = SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED,
                title = "Спецвозможности",
                description = "Перехват ввода",
                severity = SecurityIssueSeverity.CRITICAL,
                scoreDeduction = 20,
                actionLabel = "Отозвать"
            )
        )

        // 1 sleeping listener, 1 trusted accessibility app
        val sleepingListener = NotificationListenerApp(
            packageName = "com.samsung.android.app.modes",
            appName = "Режимы и сценарии",
            serviceName = ".ModesService",
            isSystemApp = true,
            riskLevel = ListenerRiskLevel.HIGH,
            riskDescription = "Системный",
            isTrusted = false,
            isDeepSleeping = true
        )
        val trustedAccessibility = AccessibilityServiceApp(
            packageName = "com.google.android.marvin.talkback",
            appName = "TalkBack",
            serviceName = ".TalkBackService",
            isSystemApp = true,
            riskLevel = AccessibilityRiskLevel.CRITICAL,
            riskDescription = "Системный",
            isTrusted = true,
            isDeepSleeping = false
        )

        val state = PrivacyShieldUiState(
            notificationListeners = listOf(sleepingListener),
            accessibilityServices = listOf(trustedAccessibility),
            securityScoreReport = SecurityScoreReport(
                score = 60,
                issues = issues,
                statusTitle = "Умеренный риск",
                statusDescription = "Опасность",
                categoryBreakdown = emptyMap()
            )
        )

        val plan = OneClickRemediationEngine.planRemediation(mockContext, issues, state)

        // Both apps are excluded: 0 guided steps created
        assertEquals(0, plan.guidedSteps.size)
    }

    @Test
    fun test_planRemediation_passwordManager_flaggedAsCanBeWhitelisted() {
        val issues = listOf(
            SecurityIssue(
                id = "issue_accessibility",
                type = SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED,
                title = "Спецвозможности",
                description = "Перехват ввода",
                severity = SecurityIssueSeverity.CRITICAL,
                scoreDeduction = 20,
                actionLabel = "Отозвать"
            )
        )

        val bitwarden = AccessibilityServiceApp(
            packageName = "com.x8bit.bitwarden",
            appName = "Bitwarden",
            serviceName = ".A11yService",
            isSystemApp = false,
            riskLevel = AccessibilityRiskLevel.CRITICAL,
            riskDescription = "Менеджер паролей",
            isTrusted = false,
            isDeepSleeping = false
        )

        val state = PrivacyShieldUiState(
            accessibilityServices = listOf(bitwarden),
            securityScoreReport = SecurityScoreReport(
                score = 80,
                issues = issues,
                statusTitle = "Защищен",
                statusDescription = "В норме",
                categoryBreakdown = emptyMap()
            )
        )

        val plan = OneClickRemediationEngine.planRemediation(mockContext, issues, state)

        assertEquals(1, plan.guidedSteps.size)
        val step = plan.guidedSteps.first()
        assertTrue(step.canBeWhitelisted)
        assertTrue(step.instruction.contains("менеджер паролей"))
    }
}
