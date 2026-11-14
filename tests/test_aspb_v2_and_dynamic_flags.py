import json
import pytest
from backend.crypto_signer import (
    generate_ed25519_keypair,
    sign_aspb_payload,
    verify_aspb_payload,
)
from backend.aspb import build_aspb_payload, parse_aspb_payload


def test_aspb_v2_with_dynamic_config_roundtrip():
    priv, pub = generate_ed25519_keypair()

    dynamic_config = {
        "new_spyware_packages": ["ru.superapp.tracker", "com.hidden.spy"],
        "dynamic_cdn_mirrors": [
            "https://cdn.mirror1.org/pools.aspb",
            "https://cdn.mirror2.org/pools.aspb",
        ],
        "antifraud_sms_keywords": ["код для входа", "пароль от личного кабинета"],
        "foreign_spoofing_prefixes": ["76", "77", "7940"],
    }

    records = [
        (79001000000, 79001000099, 1, 100),
        (79998000000, 79998000100, 3, 101),
    ]

    # 1. Build payload with dynamic config
    payload_v1 = build_aspb_payload(
        current_ver=105,
        is_full_sync=True,
        has_more=False,
        added_records=records,
        removed_records=[],
        dynamic_config=dynamic_config,
    )

    # 2. Sign payload -> ASPB v2
    signed_v2 = sign_aspb_payload(payload_v1, priv)
    assert signed_v2[:4] == b"ASPB"
    assert signed_v2[4:6] == b"\x00\x02"  # proto_version 2

    # 3. Verify signature
    valid, msg = verify_aspb_payload(signed_v2, pub)
    assert valid is True
    assert "Valid Ed25519" in msg
    assert "dynamic_config_len" in msg

    # 4. Parse payload and check dynamic config
    parsed = parse_aspb_payload(signed_v2)
    assert parsed["proto_version"] == 2
    assert parsed["current_version"] == 105
    assert parsed["is_full_sync"] is True
    assert parsed["signature"] is not None
    assert len(parsed["added"]) == 2

    assert parsed["dynamic_config"] is not None
    assert parsed["dynamic_config"]["new_spyware_packages"] == ["ru.superapp.tracker", "com.hidden.spy"]
    assert "https://cdn.mirror1.org/pools.aspb" in parsed["dynamic_config"]["dynamic_cdn_mirrors"]
    assert "код для входа" in parsed["dynamic_config"]["antifraud_sms_keywords"]
    assert "7940" in parsed["dynamic_config"]["foreign_spoofing_prefixes"]


def test_dynamic_config_tampering_breaks_signature():
    priv, pub = generate_ed25519_keypair()

    dynamic_config = {
        "new_spyware_packages": ["ru.superapp.tracker"],
        "dynamic_cdn_mirrors": ["https://legit.mirror.org/pools.aspb"],
    }

    payload_v1 = build_aspb_payload(
        current_ver=200,
        is_full_sync=True,
        has_more=False,
        added_records=[(79001112233, 79001112244, 1, 200)],
        dynamic_config=dynamic_config,
    )

    signed_v2 = bytearray(sign_aspb_payload(payload_v1, priv))

    # The dynamic config JSON starts at offset 80 (after 16-byte header + 64-byte signature)
    # Tamper with the JSON payload (e.g. replace 'legit' with 'evil_')
    tampered_bytes = bytearray(signed_v2)
    json_start = 80
    tampered_bytes[json_start + 5] ^= 0xFF

    valid, msg = verify_aspb_payload(bytes(tampered_bytes), pub)
    assert valid is False
    assert "verification failed" in msg.lower()


def test_anti_rollback_protection():
    priv, pub = generate_ed25519_keypair()

    payload_v1 = build_aspb_payload(
        current_ver=50,
        is_full_sync=True,
        has_more=False,
        added_records=[(79001112233, 79001112244, 1, 50)],
    )
    signed_v2 = sign_aspb_payload(payload_v1, priv)

    # If client locally stored version is 100, version 50 is a rollback attempt!
    valid, msg = verify_aspb_payload(signed_v2, pub, local_version=100)
    assert valid is False
    assert "Anti-rollback protection" in msg
    assert "50 is older than local version 100" in msg

    # If client local version is 40 or 50, version 50 is accepted
    valid_newer, _ = verify_aspb_payload(signed_v2, pub, local_version=40)
    assert valid_newer is True

    valid_same, _ = verify_aspb_payload(signed_v2, pub, local_version=50)
    assert valid_same is True


def test_sign_aspb_payload_with_direct_dynamic_config_arg():
    priv, pub = generate_ed25519_keypair()

    payload_without_config = build_aspb_payload(
        current_ver=30,
        is_full_sync=True,
        has_more=False,
        added_records=[(79001112233, 79001112244, 1, 30)],
    )

    direct_config = {
        "foreign_spoofing_prefixes": ["77", "76"],
    }

    signed_v2 = sign_aspb_payload(payload_without_config, priv, dynamic_config=direct_config)
    valid, _ = verify_aspb_payload(signed_v2, pub)
    assert valid is True

    parsed = parse_aspb_payload(signed_v2)
    assert parsed["dynamic_config"] == direct_config
