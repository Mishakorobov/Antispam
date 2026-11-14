"""
Cryptographic Signer and Verifier for ASPB (AntiSpam Protocol Binary).
Uses asymmetric Ed25519 (RFC 8032) signatures to prevent MITM tampering,
downgrade attacks, and unauthorized database injection.
"""

import json
import os
import struct
from pathlib import Path
from typing import Any, Optional, Tuple
from cryptography.hazmat.primitives.asymmetric import ed25519
from cryptography.hazmat.primitives import serialization

# Master public key embedded in the AntiSpam client
DEFAULT_PUBLIC_KEY_HEX = "24ebaac70ef49eaf434e4bd0d62403000e0cb1e9f31d453cdc9be3dc38309916"


def generate_ed25519_keypair() -> Tuple[bytes, bytes]:
    """
    Generate an Ed25519 asymmetric keypair.
    Returns:
        (private_key_bytes, public_key_bytes): 32 bytes each (raw format).
    """
    priv = ed25519.Ed25519PrivateKey.generate()
    pub = priv.public_key()
    priv_bytes = priv.private_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PrivateFormat.Raw,
        encryption_algorithm=serialization.NoEncryption(),
    )
    pub_bytes = pub.public_bytes(
        encoding=serialization.Encoding.Raw,
        format=serialization.PublicFormat.Raw,
    )
    return priv_bytes, pub_bytes


def load_private_key(key_bytes_or_hex: bytes | str) -> ed25519.Ed25519PrivateKey:
    """Load an Ed25519 private key from 32-byte raw bytes or 64-char hex string."""
    if isinstance(key_bytes_or_hex, str):
        key_bytes = bytes.fromhex(key_bytes_or_hex.strip())
    else:
        key_bytes = key_bytes_or_hex

    if len(key_bytes) != 32:
        raise ValueError(f"Ed25519 private key must be exactly 32 bytes, got {len(key_bytes)}")

    return ed25519.Ed25519PrivateKey.from_private_bytes(key_bytes)


def load_public_key(key_bytes_or_hex: bytes | str) -> ed25519.Ed25519PublicKey:
    """Load an Ed25519 public key from 32-byte raw bytes or 64-char hex string."""
    if isinstance(key_bytes_or_hex, str):
        key_bytes = bytes.fromhex(key_bytes_or_hex.strip())
    else:
        key_bytes = key_bytes_or_hex

    if len(key_bytes) != 32:
        raise ValueError(f"Ed25519 public key must be exactly 32 bytes, got {len(key_bytes)}")

    return ed25519.Ed25519PublicKey.from_public_bytes(key_bytes)


def sign_aspb_payload(
    payload: bytes,
    private_key_bytes_or_hex: bytes | str,
    dynamic_config: Optional[dict[str, Any]] = None,
) -> bytes:
    """
    Sign an ASPB binary payload using Ed25519.
    Produces ASPB v2 format:
      [0..15]: 16-byte header with proto_version=2, dynamic_config_len
      [16..79]: 64-byte Ed25519 signature of the body (dynamic_config + deltas)
      [80..]: body (dynamic_config JSON + added + removed records)
    """
    if len(payload) < 16:
        raise ValueError(f"Payload too short for ASPB header: {len(payload)} bytes")

    magic, proto_ver, current_ver, flags, dynamic_config_len = struct.unpack(">4sHIHI", payload[:16])
    if magic != b"ASPB":
        raise ValueError(f"Invalid ASPB magic: {magic!r}")

    # If dynamic_config is explicitly provided and not already packed
    if dynamic_config is not None and dynamic_config_len == 0:
        config_bytes = json.dumps(dynamic_config, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
        dynamic_config_len = len(config_bytes)
        flags |= 4
        if proto_ver == 1:
            body = config_bytes + payload[16:]
        elif proto_ver == 2:
            if len(payload) < 80:
                raise ValueError(f"Invalid ASPB v2 payload length: {len(payload)} bytes")
            body = config_bytes + payload[80:]
        else:
            raise ValueError(f"Unsupported ASPB proto version: {proto_ver}")
    else:
        # Determine body offset based on existing version
        if proto_ver == 1:
            body = payload[16:]
        elif proto_ver == 2:
            if len(payload) < 80:
                raise ValueError(f"Invalid ASPB v2 payload length: {len(payload)} bytes")
            body = payload[80:]
        else:
            raise ValueError(f"Unsupported ASPB proto version: {proto_ver}")

    priv_key = load_private_key(private_key_bytes_or_hex)
    signature = priv_key.sign(body)
    if len(signature) != 64:
        raise RuntimeError(f"Unexpected signature length: {len(signature)}")

    # Construct ASPB v2 header: proto_version=2
    v2_header = struct.pack(">4sHIHI", b"ASPB", 2, current_ver, flags, dynamic_config_len)
    return v2_header + signature + body


def sign_aspb_file(
    file_path: Path,
    private_key_bytes_or_hex: bytes | str,
    dynamic_config: Optional[dict[str, Any]] = None,
) -> bytes:
    """
    Sign an ASPB file in-place and return the signed bytes.
    """
    path = Path(file_path)
    if not path.is_file():
        raise FileNotFoundError(f"ASPB file not found: {file_path}")

    raw_data = path.read_bytes()
    signed_data = sign_aspb_payload(raw_data, private_key_bytes_or_hex, dynamic_config=dynamic_config)
    path.write_bytes(signed_data)
    return signed_data


def verify_aspb_payload(
    payload: bytes,
    public_key_bytes_or_hex: bytes | str = DEFAULT_PUBLIC_KEY_HEX,
    local_version: int = 0,
) -> Tuple[bool, str]:
    """
    Verify Ed25519 signature of an ASPB v2 payload, with anti-rollback version check.
    Returns:
        (is_valid, message)
    """
    if len(payload) < 80:
        return False, f"Payload too short for ASPB v2: {len(payload)} bytes (minimum 80 required)"

    magic, proto_ver, current_ver, flags, dynamic_config_len = struct.unpack(">4sHIHI", payload[:16])
    if magic != b"ASPB":
        return False, f"Invalid magic header: {magic!r}"

    if proto_ver != 2:
        return False, f"Not an ASPB v2 signed file (proto_version={proto_ver})"

    # Anti-rollback protection: prevent attackers from downgrading dynamic rules or snapshots
    if local_version > 0 and current_ver < local_version:
        return False, f"Anti-rollback protection: incoming version {current_ver} is older than local version {local_version}"

    signature = payload[16:80]
    body = payload[80:]

    try:
        pub_key = load_public_key(public_key_bytes_or_hex)
        pub_key.verify(signature, body)
        return True, f"Valid Ed25519 signature for ASPB v2 (current_ver={current_ver}, body_len={len(body)}, dynamic_config_len={dynamic_config_len})"
    except Exception as e:
        return False, f"Signature verification failed: {e}"


def verify_aspb_file(
    file_path: Path,
    public_key_bytes_or_hex: bytes | str = DEFAULT_PUBLIC_KEY_HEX,
    local_version: int = 0,
) -> Tuple[bool, str]:
    """Verify an ASPB file on disk with anti-rollback protection."""
    path = Path(file_path)
    if not path.is_file():
        return False, f"File not found: {file_path}"

    return verify_aspb_payload(path.read_bytes(), public_key_bytes_or_hex, local_version=local_version)
    return verify_aspb_payload(path.read_bytes(), public_key_bytes_or_hex)
