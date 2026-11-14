package ru.antispam.app.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.antispam.app.data.remote.PowGenerator

class PowGeneratorTest {

    @Test
    fun `solve produces valid proof of work with two zero bytes`() {
        val phone = "+7 (999) 111-22-33"
        val deviceHash = "device_test_hash_abcdef123456"
        val now = 1700000000L

        val solution = PowGenerator.solve(phone, deviceHash, now)

        assertTrue("Nonce should be non-negative", solution.nonce >= 0)
        assertTrue("Hash should start with 0000", solution.hashHex.startsWith("0000"))

        val isValid = PowGenerator.verify(
            phoneNumber = phone,
            deviceHash = deviceHash,
            timestamp = solution.timestamp,
            nonce = solution.nonce,
            currentTimeSeconds = now
        )
        assertTrue("Verification should succeed for freshly solved PoW", isValid)
    }

    @Test
    fun `verify accepts normalized equivalent phone format`() {
        val rawPhone = "+7 (999) 123-45-67"
        val digitsPhone = "79991234567"
        val deviceHash = "device_xyz_987654321"
        val ts = 1700000500L

        val solution = PowGenerator.solve(rawPhone, deviceHash, ts)

        val isValid = PowGenerator.verify(
            phoneNumber = digitsPhone,
            deviceHash = deviceHash,
            timestamp = ts,
            nonce = solution.nonce,
            currentTimeSeconds = ts
        )
        assertTrue("Phone normalization should make both formats match", isValid)
    }

    @Test
    fun `verify rejects expired timestamp outside 10 minute window`() {
        val phone = "79991112233"
        val deviceHash = "device_xyz"
        val ts = 1700000000L

        val solution = PowGenerator.solve(phone, deviceHash, ts)

        // 601 seconds later -> expired
        val isExpired = PowGenerator.verify(
            phoneNumber = phone,
            deviceHash = deviceHash,
            timestamp = ts,
            nonce = solution.nonce,
            currentTimeSeconds = ts + 601
        )
        assertFalse("PoW should expire after 600 seconds", isExpired)

        // 601 seconds earlier -> invalid clock skew
        val isPremature = PowGenerator.verify(
            phoneNumber = phone,
            deviceHash = deviceHash,
            timestamp = ts,
            nonce = solution.nonce,
            currentTimeSeconds = ts - 601
        )
        assertFalse("PoW should reject timestamp skewed more than 600s into future", isPremature)
    }

    @Test
    fun `verify rejects corrupted nonce`() {
        val phone = "79991112233"
        val deviceHash = "device_xyz"
        val ts = 1700000000L

        val solution = PowGenerator.solve(phone, deviceHash, ts)

        val isInvalid = PowGenerator.verify(
            phoneNumber = phone,
            deviceHash = deviceHash,
            timestamp = ts,
            nonce = solution.nonce + 1, // Corrupted nonce
            currentTimeSeconds = ts
        )
        assertFalse("PoW with wrong nonce must fail", isInvalid)
    }

    @Test
    fun `verify rejects mismatched device hash`() {
        val phone = "79991112233"
        val deviceHash = "device_xyz"
        val ts = 1700000000L

        val solution = PowGenerator.solve(phone, deviceHash, ts)

        val isMismatched = PowGenerator.verify(
            phoneNumber = phone,
            deviceHash = "different_device_hash",
            timestamp = ts,
            nonce = solution.nonce,
            currentTimeSeconds = ts
        )
        assertFalse("PoW must be bound to deviceHash", isMismatched)
    }
}
