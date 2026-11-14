package ru.antispam.app.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.privacy.model.CertificateRiskLevel

class CertificateGuardManagerTest {

    @Test
    fun test_extractCnAndOrg_standardFormat() {
        val dn = "CN=Russian Trusted Root CA, O=The Ministry of Digital Development, C=RU"
        val (cn, org) = CertificateGuardManager.extractCnAndOrg(dn)

        assertEquals("Russian Trusted Root CA", cn)
        assertEquals("The Ministry of Digital Development", org)
    }

    @Test
    fun test_extractCnAndOrg_fallbackWithoutCn() {
        val dn = "O=Custom Enterprise, C=US"
        val (cn, org) = CertificateGuardManager.extractCnAndOrg(dn)

        assertEquals("O=Custom Enterprise, C=US", cn)
        assertEquals("Custom Enterprise", org)
    }

    @Test
    fun test_extractCnAndOrg_withQuotes() {
        val dn = "CN=\"AdGuard Personal CA\", O=\"Adguard Software Limited\""
        val (cn, org) = CertificateGuardManager.extractCnAndOrg(dn)

        assertEquals("AdGuard Personal CA", cn)
        assertEquals("Adguard Software Limited", org)
    }

    @Test
    fun test_classifyCertificateRisk_russianNationalCa_isWarning() {
        val (risk, desc) = CertificateGuardManager.classifyCertificateRisk(
            subjectDn = "CN=Russian Trusted Root CA, O=The Ministry of Digital Development",
            issuerDn = "CN=Russian Trusted Root CA, O=The Ministry of Digital Development"
        )

        assertEquals(CertificateRiskLevel.WARNING, risk)
        assertTrue(desc.contains("Минцифры"))
        assertTrue(desc.contains("расшифровывать"))
    }

    @Test
    fun test_classifyCertificateRisk_adGuardCa_isMedium() {
        val (risk, desc) = CertificateGuardManager.classifyCertificateRisk(
            subjectDn = "CN=AdGuard Personal CA, O=Adguard Software Limited",
            issuerDn = "CN=AdGuard Personal CA, O=Adguard Software Limited"
        )

        assertEquals(CertificateRiskLevel.MEDIUM, risk)
        assertTrue(desc.contains("AdGuard"))
    }

    @Test
    fun test_classifyCertificateRisk_mitmTools_isCritical() {
        val tools = listOf(
            "CN=Charles Proxy CA, O=XK72 Ltd",
            "CN=mitmproxy, O=mitmproxy",
            "CN=HTTPCanary Root CA",
            "CN=Fiddler Root Certificate, O=DO_NOT_TRUST",
            "CN=PortSwigger CA, O=Burp Suite"
        )

        for (toolSubject in tools) {
            val (risk, desc) = CertificateGuardManager.classifyCertificateRisk(
                subjectDn = toolSubject,
                issuerDn = toolSubject
            )
            assertEquals("Expected $toolSubject to be CRITICAL", CertificateRiskLevel.CRITICAL, risk)
            assertTrue(desc.contains("MITM") || desc.contains("перехвата"))
        }
    }

    @Test
    fun test_classifyCertificateRisk_unknownUserCa_isCritical() {
        val (risk, desc) = CertificateGuardManager.classifyCertificateRisk(
            subjectDn = "CN=SuperFreeWifi CA, O=Unknown Corp",
            issuerDn = "CN=SuperFreeWifi CA, O=Unknown Corp"
        )

        assertEquals(CertificateRiskLevel.CRITICAL, risk)
        assertTrue(desc.contains("КРИТИЧЕСКИЙ РИСК"))
        assertTrue(desc.contains("HTTPS"))
    }

    @Test
    fun test_constants_and_intentNotNull() {
        assertEquals("user:", CertificateGuardManager.USER_PREFIX)
        assertEquals("AndroidCAStore", CertificateGuardManager.ANDROID_CA_STORE)
        assertNotNull(CertificateGuardManager.getSecuritySettingsIntent())
    }
}
