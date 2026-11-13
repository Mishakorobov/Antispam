"""
Telephone Normalizer & Country Routing Module.
Handles +7 numbering plan distribution between Russia (RU) and Kazakhstan (KZ),
as well as standardizing phone numbers and ranges.
"""

from pathlib import Path
from typing import Tuple

def detect_encoding(filepath: Path) -> str:
    """Detect whether file is UTF-8 (with or without BOM) or Windows-1251."""
    with open(filepath, "rb") as f:
        sample = f.read(4096)
        if sample.startswith(b"\xef\xbb\xbf"):
            return "utf-8-sig"
        try:
            sample.decode("utf-8")
            return "utf-8"
        except UnicodeDecodeError:
            return "cp1251"

def normalize_incoming_phone(raw_phone: str | int) -> int | None:
    """
    Normalize any phone string or integer to 11-digit +7 number (7XXXXXXXXXX).
    Handles +7, 8, whitespace, dashes, and 10-digit national inputs.
    """
    if raw_phone is None:
        return None
    digits = "".join(ch for ch in str(raw_phone) if ch.isdigit())
    if len(digits) == 11 and digits.startswith("8"):
        digits = "7" + digits[1:]
    elif len(digits) == 10:
        digits = "7" + digits

    if len(digits) == 11 and digits.startswith("7"):
        return int(digits)
    return None

def get_phone_code(phone_num: int | str) -> int | None:
    """
    Extract the 3-digit ABC/DEF code from an 11-digit +7 number.
    e.g. 79031234567 -> 903, 77011234567 -> 701.
    """
    norm = normalize_incoming_phone(phone_num)
    if not norm:
        return None
    s = str(norm)
    return int(s[1:4])

def is_kazakhstan_number(phone_num: int | str) -> bool:
    """
    Check if phone belongs to the foreign +7 segment (Kazakhstan).
    In the shared +7 world zone:
    - Kazakhstan uses codes 600..799 (e.g. 700-708, 710-729, 747, 750, 751, 760-764, 771-778).
    - Russia uses codes 300..599 (geographic) and 800..999 (geographic, mobile, toll-free).
    """
    code = get_phone_code(phone_num)
    if code is None:
        return False
    return 600 <= code <= 799

def get_country_and_code(phone_num: int | str) -> Tuple[str, int | None]:
    """
    Determine country code ('KZ', 'RU', or 'UNKNOWN') and 3-digit area/def code.
    """
    code = get_phone_code(phone_num)
    if code is None:
        return ("UNKNOWN", None)
    if 600 <= code <= 799:
        return ("KZ", code)
    if (300 <= code <= 599) or (800 <= code <= 999):
        return ("RU", code)
    return ("UNKNOWN", code)

def format_phone(phone_num: int | str) -> str:
    """
    Format phone number with human-readable country tag:
    - +7 (701) 123-45-67 (Казахстан)
    - +7 (903) 123-45-67
    """
    norm = normalize_incoming_phone(phone_num)
    if not norm:
        return str(phone_num)
    s = str(norm)
    formatted = f"+7 ({s[1:4]}) {s[4:7]}-{s[7:9]}-{s[9:11]}"
    if is_kazakhstan_number(norm):
        return f"{formatted} (Казахстан)"
    return formatted

def normalize_range(code_val: int, start_str: str, end_str: str) -> Tuple[int, int]:
    """
    Convert 3-digit code and 7-digit local numbers to 11-digit BIGINT (7XXXXXXXXXX).
    start_str and end_str are padded with leading zeroes up to 7 characters.
    """
    start_clean = start_str.strip().zfill(7)
    end_clean = end_str.strip().zfill(7)

    start_int = int(start_clean)
    end_int = int(end_clean)

    # Formula: 70_000_000_000 + code * 10_000_000 + local_num
    base = 70_000_000_000 + (code_val * 10_000_000)
    range_start = base + start_int
    range_end = base + end_int
    return range_start, range_end
