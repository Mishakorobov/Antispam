package ru.antispam.app.security

import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import ru.antispam.app.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of ASPB snapshot cryptographic verification.
 */
sealed class AspbVerificationResult {
    data class Valid(
        val version: Long,
        val isFullSync: Boolean,
        val signatureHex: String,
        val dynamicConfigLen: Int = 0
    ) : AspbVerificationResult()

    data class RollbackDetected(
        val incomingVersion: Long,
        val localVersion: Long,
        val message: String
    ) : AspbVerificationResult()

    data class InvalidSignature(
        val reason: String,
        val signatureHex: String?
    ) : AspbVerificationResult()

    data class UnsignedFormat(
        val protoVersion: Int,
        val isAllowed: Boolean,
        val message: String
    ) : AspbVerificationResult()

    data class Corrupted(
        val error: String
    ) : AspbVerificationResult()

    val isAccepted: Boolean
        get() = this is Valid || (this is UnsignedFormat && isAllowed)
}

/**
 * AspbCryptoVerifier: Asymmetric Ed25519 cryptographic verifier for AntiSpam binary snapshots.
 *
 * Guarantees mathematical immutability of binary pool files (pools.aspb)
 * delivered over public CDNs (GitHub Releases, jsDelivr, Cloudflare).
 * Eliminates MITM attacks, emergency number blocking exploits, and unauthorized database injection.
 */
object AspbCryptoVerifier {

    private const val TAG = "AspbCryptoVerifier"

    /**
     * Default production master public key embedded in the build.
     */
    val DEFAULT_PUBLIC_KEY_HEX: String = BuildConfig.ED25519_PUBLIC_KEY

    /**
     * Header size constants.
     */
    const val ASPB_HEADER_SIZE = 16
    const val ED25519_SIGNATURE_SIZE = 64
    const val ASPB_V2_HEADER_SIZE = ASPB_HEADER_SIZE + ED25519_SIGNATURE_SIZE // 80 bytes

    /**
     * Verifies the cryptographic integrity of an ASPB binary snapshot.
     *
     * @param bytes Raw binary content of the ASPB file.
     * @param expectedPublicKeyHex 32-byte Ed25519 public key formatted as a 64-char hex string.
     * @param allowUnsignedInDebug If true, permits ASPB v1 unsigned files during development.
     */
    fun verifyAspbSnapshot(
        bytes: ByteArray,
        expectedPublicKeyHex: String = DEFAULT_PUBLIC_KEY_HEX,
        allowUnsignedInDebug: Boolean = BuildConfig.DEBUG,
        localStoredVersion: Long = 0L
    ): AspbVerificationResult {
        if (bytes.size < ASPB_HEADER_SIZE) {
            return AspbVerificationResult.Corrupted("Payload too short for ASPB header: ${bytes.size} bytes")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // 1. Verify Magic
        val magic = ByteArray(4)
        buffer.get(magic)
        if (magic[0] != 'A'.code.toByte() || magic[1] != 'S'.code.toByte() ||
            magic[2] != 'P'.code.toByte() || magic[3] != 'B'.code.toByte()
        ) {
            return AspbVerificationResult.Corrupted("Invalid ASPB magic header: ${String(magic)}")
        }

        val protoVer = buffer.short.toInt() and 0xFFFF
        val currentVer = buffer.int.toLong() and 0xFFFFFFFFL
        val flags = buffer.short.toInt() and 0xFFFF
        val dynamicConfigLen = buffer.int.toLong() and 0xFFFFFFFFL
        val isFullSync = (flags and 1) != 0

        // Anti-Rollback Protection: prevent downgrade attacks where an attacker serves an older valid signed snapshot
        if (localStoredVersion > 0L && currentVer < localStoredVersion) {
            val rollbackMsg = "Anti-rollback protection: snapshot version $currentVer is older than local version $localStoredVersion"
            Log.e(TAG, "SECURITY_ROLLBACK_ATTACK: $rollbackMsg")
            return AspbVerificationResult.RollbackDetected(
                incomingVersion = currentVer,
                localVersion = localStoredVersion,
                message = rollbackMsg
            )
        }

        // Handle ASPB v1 (Unsigned legacy format)
        if (protoVer == 1) {
            return if (allowUnsignedInDebug) {
                Log.w(TAG, "Unsigned ASPB v1 snapshot detected. Permitted in Debug mode.")
                AspbVerificationResult.UnsignedFormat(
                    protoVersion = 1,
                    isAllowed = true,
                    message = "Unsigned ASPB v1 permitted in debug build."
                )
            } else {
                Log.e(TAG, "SECURITY ALERT: Unsigned ASPB v1 snapshot rejected! Release builds mandate Ed25519 signatures.")
                AspbVerificationResult.UnsignedFormat(
                    protoVersion = 1,
                    isAllowed = false,
                    message = "SECURITY_INTEGRITY_BREACH: Release build strictly requires signed ASPB v2 snapshots."
                )
            }
        }

        // Handle ASPB v2 (Signed format)
        if (protoVer == 2) {
            if (bytes.size < ASPB_V2_HEADER_SIZE) {
                return AspbVerificationResult.Corrupted(
                    "Payload too short for ASPB v2 signed header: ${bytes.size} bytes (minimum $ASPB_V2_HEADER_SIZE required)"
                )
            }

            buffer.position(ASPB_HEADER_SIZE)
            val signatureBytes = ByteArray(ED25519_SIGNATURE_SIZE)
            buffer.get(signatureBytes)
            val signatureHex = bytesToHex(signatureBytes)

            val pubKeyBytes = try {
                hexToBytes(expectedPublicKeyHex)
            } catch (e: Exception) {
                return AspbVerificationResult.InvalidSignature(
                    reason = "Invalid Ed25519 public key hex: ${e.message}",
                    signatureHex = signatureHex
                )
            }

            if (pubKeyBytes.size != 32) {
                return AspbVerificationResult.InvalidSignature(
                    reason = "Ed25519 public key must be 32 bytes, got ${pubKeyBytes.size}",
                    signatureHex = signatureHex
                )
            }

            // Body starts at offset 80 (dynamic config JSON + pool deltas)
            val bodyOffset = ASPB_V2_HEADER_SIZE
            val bodyLength = bytes.size - bodyOffset

            val isValid = verifyEd25519Signature(
                data = bytes,
                dataOffset = bodyOffset,
                dataLength = bodyLength,
                signature = signatureBytes,
                publicKeyBytes = pubKeyBytes
            )

            return if (isValid) {
                Log.i(TAG, "ASPB snapshot v$currentVer cryptographically verified via Ed25519! Signature: ${signatureHex.take(16)}... (dynamicConfigLen=$dynamicConfigLen)")
                AspbVerificationResult.Valid(
                    version = currentVer,
                    isFullSync = isFullSync,
                    signatureHex = signatureHex,
                    dynamicConfigLen = dynamicConfigLen.toInt()
                )
            } else {
                Log.e(TAG, "SECURITY_INTEGRITY_BREACH: Ed25519 signature mismatch! File body has been altered.")
                AspbVerificationResult.InvalidSignature(
                    reason = "Ed25519 signature mismatch. Potential MITM or database tampering detected.",
                    signatureHex = signatureHex
                )
            }
        }

        return AspbVerificationResult.Corrupted("Unsupported ASPB protocol version: $protoVer")
    }

    /**
     * Pure Ed25519 signature verification using BouncyCastle.
     */
    fun verifyEd25519Signature(
        data: ByteArray,
        dataOffset: Int,
        dataLength: Int,
        signature: ByteArray,
        publicKeyBytes: ByteArray
    ): Boolean {
        return try {
            val verifier = Ed25519Signer()
            val pubParams = Ed25519PublicKeyParameters(publicKeyBytes, 0)
            verifier.init(false, pubParams)
            verifier.update(data, dataOffset, dataLength)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Ed25519 verification failed with exception: ${e.message}", e)
            false
        }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().replace(":", "").replace(" ", "")
        require(clean.length % 2 == 0) { "Hex string must have an even length" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
