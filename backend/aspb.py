"""
AntiSpam Protocol Binary (ASPB) Serializer & Parser Module.
Transfers sorted int64 intervals in compact big-endian binary format,
providing ultra-low serialization overhead and 6x-8x size reduction.
"""

import json
import struct
from typing import Any
from sqlalchemy.orm import Session
from backend.models import ActiveBlock
from backend.clustering import get_current_version

CATEGORY_MAP = {
    "SPAM": 1,
    "SURVEY": 2,
    "FINANCIAL": 3,
    "SPOOFING": 4,
    "SILENT": 5,
    "SURVEY_VCIOM": 2,
    "VIP_MODE": 6,
    "FOREIGN_PLUS7": 7,
}

REVERSE_CATEGORY_MAP = {
    1: "SPAM",
    2: "SURVEY",
    3: "FINANCIAL",
    4: "SPOOFING",
    5: "SILENT",
    6: "VIP_MODE",
    7: "FOREIGN_PLUS7",
}

def build_aspb_payload(
    current_ver: int,
    is_full_sync: bool,
    has_more: bool,
    added_records: list[tuple[int, int, int, int]],  # (start: Q, end: Q, category_byte: B, version: I)
    removed_records: list[tuple[int, int, int]] = None,  # (start: Q, end: Q, version: I)
    dynamic_config: dict[str, Any] | None = None,
) -> bytes:
    """
    Serialize records into ASPB Protocol binary format:
    Header (16 bytes): 'ASPB', proto_version(H=1), current_ver(I), flags(H), dynamic_config_len(I)=0
    Flags: bit 0: is_full_sync, bit 1: has_more, bit 2: has_dynamic_config
    Body:
      [dynamic_config JSON (N bytes)] (if dynamic_config_len > 0)
      Added: count(I) + list of (start:Q, end:Q, cat:B, ver:I) [21 bytes each]
      Removed: count(I) + list of (start:Q, end:Q, ver:I) [20 bytes each]
    """
    if removed_records is None:
        removed_records = []

    config_bytes = b""
    if dynamic_config:
        config_bytes = json.dumps(dynamic_config, separators=(',', ':'), ensure_ascii=False).encode('utf-8')
    dynamic_config_len = len(config_bytes)

    flags = (1 if is_full_sync else 0) | (2 if has_more else 0) | (4 if dynamic_config_len > 0 else 0)
    header = struct.pack(">4sHIHI", b"ASPB", 1, current_ver, flags, dynamic_config_len)

    # Added chunk
    added_bytes = bytearray(struct.pack(">I", len(added_records)))
    for r_start, r_end, cat, ver in added_records:
        added_bytes.extend(struct.pack(">QQBI", r_start, r_end, cat, ver))

    # Removed chunk
    removed_bytes = bytearray(struct.pack(">I", len(removed_records)))
    for r_start, r_end, ver in removed_records:
        removed_bytes.extend(struct.pack(">QQI", r_start, r_end, ver))

    return header + config_bytes + bytes(added_bytes) + bytes(removed_bytes)

def generate_aspb_snapshot(session: Session, limit: int = 500_000) -> bytes:
    """
    Generate a full-sync binary snapshot containing all currently active blocks.
    Suitable for CDN distribution (GitHub Releases, jsDelivr, Cloudflare CDN).
    """
    current_ver = get_current_version(session)
    active_blocks = (
        session.query(ActiveBlock)
        .filter(ActiveBlock.status == "ACTIVE")
        .order_by(ActiveBlock.range_start.asc())
        .limit(limit)
        .all()
    )

    has_more = len(active_blocks) == limit
    added_records = [
        (
            b.range_start,
            b.range_end,
            CATEGORY_MAP.get(b.category, 1),
            b.version or current_ver,
        )
        for b in active_blocks
    ]

    return build_aspb_payload(
        current_ver=current_ver,
        is_full_sync=True,
        has_more=has_more,
        added_records=added_records,
        removed_records=[],
    )

def parse_aspb_payload(data: bytes) -> dict[str, Any]:
    """
    Parse an ASPB binary payload back into structured Python dictionaries.
    Used for testing, telemetry, and debugging.
    """
    if len(data) < 16:
        raise ValueError(f"Payload too short for ASPB header: {len(data)} bytes")

    magic, proto_ver, current_ver, flags, dynamic_config_len = struct.unpack(">4sHIHI", data[:16])
    if magic != b"ASPB":
        raise ValueError(f"Invalid ASPB magic header: {magic!r}")

    is_full_sync = bool(flags & 1)
    has_more = bool(flags & 2)

    offset = 16
    signature_hex = None
    if proto_ver == 2:
        if len(data) < 80:
            raise ValueError(f"Payload too short for signed ASPB v2 header: {len(data)} bytes (minimum 80)")
        signature_hex = data[16:80].hex()
        offset = 80

    dynamic_config = None
    if dynamic_config_len > 0:
        if len(data) < offset + dynamic_config_len:
            raise ValueError(f"Payload too short for dynamic config JSON: {len(data)} bytes")
        config_bytes = data[offset : offset + dynamic_config_len]
        offset += dynamic_config_len
        try:
            dynamic_config = json.loads(config_bytes.decode("utf-8"))
        except Exception as e:
            raise ValueError(f"Invalid dynamic config JSON: {e}")

    added_count = struct.unpack(">I", data[offset:offset + 4])[0]
    offset += 4

    added = []
    for _ in range(added_count):
        r_start, r_end, cat_b, ver = struct.unpack(">QQBI", data[offset:offset + 21])
        offset += 21
        added.append({
            "range_start": r_start,
            "range_end": r_end,
            "category": REVERSE_CATEGORY_MAP.get(cat_b, "SPAM"),
            "category_byte": cat_b,
            "version": ver,
        })

    removed_count = struct.unpack(">I", data[offset:offset + 4])[0]
    offset += 4

    removed = []
    for _ in range(removed_count):
        r_start, r_end, ver = struct.unpack(">QQI", data[offset:offset + 20])
        offset += 20
        removed.append({
            "range_start": r_start,
            "range_end": r_end,
            "version": ver,
        })

    return {
        "magic": magic.decode("ascii"),
        "proto_version": proto_ver,
        "current_version": current_ver,
        "is_full_sync": is_full_sync,
        "has_more": has_more,
        "signature": signature_hex,
        "dynamic_config": dynamic_config,
        "added": added,
        "removed": removed,
    }
