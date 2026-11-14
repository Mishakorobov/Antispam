package ru.antispam.app.security

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.data.repository.SyncRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

class AspbCryptoVerifierTest {

    private fun generateTestKeypair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val pair = gen.generateKeyPair()
        return Pair(
            pair.private as Ed25519PrivateKeyParameters,
            pair.public as Ed25519PublicKeyParameters
        )
    }

    private fun buildSampleAspbPayload(
        protoVersion: Short,
        currentVersion: Int,
        addedRecords: List<Triple<Long, Long, Byte>>
    ): ByteArray {
        val bodyBuffer = ByteBuffer.allocate(4 + addedRecords.size * 21 + 4).order(ByteOrder.BIG_ENDIAN)
        bodyBuffer.putInt(addedRecords.size)
        for (rec in addedRecords) {
            bodyBuffer.putLong(rec.first)
            bodyBuffer.putLong(rec.second)
            bodyBuffer.put(rec.third)
            bodyBuffer.putInt(currentVersion)
        }
        bodyBuffer.putInt(0) // 0 removed
        val bodyBytes = bodyBuffer.array()

        val headerBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        headerBuffer.put("ASPB".toByteArray(Charsets.US_ASCII))
        headerBuffer.putShort(protoVersion)
        headerBuffer.putInt(currentVersion)
        headerBuffer.putShort(1.toShort()) // isFullSync = true
        headerBuffer.putInt(0) // reserved
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
        val reserved = header.int

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
        v2Header.putInt(reserved)

        return v2Header.array() + signature + body
    }

    @Test
    fun test_hexConversion_roundtrip() {
        val sample = byteArrayOf(0x00, 0x24, 0xEB.toByte(), 0xAA.toByte(), 0xFF.toByte())
        val hex = AspbCryptoVerifier.bytesToHex(sample)
        assertEquals("0024ebaaff", hex)

        val recovered = AspbCryptoVerifier.hexToBytes(hex)
        assertEquals(sample.size, recovered.size)
        for (i in sample.indices) {
            assertEquals(sample[i], recovered[i])
        }
    }

    @Test
    fun test_verifyAspbSnapshot_validV2SignatureAccepted() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val v1 = buildSampleAspbPayload(
            protoVersion = 1,
            currentVersion = 55,
            addedRecords = listOf(
                Triple(79001110000L, 79001110099L, 1.toByte()),
                Triple(79112220000L, 79112220099L, 3.toByte())
            )
        )
        val signedV2 = signPayload(v1, privKey)

        val result = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false
        )

        assertTrue(result is AspbVerificationResult.Valid)
        val valid = result as AspbVerificationResult.Valid
        assertEquals(55L, valid.version)
        assertTrue(valid.isFullSync)
        assertEquals(128, valid.signatureHex.length)
        assertTrue(result.isAccepted)
    }

    @Test
    fun test_verifyAspbSnapshot_tamperedPayloadRejected() {
        val (privKey, pubKey) = generateTestKeypair()
        val pubHex = AspbCryptoVerifier.bytesToHex(pubKey.encoded)

        val v1 = buildSampleAspbPayload(
            protoVersion = 1,
            currentVersion = 10,
            addedRecords = listOf(Triple(79001110000L, 79001110099L, 1.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        // Mutate one byte in body
        signedV2[signedV2.size - 1] = (signedV2[signedV2.size - 1].toInt() xor 0xFF).toByte()

        val result = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = pubHex,
            allowUnsignedInDebug = false
        )

        assertTrue(result is AspbVerificationResult.InvalidSignature)
        assertFalse(result.isAccepted)
    }

    @Test
    fun test_verifyAspbSnapshot_wrongPublicKeyRejected() {
        val (privKey1, _) = generateTestKeypair()
        val (_, pubKey2) = generateTestKeypair()

        val v1 = buildSampleAspbPayload(1, 1, listOf(Triple(79001110000L, 79001110099L, 1.toByte())))
        val signedV2 = signPayload(v1, privKey1)

        val result = AspbCryptoVerifier.verifyAspbSnapshot(
            bytes = signedV2,
            expectedPublicKeyHex = AspbCryptoVerifier.bytesToHex(pubKey2.encoded),
            allowUnsignedInDebug = false
        )

        assertTrue(result is AspbVerificationResult.InvalidSignature)
        assertFalse(result.isAccepted)
    }

    @Test
    fun test_verifyAspbSnapshot_unsignedV1Policy() {
        val v1 = buildSampleAspbPayload(1, 10, listOf(Triple(79001110000L, 79001110099L, 1.toByte())))

        // Permitted with anti-rollback check
        val result = AspbCryptoVerifier.verifyAspbSnapshot(v1)
        assertTrue(result is AspbVerificationResult.UnsignedFormat)
        assertTrue(result.isAccepted)
    }

    @Test
    fun test_verifyAspbSnapshot_truncatedAndInvalidHeaders() {
        val tooShort = byteArrayOf(1, 2, 3)
        val shortResult = AspbCryptoVerifier.verifyAspbSnapshot(tooShort)
        assertTrue(shortResult is AspbVerificationResult.Corrupted)

        val invalidMagic = "FAIL".toByteArray() + ByteArray(20)
        val magicResult = AspbCryptoVerifier.verifyAspbSnapshot(invalidMagic)
        assertTrue(magicResult is AspbVerificationResult.Corrupted)
    }

    @Test
    fun test_syncRepository_parseAspbPayload_supportsV2Signature() {
        val (privKey, pubKey) = generateTestKeypair()
        val v1 = buildSampleAspbPayload(
            protoVersion = 1,
            currentVersion = 77,
            addedRecords = listOf(Triple(79001234567L, 79001234588L, 2.toByte()))
        )
        val signedV2 = signPayload(v1, privKey)

        val parsed = SyncRepository.parseAspbPayload(signedV2)
        assertEquals(2, parsed.protoVersion)
        assertEquals(77L, parsed.currentVersion)
        assertEquals(1, parsed.added.size)
        assertEquals("SURVEY", parsed.added[0].category)
        assertNotNull(parsed.signatureHex)
        assertEquals(128, parsed.signatureHex!!.length)
    }
}
