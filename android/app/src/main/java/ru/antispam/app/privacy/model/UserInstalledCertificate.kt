package ru.antispam.app.privacy.model

import java.util.Date

/**
 * Risk level of a user-installed Root CA certificate.
 */
enum class CertificateRiskLevel {
    /**
     * Unknown or suspicious user-installed Root CA capable of full HTTPS MITM interception.
     */
    CRITICAL,

    /**
     * Known local filtering CA (e.g., AdGuard, local proxy) installed by the user.
     */
    MEDIUM,

    /**
     * National / Regional Root CA (e.g., Минцифры / Russian Trusted Root CA).
     */
    WARNING
}

/**
 * Information about a Root CA certificate installed in the user store.
 */
data class UserInstalledCertificate(
    val alias: String,
    val subjectDn: String,
    val issuerDn: String,
    val commonName: String,
    val organization: String,
    val validFrom: Date?,
    val validTo: Date?,
    val isExpired: Boolean,
    val sha256Fingerprint: String,
    val riskLevel: CertificateRiskLevel,
    val riskDescription: String
)

/**
 * Summary of the Root CA certificate audit.
 */
data class CertificateAuditSummary(
    val userCertificates: List<UserInstalledCertificate>,
    val criticalCount: Int,
    val warningCount: Int,
    val hasMitmThreat: Boolean
)
