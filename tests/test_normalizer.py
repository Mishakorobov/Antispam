from backend.parser import normalize_range, detect_encoding
from pathlib import Path
import tempfile

def test_normalize_range_standard():
    # 495 with 7-digit local numbers
    start, end = normalize_range(495, "1234500", "1234599")
    assert start == 74951234500
    assert end == 74951234599
    assert (end - start + 1) == 100

def test_normalize_range_with_leading_zeros():
    # Test numbers with leading zeros (e.g. 0000000 to 0009999)
    start, end = normalize_range(900, "0", "99")
    assert start == 79000000000
    assert end == 79000000099
    assert (end - start + 1) == 100

def test_normalize_range_def_code():
    # DEF-903
    start, end = normalize_range(903, "5550000", "5559999")
    assert start == 79035550000
    assert end == 79035559999
    assert (end - start + 1) == 10_000

def test_detect_encoding():
    with tempfile.NamedTemporaryFile("wb", delete=False) as f:
        # Write UTF-8 with BOM
        f.write(b"\xef\xbb\xbfCode;Start;End")
        temp_utf8_bom = Path(f.name)

    with tempfile.NamedTemporaryFile("wb", delete=False) as f:
        # Write CP1251 (Russian characters: Тест)
        f.write("Тест".encode("cp1251"))
        temp_cp1251 = Path(f.name)

    try:
        assert detect_encoding(temp_utf8_bom) == "utf-8-sig"
        assert detect_encoding(temp_cp1251) == "cp1251"
    finally:
        temp_utf8_bom.unlink()
        temp_cp1251.unlink()
