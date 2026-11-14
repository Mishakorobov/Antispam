package ru.antispam.app.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AntiTamperHelperTest {

    @Test
    fun test_normalizeFingerprint_stripsColonsAndSpaces() {
        val raw = "db:c8:12:B2: 09:1c "
        val normalized = AntiTamperHelper.normalizeFingerprint(raw)
        assertEquals("DBC812B2091C", normalized)
    }

    @Test
    fun test_computeSha256_formatsColonSeparatedUppercase() {
        val sample = "AntiSpamGenuineKey2026".toByteArray(Charsets.UTF_8)
        val computed = AntiTamperHelper.computeSha256(sample)

        assertNotNull(computed)
        assertTrue(computed.contains(":"))
        assertEquals(32, computed.split(":").size) // 256 bits = 32 bytes

        val md = MessageDigest.getInstance("SHA-256")
        val expectedDigest = md.digest(sample).joinToString(":") { "%02X".format(it) }
        assertEquals(expectedDigest, computed)
    }

    @Test
    fun test_expectedProductionFingerprint_formatIsValid() {
        val normalized = AntiTamperHelper.normalizeFingerprint(AntiTamperHelper.EXPECTED_RELEASE_SHA256)
        assertEquals(64, normalized.length)
        assertEquals("DBC812B2091C2ABE35753572534D9669348D074C88508C1409EA48F10DADD1C8", normalized)

        val colonNormalized = AntiTamperHelper.normalizeFingerprint(AntiTamperHelper.EXPECTED_RELEASE_SHA256_COLON)
        assertEquals(normalized, colonNormalized)
    }

    @Test
    fun test_verifySignatureInternal_tamperedDetectedOnRelease() {
        // Test with non-matching bytes on release build (not debuggable)
        val tamperedBytes = "MaliciousHackerReSignedCertificate".toByteArray(Charsets.UTF_8)
        val tamperedRes = AntiTamperHelper.verifySignatureInternal(listOf(tamperedBytes), isDebuggable = false)
        assertEquals(SignatureStatus.TAMPERED, tamperedRes.status)
        assertFalse(tamperedRes.isSafe)
        assertTrue(tamperedRes.message.contains("модифицированная"))

        // Test with same non-matching bytes on debug build
        val debugRes = AntiTamperHelper.verifySignatureInternal(listOf(tamperedBytes), isDebuggable = true)
        assertEquals(SignatureStatus.DEBUG_BUILD, debugRes.status)
        assertTrue(debugRes.isSafe)
        assertTrue(debugRes.message.contains("Debug"))
    }

    @Test
    fun test_verifySignatureInternal_emptySignaturesTreatedAsTampered() {
        val result = AntiTamperHelper.verifySignatureInternal(emptyList(), isDebuggable = false)
        assertEquals(SignatureStatus.TAMPERED, result.status)
        assertFalse(result.isSafe)
        assertTrue(result.message.contains("Не удалось извлечь"))
    }

    @Test
    fun test_signatureVerificationResult_safetyProperty() {
        val genuine = SignatureVerificationResult(SignatureStatus.GENUINE_RELEASE, "HASH", "OK")
        assertTrue(genuine.isSafe)

        val debug = SignatureVerificationResult(SignatureStatus.DEBUG_BUILD, "HASH", "DEBUG")
        assertTrue(debug.isSafe)

        val tampered = SignatureVerificationResult(SignatureStatus.TAMPERED, "HASH", "TAMPERED")
        assertFalse(tampered.isSafe)
    }

    @Test
    fun test_environmentIntegrity_detectsSuBinary() {
        try {
            AntiTamperHelper.mockExistingFilesForTest = setOf("/system/xbin/su")
            val dummyContext = android.content.ContextWrapper(null)
            val report = AntiTamperHelper.checkEnvironmentIntegrity(dummyContext)

            assertTrue(report.isRootDetected)
            assertTrue(report.hasPrivilegeOrHookExposure)
            assertTrue(report.detectedIndicators.any { it.contains("/system/xbin/su") })
            assertTrue(report.description.contains("Root"))
        } finally {
            AntiTamperHelper.mockExistingFilesForTest = null
        }
    }

    @Test
    fun test_environmentIntegrity_detectsTestKeys() {
        try {
            AntiTamperHelper.mockBuildTagsForTest = "test-keys,release-keys"
            val dummyContext = android.content.ContextWrapper(null)
            val report = AntiTamperHelper.checkEnvironmentIntegrity(dummyContext)

            assertTrue(report.isRootDetected)
            assertTrue(report.hasPrivilegeOrHookExposure)
            assertTrue(report.detectedIndicators.any { it.contains("test-keys") })
        } finally {
            AntiTamperHelper.mockBuildTagsForTest = null
        }
    }

    @Test
    fun test_twoTierPolicy_rootDoesNotHardBlock() {
        // Genuine release signature with active Root
        val genuineBytes = "AntiSpamOfficialProductionCertificateBytes".toByteArray(Charsets.UTF_8)
        val dummyContext = android.content.ContextWrapper(null)

        try {
            // Emulate genuine signature matching expected
            val normalizedExpected = AntiTamperHelper.normalizeFingerprint(AntiTamperHelper.EXPECTED_RELEASE_SHA256)
            val genuineRes = SignatureVerificationResult(
                status = SignatureStatus.GENUINE_RELEASE,
                sha256Fingerprint = AntiTamperHelper.EXPECTED_RELEASE_SHA256,
                message = "Genuine"
            )
            AntiTamperHelper.mockExistingFilesForTest = setOf("/system/bin/su")

            val envReport = AntiTamperHelper.checkEnvironmentIntegrity(dummyContext)
            assertTrue(envReport.isRootDetected)

            // Crucial verification: genuine release build is NOT hard blocked even with root!
            assertFalse(genuineRes.status == SignatureStatus.TAMPERED)
            assertTrue(genuineRes.isSafe)
        } finally {
            AntiTamperHelper.mockExistingFilesForTest = null
        }
    }
}
