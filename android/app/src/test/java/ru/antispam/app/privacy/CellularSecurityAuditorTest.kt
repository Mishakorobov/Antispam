package ru.antispam.app.privacy

import android.content.Context
import android.content.ContextWrapper
import android.telephony.TelephonyManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.privacy.model.CellularGeneration
import ru.antispam.app.privacy.model.CellularRiskLevel
import ru.antispam.app.privacy.model.PrivacyAuditInput
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType

class CellularSecurityAuditorTest {

    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        dummyContext = object : ContextWrapper(null) {}
        CellularSecurityAuditor.mockNetworkTypeProvider = null
        CellularSecurityAuditor.mockNullCipherNotificationsProvider = null
    }

    @After
    fun tearDown() {
        CellularSecurityAuditor.mockNetworkTypeProvider = null
        CellularSecurityAuditor.mockNullCipherNotificationsProvider = null
    }

    @Test
    fun test_mapNetworkTypeToGeneration_2gTypes_classifiedAsGen2g() {
        val types2g = listOf(
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN
        )

        for (type in types2g) {
            val gen = CellularSecurityAuditor.mapNetworkTypeToGeneration(type)
            assertEquals(CellularGeneration.GEN_2G, gen)
            assertFalse(gen.isSecure)
            assertTrue(gen.riskDescription.contains("Критический риск"))
        }
    }

    @Test
    fun test_mapNetworkTypeToGeneration_3gTypes_classifiedAsGen3g() {
        val types3g = listOf(
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA
        )

        for (type in types3g) {
            val gen = CellularSecurityAuditor.mapNetworkTypeToGeneration(type)
            assertEquals(CellularGeneration.GEN_3G, gen)
            assertTrue(gen.isSecure)
        }
    }

    @Test
    fun test_mapNetworkTypeToGeneration_4gAnd5gTypes_classifiedAsSecure() {
        assertEquals(CellularGeneration.GEN_4G_LTE, CellularSecurityAuditor.mapNetworkTypeToGeneration(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals(CellularGeneration.GEN_4G_LTE, CellularSecurityAuditor.mapNetworkTypeToGeneration(TelephonyManager.NETWORK_TYPE_IWLAN))
        assertEquals(CellularGeneration.GEN_5G, CellularSecurityAuditor.mapNetworkTypeToGeneration(TelephonyManager.NETWORK_TYPE_NR))
    }

    @Test
    fun test_networkTypeNames_formatCorrectly() {
        assertTrue(CellularSecurityAuditor.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_EDGE).contains("EDGE"))
        assertTrue(CellularSecurityAuditor.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_LTE).contains("LTE"))
        assertTrue(CellularSecurityAuditor.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_NR).contains("5G"))
    }

    @Test
    fun test_auditCellularSecurity_whenOn2g_triggersCriticalThreatAndRecommendations() {
        CellularSecurityAuditor.mockNetworkTypeProvider = { TelephonyManager.NETWORK_TYPE_EDGE }

        val report = CellularSecurityAuditor.auditCellularSecurity(dummyContext)

        assertTrue(report.is2GActive)
        assertEquals(CellularGeneration.GEN_2G, report.generation)
        assertEquals(CellularRiskLevel.CRITICAL, report.riskLevel)
        assertTrue(report.summary.contains("Внимание: Подключение работает через незащищенную сеть 2G"))
        assertTrue(report.recommendations.any { it.contains("Разрешить 2G") })
    }

    @Test
    fun test_auditCellularSecurity_whenOnLte_isSafe() {
        CellularSecurityAuditor.mockNetworkTypeProvider = { TelephonyManager.NETWORK_TYPE_LTE }

        val report = CellularSecurityAuditor.auditCellularSecurity(dummyContext)

        assertFalse(report.is2GActive)
        assertEquals(CellularGeneration.GEN_4G_LTE, report.generation)
        assertEquals(CellularRiskLevel.SAFE, report.riskLevel)
        assertTrue(report.summary.contains("4G/5G защищено"))
    }

    @Test
    fun test_auditCellularSecurity_nullCipherReporting() {
        CellularSecurityAuditor.mockNetworkTypeProvider = { TelephonyManager.NETWORK_TYPE_LTE }
        CellularSecurityAuditor.mockNullCipherNotificationsProvider = { true }

        val report = CellularSecurityAuditor.auditCellularSecurity(dummyContext)

        assertTrue(report.isNullCipherAlertSupported)
        assertEquals(true, report.isNullCipherAlertEnabled)
    }

    @Test
    fun test_createCellularSettingsIntent_returnsValidIntent() {
        val intent = CellularSecurityAuditor.createCellularSettingsIntent()
        assertNotNull(intent)
    }

    @Test
    fun test_getInstructionSteps_containsClearGuidance() {
        val steps = CellularSecurityAuditor.getInstructionSteps()
        assertTrue(steps.size >= 4)
        assertTrue(steps.any { it.contains("Разрешить 2G") })
    }

    @Test
    fun test_securityScoreEngine_active2gDeductsPenalty() {
        val cleanInput = PrivacyAuditInput(
            isRegistryUpToDate = true,
            isMicGuardEnabled = true,
            hasNotificationSpies = false,
            isClipboardGuardEnabled = true,
            hasMitmRootCa = false,
            hasActive2GConnection = false
        )
        val cleanReport = SecurityScoreEngine.calculateScore(cleanInput)
        assertEquals(100, cleanReport.score)

        val threatInput = cleanInput.copy(hasActive2GConnection = true)
        val threatReport = SecurityScoreEngine.calculateScore(threatInput)

        assertEquals(85, threatReport.score) // 100 - 15 = 85
        val issue2g = threatReport.issues.find { it.type == SecurityIssueType.CELLULAR_2G_DOWNGRADE_ACTIVE }
        assertNotNull(issue2g)
        assertEquals(SecurityIssueSeverity.CRITICAL, issue2g?.severity)
        assertEquals("Отключить 2G", issue2g?.actionLabel)
    }
}
