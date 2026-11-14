package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType

class SecurityScoreEngineTest {

    @Test
    fun test_calculateScore_allProtectionsActive_returns100() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasCriticalAccessibility = false,
            hasVpnInventoryExposed = false,
            hasStealthGeoTrackers = false
        )

        val report = SecurityScoreEngine.calculateScore(input)

        assertEquals(100, report.score)
        assertTrue(report.issues.isEmpty())
        assertEquals("Надежно защищен", report.statusTitle)
        assertEquals(30, report.categoryBreakdown["Реестр Минцифры"])
        assertEquals(20, report.categoryBreakdown["Защита микрофона"])
        assertEquals(20, report.categoryBreakdown["Защита СМС и 2FA"])
        assertEquals(15, report.categoryBreakdown["Шредер буфера 2FA"])
        assertEquals(15, report.categoryBreakdown["Сертификаты (MITM)"])
    }

    @Test
    fun test_calculateScore_allProtectionsDisabled_returns0() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = false,
            isMicGuardEnabled = false,
            hasNotificationSpies = true,
            isClipboardGuardEnabled = false,
            hasMitmRootCa = true,
            hasCriticalAccessibility = true,
            hasVpnInventoryExposed = true,
            hasStealthGeoTrackers = true
        )

        val report = SecurityScoreEngine.calculateScore(input)

        assertEquals(0, report.score)
        assertEquals("Критический риск", report.statusTitle)
        assertTrue(report.issues.size >= 5)
    }

    @Test
    fun test_calculateScore_partialDeductions_registryOutdated() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = false,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false
        )

        val report = SecurityScoreEngine.calculateScore(input)

        // 100 - 30 = 70
        assertEquals(70, report.score)
        assertEquals("Требуется внимание", report.statusTitle)
        assertEquals(1, report.issues.size)
        assertEquals(SecurityIssueType.REGISTRY_OUTDATED, report.issues[0].type)
        assertEquals(30, report.issues[0].scoreDeduction)
    }

    @Test
    fun test_calculateScore_micGuardDisabled() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = false,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false
        )

        val report = SecurityScoreEngine.calculateScore(input)

        // 100 - 20 = 80
        assertEquals(80, report.score)
        assertEquals(1, report.issues.size)
        assertEquals(SecurityIssueType.MIC_GUARD_DISABLED, report.issues[0].type)
    }

    @Test
    fun test_calculateScore_notificationSpyDetected() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = true,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false
        )

        val report = SecurityScoreEngine.calculateScore(input)

        // 100 - 20 = 80
        assertEquals(80, report.score)
        assertEquals(1, report.issues.size)
        assertEquals(SecurityIssueType.NOTIFICATION_SPY_DETECTED, report.issues[0].type)
        assertEquals(SecurityIssueSeverity.CRITICAL, report.issues[0].severity)
    }

    @Test
    fun test_calculateScore_penaltiesAppliedForAccessibilityAndVpn() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasCriticalAccessibility = true, // -10
            hasVpnInventoryExposed = true,   // -10
            hasStealthGeoTrackers = false
        )

        val report = SecurityScoreEngine.calculateScore(input)

        // 100 - 10 - 10 = 80
        assertEquals(80, report.score)
        assertEquals(2, report.issues.size)
        assertTrue(report.issues.any { it.type == SecurityIssueType.CRITICAL_ACCESSIBILITY_DETECTED })
        assertTrue(report.issues.any { it.type == SecurityIssueType.SUPERAPP_VPN_EXPOSED })
    }

    @Test
    fun test_issues_sortedBySeverity() {
        val input = PrivacyAuditInput(
            isRegistryUpToDate = false, // HIGH
            isMicGuardEnabled = true,
            hasNotificationSpies = true, // CRITICAL
            isClipboardGuardEnabled = false, // MEDIUM
            hasMitmRootCa = false,
            hasRootOrHooks = true // INFO
        )

        val report = SecurityScoreEngine.calculateScore(input)

        assertEquals(4, report.issues.size)
        assertEquals(SecurityIssueSeverity.CRITICAL, report.issues[0].severity)
        assertEquals(SecurityIssueSeverity.HIGH, report.issues[1].severity)
        assertEquals(SecurityIssueSeverity.MEDIUM, report.issues[2].severity)
        assertEquals(SecurityIssueSeverity.INFO, report.issues[3].severity)
    }

    @Test
    fun test_calculateScore_rootOrHooks_producesAdvisoryIssueWithoutScoreDeduction() {
        // Phase 11.1: Power-user with Root/Magisk and all other shields active
        val input = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasCriticalAccessibility = false,
            hasVpnInventoryExposed = false,
            hasStealthGeoTrackers = false,
            hasRootOrHooks = true
        )

        val report = SecurityScoreEngine.calculateScore(input)

        // Soft advisory does NOT deduct points from the user (score remains 100!)
        assertEquals(100, report.score)
        assertEquals("Надежно защищен", report.statusTitle)
        assertEquals(1, report.issues.size)

        val issue = report.issues[0]
        assertEquals(SecurityIssueType.ROOT_OR_HOOK_ENVIRONMENT, issue.type)
        assertEquals(SecurityIssueSeverity.INFO, issue.severity)
        assertEquals(0, issue.scoreDeduction)
        assertTrue(issue.description.contains("Root/Magisk/KernelSU"))
    }
}
