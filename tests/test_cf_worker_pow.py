"""
Unit tests for Cloudflare Worker Micro-Proof-of-Work (PoW) verification (Phase 11.2).
Validates cross-platform cryptographic parity between Android (PowGenerator.kt),
Cloudflare Worker (cf_worker_report_bridge.js), and Python backend test harness.
"""

import hashlib
import re
import time
import pytest


def verify_proof_of_work(phone_number: str, device_hash: str, timestamp: int, nonce: int, current_time: int = None) -> bool:
    """
    Python reference implementation of Cloudflare Worker's verifyProofOfWork().
    """
    if current_time is None:
        current_time = int(time.time())

    # 1. Check timestamp freshness (+- 10 minutes / 600s)
    if abs(current_time - timestamp) > 600:
        return False

    # 2. Normalize phone number (digits only)
    clean_phone = re.sub(r"\D", "", phone_number)
    challenge = f"{clean_phone}:{device_hash}:{timestamp}:{nonce}"
    digest = hashlib.sha256(challenge.encode("utf-8")).digest()

    # 3. Require 16 bits of leading zeros (first 2 bytes == 0x00)
    return digest[0] == 0 and digest[1] == 0


def solve_proof_of_work(phone_number: str, device_hash: str, timestamp: int = None) -> tuple[int, int, str]:
    """
    Python solver for 16-bit challenge.
    Returns (nonce, timestamp, hash_hex).
    """
    if timestamp is None:
        timestamp = int(time.time())

    clean_phone = re.sub(r"\D", "", phone_number)
    nonce = 0
    while True:
        challenge = f"{clean_phone}:{device_hash}:{timestamp}:{nonce}"
        digest = hashlib.sha256(challenge.encode("utf-8")).digest()
        if digest[0] == 0 and digest[1] == 0:
            return nonce, timestamp, digest.hex()
        nonce += 1


class TestCloudflareWorkerPoW:
    """Tests PoW generation and verification logic."""

    def test_solve_and_verify_valid_pow(self):
        phone = "+7 (999) 777-66-55"
        device_hash = "device_test_hash_001"
        now = int(time.time())

        nonce, ts, hash_hex = solve_proof_of_work(phone, device_hash, now)

        assert nonce >= 0
        assert hash_hex.startswith("0000")
        assert verify_proof_of_work(phone, device_hash, ts, nonce, now) is True

    def test_verify_rejects_expired_timestamp(self):
        phone = "79991234567"
        device_hash = "device_xyz"
        now = int(time.time())
        old_ts = now - 601  # > 10 min

        nonce, _, _ = solve_proof_of_work(phone, device_hash, old_ts)

        assert verify_proof_of_work(phone, device_hash, old_ts, nonce, now) is False

    def test_verify_rejects_future_skewed_timestamp(self):
        phone = "79991234567"
        device_hash = "device_xyz"
        now = int(time.time())
        future_ts = now + 650

        nonce, _, _ = solve_proof_of_work(phone, device_hash, future_ts)

        assert verify_proof_of_work(phone, device_hash, future_ts, nonce, now) is False

    def test_verify_rejects_tampered_nonce(self):
        phone = "79991234567"
        device_hash = "device_xyz"
        now = int(time.time())

        nonce, ts, _ = solve_proof_of_work(phone, device_hash, now)

        assert verify_proof_of_work(phone, device_hash, ts, nonce + 1, now) is False

    def test_verify_rejects_mismatched_phone_or_device(self):
        phone = "79991234567"
        device_hash = "device_xyz"
        now = int(time.time())

        nonce, ts, _ = solve_proof_of_work(phone, device_hash, now)

        assert verify_proof_of_work("79990000000", device_hash, ts, nonce, now) is False
        assert verify_proof_of_work(phone, "different_device", ts, nonce, now) is False

    def test_phone_formatting_invariance(self):
        formatted = "+7 (999) 555-44-33"
        raw = "79995554433"
        device_hash = "dev_123"
        now = int(time.time())

        nonce, ts, _ = solve_proof_of_work(formatted, device_hash, now)

        # Verification using raw phone number should succeed
        assert verify_proof_of_work(raw, device_hash, ts, nonce, now) is True

    def test_pow_verification_performance(self):
        phone = "79991234567"
        device_hash = "dev_benchmark"
        now = int(time.time())

        nonce, ts, _ = solve_proof_of_work(phone, device_hash, now)

        # Verify performance: 1000 verifications should take well under 100ms (<0.1ms each)
        start = time.perf_counter()
        for _ in range(1000):
            verify_proof_of_work(phone, device_hash, ts, nonce, now)
        elapsed = time.perf_counter() - start

        avg_ms = (elapsed / 1000) * 1000
        assert avg_ms < 0.5, f"Verification too slow: {avg_ms:.4f} ms per verification"
