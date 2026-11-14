import tempfile
from pathlib import Path
import pytest
from backend.crypto_signer import (
    generate_ed25519_keypair,
    sign_aspb_payload,
    verify_aspb_payload,
    sign_aspb_file,
    verify_aspb_file,
    load_private_key,
    load_public_key,
)
from backend.aspb import build_aspb_payload, parse_aspb_payload


def test_ed25519_keypair_generation():
    priv, pub = generate_ed25519_keypair()
    assert len(priv) == 32
    assert len(pub) == 32

    # Load from raw bytes and from hex
    priv_obj = load_private_key(priv)
    pub_obj = load_public_key(pub.hex())
    assert priv_obj is not None
    assert pub_obj is not None


def test_sign_and_verify_aspb_v2_payload():
    priv, pub = generate_ed25519_keypair()

    # Create dummy v1 payload
    records = [
        (79001000000, 79001000099, 1, 100),
        (79998000000, 79998000100, 3, 101),
    ]
    raw_v1 = build_aspb_payload(
        current_ver=42,
        is_full_sync=True,
        has_more=False,
        added_records=records,
        removed_records=[],
    )
    assert raw_v1[:4] == b"ASPB"
    assert raw_v1[4:6] == b"\x00\x01"  # proto_version 1

    # Sign payload -> ASPB v2
    signed_v2 = sign_aspb_payload(raw_v1, priv)
    assert signed_v2[:4] == b"ASPB"
    assert signed_v2[4:6] == b"\x00\x02"  # proto_version 2
    assert len(signed_v2) == len(raw_v1) + 64  # +64 bytes signature

    # Verify signature
    valid, msg = verify_aspb_payload(signed_v2, pub)
    assert valid is True
    assert "Valid Ed25519" in msg

    # Parse with backend.aspb parser
    parsed = parse_aspb_payload(signed_v2)
    assert parsed["proto_version"] == 2
    assert parsed["current_version"] == 42
    assert parsed["is_full_sync"] is True
    assert len(parsed["added"]) == 2
    assert parsed["signature"] is not None
    assert len(parsed["signature"]) == 128  # 64 bytes = 128 hex chars


def test_tampered_payload_fails_verification():
    priv, pub = generate_ed25519_keypair()

    raw_v1 = build_aspb_payload(
        current_ver=1,
        is_full_sync=True,
        has_more=False,
        added_records=[(79001112233, 79001112244, 1, 1)],
    )
    signed_v2 = bytearray(sign_aspb_payload(raw_v1, priv))

    # Tamper one byte in the body (after byte 80)
    signed_v2[-1] ^= 0xFF

    valid, msg = verify_aspb_payload(bytes(signed_v2), pub)
    assert valid is False
    assert "verification failed" in msg.lower()


def test_wrong_public_key_fails():
    priv1, pub1 = generate_ed25519_keypair()
    _, pub2 = generate_ed25519_keypair()

    raw_v1 = build_aspb_payload(1, True, False, [(79000000000, 79000000010, 1, 1)])
    signed_v2 = sign_aspb_payload(raw_v1, priv1)

    valid, msg = verify_aspb_payload(signed_v2, pub2)
    assert valid is False


def test_sign_aspb_file_inplace():
    priv, pub = generate_ed25519_keypair()

    with tempfile.NamedTemporaryFile(suffix=".aspb", delete=False) as f:
        file_path = Path(f.name)

    try:
        raw_v1 = build_aspb_payload(10, True, False, [(79002223344, 79002223355, 2, 10)])
        file_path.write_bytes(raw_v1)

        # In-place sign
        sign_aspb_file(file_path, priv)

        # Verify
        valid, msg = verify_aspb_file(file_path, pub)
        assert valid is True
    finally:
        if file_path.exists():
            file_path.unlink()
