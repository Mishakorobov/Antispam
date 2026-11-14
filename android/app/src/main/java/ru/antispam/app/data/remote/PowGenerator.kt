package ru.antispam.app.data.remote

import java.security.MessageDigest

/**
 * Result of solving a micro-Proof-of-Work challenge.
 */
data class PowSolution(
    val nonce: Long,
    val timestamp: Long,
    val hashHex: String
)

/**
 * PowGenerator:
 * Micro-Proof-of-Work (PoW) generator for crowdsource report submission.
 *
 * Threat:
 * Distributed proxy-farms and botnets can bypass IP-based rate limiting (10 req/min)
 * and flood the Cloudflare Worker bridge / GitHub Actions repository_dispatch queue.
 *
 * Solution:
 * Requires the client to solve a lightweight 16-bit SHA-256 challenge
 * (finding nonce such that SHA256(phone:deviceHash:timestamp:nonce) starts with two zero bytes: "0000...").
 * Takes ~10–30 ms on a mobile phone (negligible for single users), but makes mass spamming
 * computationally prohibitive for botnets.
 */
object PowGenerator {

    const val REQUIRED_ZERO_BYTES = 2 // 16 bits of leading zeros ("0000...")
    const val MAX_EXPIRY_SECONDS = 600L // 10 minutes tolerance window

    /**
     * Solves the Proof-of-Work challenge for the given phone number and device hash.
     */
    fun solve(
        phoneNumber: String,
        deviceHash: String,
        timestamp: Long = System.currentTimeMillis() / 1000
    ): PowSolution {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val md = MessageDigest.getInstance("SHA-256")
        var nonce = 0L

        while (true) {
            val challenge = "$cleanPhone:$deviceHash:$timestamp:$nonce"
            md.reset()
            val digest = md.digest(challenge.toByteArray(Charsets.UTF_8))

            if (digest[0] == 0.toByte() && digest[1] == 0.toByte()) {
                val hex = digest.joinToString("") { "%02x".format(it) }
                return PowSolution(
                    nonce = nonce,
                    timestamp = timestamp,
                    hashHex = hex
                )
            }
            nonce++
        }
    }

    /**
     * Verifies the Proof-of-Work solution.
     */
    fun verify(
        phoneNumber: String,
        deviceHash: String,
        timestamp: Long,
        nonce: Long,
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000
    ): Boolean {
        // 1. Check timestamp expiration window (+- 10 minutes)
        if (kotlin.math.abs(currentTimeSeconds - timestamp) > MAX_EXPIRY_SECONDS) {
            return false
        }

        // 2. Validate hash starts with two zero bytes (16 bits)
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val challenge = "$cleanPhone:$deviceHash:$timestamp:$nonce"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(challenge.toByteArray(Charsets.UTF_8))

        return digest[0] == 0.toByte() && digest[1] == 0.toByte()
    }
}
