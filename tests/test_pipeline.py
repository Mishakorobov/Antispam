import tempfile
from pathlib import Path
from backend.importer import run_sync_pipeline, lookup_phone_number
from backend.parser import parse_registry_csv

SYNTHETIC_CSV_CONTENT = """АВС/ DEF;От;До;Емкость;Оператор;Регион;Территория ГАР;ИНН
495;1234500;1234599;100;ООО "Манго Телеком";Москва;г. Москва;7701234567
495;2000000;2099999;100000;ПАО "Ростелеком";Москва;г. Москва;7707049388
903;5000000;5000099;100;ПАО "Вымпел-Коммуникации";-;г. Москва;7713076301
301;2190000;2190499;500;АО "МТТ";-;Республика Бурятия;7705017253
"""

def test_parse_synthetic_csv():
    with tempfile.NamedTemporaryFile("w", encoding="utf-8-sig", delete=False) as f:
        f.write(SYNTHETIC_CSV_CONTENT)
        tmp_csv = Path(f.name)

    try:
        records = list(parse_registry_csv(tmp_csv))
        assert len(records) == 4

        # Record 0: Mango Telecom (B2B SIP)
        r0 = records[0]
        assert r0["code_abc_def"] == 495
        assert r0["range_start"] == 74951234500
        assert r0["range_end"] == 74951234599
        assert r0["capacity"] == 100
        assert r0["is_b2b_sip"] is True
        assert r0["operator_clean"] == "Манго Телеком"

        # Record 1: Rostelecom (Large pool)
        r1 = records[1]
        assert r1["range_start"] == 74952000000
        assert r1["range_end"] == 74952099999
        assert r1["capacity"] == 100_000
        assert r1["is_b2b_sip"] is False

        # Record 3: MTT in Buryatia
        r3 = records[3]
        assert r3["range_start"] == 73012190000
        assert r3["range_end"] == 73012190499
        assert r3["region"] == "Республика Бурятия"
        assert r3["is_b2b_sip"] is True
    finally:
        tmp_csv.unlink()

def test_sync_pipeline_and_lookup():
    # Write a test CSV with enough rows or test file
    content = SYNTHETIC_CSV_CONTENT + "\n".join(
        f"999;{i:07d};{i:07d};1;Тест Оператор;Москва;г. Москва;1234567890" for i in range(150)
    )
    with tempfile.NamedTemporaryFile("w", encoding="utf-8-sig", delete=False) as f:
        f.write(content)
        tmp_csv = Path(f.name)

    try:
        # Run pipeline with the synthetic file
        res = run_sync_pipeline(specific_files={"ABC-Test": tmp_csv})
        assert res["status"] == "SUCCESS"
        assert res["total_records"] >= 154

        # Test lookup inside Mango Telecom pool
        res_mango = lookup_phone_number(74951234550)
        assert res_mango is not None
        assert res_mango["operator_clean"] == "Манго Телеком"
        assert res_mango["is_b2b_sip"] is True

        # Test lookup inside Rostelecom pool
        res_rt = lookup_phone_number(74952055555)
        assert res_rt is not None
        assert res_rt["operator_clean"] == "Ростелеком"
        assert res_rt["is_b2b_sip"] is False

        # Test lookup for unknown number
        res_none = lookup_phone_number(79999999999)
        assert res_none is None
    finally:
        tmp_csv.unlink()
