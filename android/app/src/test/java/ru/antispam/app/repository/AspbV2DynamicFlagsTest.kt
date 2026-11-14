package ru.antispam.app.repository

import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.antispam.app.antifraud.AntiFraudCallSmsMonitor
import ru.antispam.app.data.repository.SyncRepository
import ru.antispam.app.privacy.SuperAppInspector
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.security.AspbCryptoVerifier
import ru.antispam.app.security.AspbVerificationResult
import ru.antispam.app.security.DynamicConfig
import ru.antispam.app.security.DynamicConfigManager
import ru.antispam.app.util.PhoneNormalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

class AspbV2DynamicFlagsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        DynamicConfigManager.reset()
    }

    @After
    fun tearDown() {
        DynamicConfigManager.reset()
    }

    private fun generateTestKeypair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = gen.generateKeyPair()
        return Pair(
            pair.private as Ed25519PrivateKeyParameters,
            pair.public as Ed25519PublicKeyParameters
        )
    }

    private fun buildSampleAspbWithConfig(
        currentVersion: Int,
        dynamicConfig: DynamicConfig?,
        addedRecords: List<Triple<Long, Long, Byte>>
    ): ByteArray {
        val configBytes = if (dynamicConfig != null) {
            json.encodeToString(DynamicConfig.serializer(), dynamicConfig).toByteArray(Charsets.UTF_8)
        } else {
            ByteArray(0)
        }
        val configLen = configBytes.size

        val bodyBuffer = ByteBuffer.allocate(configLen + 4 + addedRecords.size * 21 + 4).order(ByteOrder.BIG_ENDIAN)
        if (configLen > 0) {
            bodyBuffer.put(configBytes)
        }
        bodyBuffer.putInt(addedRecords.size)
        for (rec in addedRecords) {
            bodyBuffer.putLong(rec.first)
            bodyBuffer.putLong(rec.second)
            bodyBuffer.put(rec.third)
            bodyBuffer.putInt(currentVersion)
        }
        bodyBuffer.putInt(0) // 0 removed
        val bodyBytes = bodyBuffer.array()

        val flags = (1) or (if (configLen > 0) 4 else 0) // isFullSync=1, hasDynamicConfig=4
        val headerBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        headerBuffer.put("ASPB".toByteArray(Charsets.US_ASCII))
        headerBuffer.putShort(1.toShort()) // proto_version 1 before signing
        headerBuffer.putInt(currentVersion)
        headerBuffer.putShort(flags.toShort())
        headerBuffer.putInt(configLen) // dynamicConfigLen
        val headerBytes = headerBuffer.array()

        return headerBytes + bodyBytes
    }

    private fun signPayload(
        v1Payload: ByteArray,
        privKey: Ed25519PrivateKeyParameters
    ): ByteArray {
        val header = ByteBuffer.wrap(v1Payload, 0, 16).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4)
        header.get(magic)
        header.short // skip proto_ver
        val curVer = header.int
        val flags = header.short
        val configLen = header.int

        val body = v1Payload.copyOfRange(16, v1Payload.size)

        val signer = Ed25519Signer()
        signer.init(true, privKey)
        signer.update(body, 0, body.size)
        val signature = signer.generateSignature()

        val v2Header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        v2Header.put(magic)
        v2Header.putShort(2.toShort()) // proto_version = 2
        v2Header.putInt(curVer)
        v2Header.putShort(flags)
        v2Header.putInt(configLen)

        return v2Header.array() + signature + body
    }

    @Test
    fun test_aspbV2_dynamicConfig_roundtripAndVerification() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val config = DynamicConfig(
            newSpywarePackages = listOf("ru.superapp.tracker", "com.stealth.logger"),
            dynamicCdnMirrors = listOf("https://backup-cdn.antispam.org/pools.aspb"),
            antifraudSmsKeywords = listOf("код для входа", "пароль от личного кабинета"),
            foreignSpoofingPrefixes = listOf("76", "77", "7940")
        )

        val v1 = buildSampleAspbWithConfig(
            currentVersion = 88,
            dynamicConfig = config,
            addedRecords = listOf(Triple(79001112233L, 79001112244L, 1.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        // 1. Verify with AspbCryptoVerifier
        val verResult = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false,
            localStoredVersion = 50L
        )
        assertTrue(verResult is AspbVerificationResult.Valid)
        val valid = verResult as AspbVerificationResult.Valid
        assertEquals(88L, valid.version)
        assertTrue(valid.dynamicConfigLen > 0)
        assertTrue(verResult.isAccepted)

        // 2. Parse with SyncRepository.parseAspbPayload
        val parsed = SyncRepository.parseAspbPayload(signedV2)
        assertEquals(2, parsed.protoVersion)
        assertEquals(88L, parsed.currentVersion)
        assertNotNull(parsed.dynamicConfig)
        val dyn = parsed.dynamicConfig!!
        assertEquals(listOf("ru.superapp.tracker", "com.stealth.logger"), dyn.newSpywarePackages)
        assertEquals(listOf("https://backup-cdn.antispam.org/pools.aspb"), dyn.dynamicCdnMirrors)
        assertEquals(listOf("код для входа", "пароль от личного кабинета"), dyn.antifraudSmsKeywords)
        assertEquals(listOf("76", "77", "7940"), dyn.foreignSpoofingPrefixes)
        assertEquals(1, parsed.added.size)
    }

    @Test
    fun test_aspbV2_tamperedDynamicConfig_failsSignature() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val config = DynamicConfig(
            newSpywarePackages = listOf("ru.superapp.tracker")
        )
        val v1 = buildSampleAspbWithConfig(
            currentVersion = 90,
            dynamicConfig = config,
            addedRecords = listOf(Triple(79001112233L, 79001112244L, 1.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        // Mutate dynamic config JSON (at byte 85)
        signedV2[85] = (signedV2[85].toInt() xor 0xFF).toByte()

        val verResult = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false
        )
        assertTrue(verResult is AspbVerificationResult.InvalidSignature)
        assertFalse(verResult.isAccepted)
    }

    @Test
    fun test_aspbV2_antiRollbackProtection() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val v1 = buildSampleAspbWithConfig(
            currentVersion = 40,
            dynamicConfig = null,
            addedRecords = listOf(Triple(79001112233L, 79001112244L, 1.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        // Local stored version is 60 -> incoming 40 must be rejected as rollback attack!
        val rollbackResult = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false,
            localStoredVersion = 60L
        )
        assertTrue(rollbackResult is AspbVerificationResult.RollbackDetected)
        val rollback = rollbackResult as AspbVerificationResult.RollbackDetected
        assertEquals(40L, rollback.incomingVersion)
        assertEquals(60L, rollback.localVersion)
        assertFalse(rollbackResult.isAccepted)

        // If local is 40 or lower -> accepted
        val okResult = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false,
            localStoredVersion = 40L
        )
        assertTrue(okResult is AspbVerificationResult.Valid)
    }

    @Test
    fun test_dynamicConfig_appliesToSecurityModules() {
        val config = DynamicConfig(
            newSpywarePackages = listOf("ru.custom.tracker"),
            dynamicCdnMirrors = listOf("https://custom-cdn.example.org/pools.aspb"),
            antifraudSmsKeywords = listOf("секретный ключ доступа"),
            foreignSpoofingPrefixes = listOf("7999888")
        )

        DynamicConfigManager.applyConfig(config)

        // 1. Check AntiFraudCallSmsMonitor recognizes dynamic keyword
        val smsText = "Ваш секретный ключ доступа: 481921. Никому не говорите."
        assertTrue(AntiFraudCallSmsMonitor.isFraudVerificationMessage(smsText))

        // 2. Check PhoneNormalizer recognizes dynamic prefix
        assertTrue(PhoneNormalizer.isKazakhstanOrForeignPlus7(79998881234L))

        // 3. Check SuperAppInspector recognizes dynamic spyware package
        val (risk, category, _) = SuperAppInspector.classifyRisk(
            packageName = "ru.custom.tracker",
            requestedPermissions = setOf(SuperAppInspector.PERM_QUERY_ALL_PACKAGES),
            isSystemApp = false
        )
        assertEquals(SuperAppRiskLevel.CRITICAL, risk)

        // 4. Check CDN mirror retrieval
        assertEquals(listOf("https://custom-cdn.example.org/pools.aspb"), DynamicConfigManager.getDynamicCdnMirrors())
    }

    @Test
    fun test_aspbV2_withoutDynamicConfig_backwardCompatible() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val v1 = buildSampleAspbWithConfig(
            currentVersion = 15,
            dynamicConfig = null,
            addedRecords = listOf(Triple(79001112233L, 79001112244L, 1.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        val verResult = AspbCryptoVerifier.verifyAspbSnapshot(signedV2, pubHex, allowUnsignedInDebug = false)
        assertTrue(verResult is AspbVerificationResult.Valid)
        val valid = verResult as AspbVerificationResult.Valid
        assertEquals(0, valid.dynamicConfigLen)

        val parsed = SyncRepository.parseAspbPayload(signedV2)
        assertEquals(15L, parsed.currentVersion)
        assertNull(parsed.dynamicConfig)
        assertNull(parsed.dynamicConfigRawJson)
        assertEquals(1, parsed.added.size)
    }
}
