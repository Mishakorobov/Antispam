package ru.antispam.app.privacy

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import ru.antispam.app.privacy.model.CertificateAuditSummary
import ru.antispam.app.privacy.model.CertificateRiskLevel
import ru.antispam.app.privacy.model.UserInstalledCertificate
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date

/**
 * CertificateGuardManager: Inspects the AndroidCAStore KeyStore for user-installed Root CAs.
 *
 * Threat Model:
 * Android classifies Root Certificate Authorities into "system:" (pre-installed in OS)
 * and "user:" (installed by user, MDM, or apps).
 *
 * An adversary, employer, spyware app, or state agency that persuades the user to install
 * a custom Root CA can perform transparent Man-in-the-Middle (MITM) decryption of HTTPS traffic:
 * 1. Read private passwords and 2FA sessions in real time.
 * 2. Decrypt banking, messenger, and email sessions.
 * 3. Inject malicious scripts or phishing redirects into secure connections.
 */
object CertificateGuardManager {

    private const val TAG = "CertificateGuard"
    const val ANDROID_CA_STORE = "AndroidCAStore"
    const val USER_PREFIX = "user:"

    /**
     * Extracts Common Name (CN) and Organization (O) from an X.500 Distinguished Name (DN) string.
     */
    fun extractCnAndOrg(dn: String): Pair<String, String> {
        var cn = ""
        var org = ""

        val parts = dn.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            val lower = trimmed.lowercase()
            if (lower.startsWith("cn=")) {
                cn = trimmed.substring(3).trim().trim('"', '\'')
            } else if (lower.startsWith("o=")) {
                org = trimmed.substring(2).trim().trim('"', '\'')
            }
        }

        if (cn.isBlank()) {
            cn = dn
        }

        return Pair(cn, org)
    }

    /**
     * Computes the SHA-256 fingerprint of a certificate as a hex string with colons (e.g., "AA:BB:CC:...").
     */
    fun calculateSha256Fingerprint(cert: Certificate): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.encoded)
            hash.joinToString(":") { byte ->
                String.format("%02X", byte)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate SHA-256 fingerprint: ${e.message}")
            "UNKNOWN"
        }
    }

    /**
     * Classifies security risk of a user-installed Root CA based on its Subject and Issuer DN.
     */
    fun classifyCertificateRisk(
        subjectDn: String,
        issuerDn: String
    ): Pair<CertificateRiskLevel, String> {
        val combined = "$subjectDn $issuerDn".lowercase()

        // 1. Russian National Root CA (Минцифры / Russian Trusted Root CA)
        if (combined.contains("russian trusted") ||
            combined.contains("ministry of digital") ||
            combined.contains("минцифры") ||
            combined.contains("госуслуги")
        ) {
            return Pair(
                CertificateRiskLevel.WARNING,
                "Государственный корневой сертификат (Минцифры РФ). Необходим для сайтов с сертификатами РФ, однако дает возможность операторам связи расшифровывать защищенный трафик."
            )
        }

        // 2. Known local content filtering / Ad-blocker Root CAs
        if (combined.contains("adguard") || combined.contains("adblock")) {
            return Pair(
                CertificateRiskLevel.MEDIUM,
                "Пользовательский сертификат локального блокировщика рекламы (AdGuard / AdBlocker). Используется для фильтрации HTTPS-рекламы."
            )
        }

        // 3. Known debugging / interception tools (Charles, Fiddler, HTTPCanary, Burp, mitmproxy)
        if (combined.contains("charles") ||
            combined.contains("fiddler") ||
            combined.contains("httpcanary") ||
            combined.contains("burp") ||
            combined.contains("mitmproxy")
        ) {
            return Pair(
                CertificateRiskLevel.CRITICAL,
                "🚨 КРИТИЧЕСКАЯ УГРОЗА MITM: Прокси-инструмент глубокого перехвата трафика (Charles / Fiddler / mitmproxy). Все ваши пароли и переписка расшифровываются!"
            )
        }

        // 4. Unknown third-party Root CA
        return Pair(
            CertificateRiskLevel.CRITICAL,
            "🚨 КРИТИЧЕСКИЙ РИСК: Неизвестный пользовательский Root CA! Владелец приватного ключа способен незаметно перехватывать и читать весь ваш зашифрованный HTTPS-трафик."
        )
    }

    /**
     * Parses an X509Certificate instance into a domain model.
     */
    fun parseX509Certificate(alias: String, cert: X509Certificate): UserInstalledCertificate {
        val subjectDn = cert.subjectDN?.name ?: ""
        val issuerDn = cert.issuerDN?.name ?: ""
        val (cn, org) = extractCnAndOrg(subjectDn)

        val validFrom = cert.notBefore
        val validTo = cert.notAfter
        val isExpired = validTo?.before(Date()) ?: false

        val fingerprint = calculateSha256Fingerprint(cert)
        val (riskLevel, riskDesc) = classifyCertificateRisk(subjectDn, issuerDn)

        return UserInstalledCertificate(
            alias = alias,
            subjectDn = subjectDn,
            issuerDn = issuerDn,
            commonName = cn,
            organization = org,
            validFrom = validFrom,
            validTo = validTo,
            isExpired = isExpired,
            sha256Fingerprint = fingerprint,
            riskLevel = riskLevel,
            riskDescription = riskDesc
        )
    }

    /**
     * Inspects a given KeyStore for user-installed certificates (alias starting with "user:").
     */
    fun inspectKeyStore(keyStore: KeyStore): List<UserInstalledCertificate> {
        val results = mutableListOf<UserInstalledCertificate>()
        try {
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith(USER_PREFIX) || alias.startsWith("user_")) {
                    val cert = keyStore.getCertificate(alias)
                    if (cert is X509Certificate) {
                        results.add(parseX509Certificate(alias, cert))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error iterating KeyStore aliases: ${e.message}")
        }

        return results.sortedBy {
            when (it.riskLevel) {
                CertificateRiskLevel.CRITICAL -> 0
                CertificateRiskLevel.WARNING -> 1
                CertificateRiskLevel.MEDIUM -> 2
            }
        }
    }

    /**
     * Inspects AndroidCAStore on live Android devices.
     */
    fun inspectUserCertificates(context: Context): List<UserInstalledCertificate> {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_CA_STORE)
            keyStore.load(null, null)
            inspectKeyStore(keyStore)
        } catch (e: Exception) {
            Log.w(TAG, "AndroidCAStore not available or access restricted: ${e.message}")
            emptyList()
        }
    }

    /**
     * Audits user-installed certificates and produces a summary.
     */
    fun auditCertificates(context: Context): CertificateAuditSummary {
        val certs = inspectUserCertificates(context)
        val critical = certs.count { it.riskLevel == CertificateRiskLevel.CRITICAL }
        val warning = certs.count { it.riskLevel == CertificateRiskLevel.WARNING }

        return CertificateAuditSummary(
            userCertificates = certs,
            criticalCount = critical,
            warningCount = warning,
            hasMitmThreat = critical > 0
        )
    }

    /**
     * Returns an Intent to open the Android Security / Credentials settings screen.
     */
    fun getSecuritySettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the system security / certificate management settings.
     */
    fun openSecuritySettings(context: Context) {
        try {
            context.startActivity(getSecuritySettingsIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open security settings: ${e.message}")
        }
    }
}
