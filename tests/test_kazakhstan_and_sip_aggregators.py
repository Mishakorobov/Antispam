import tempfile
from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.api.app import app
from backend.database import get_db_session
from backend.models import Base, RegistryRangeStaging, RegistryRange, ActiveBlock
from backend.whitelist import seed_default_whitelist
from backend.normalizer import (
    normalize_incoming_phone,
    is_kazakhstan_number,
    get_country_and_code,
    format_phone,
    get_phone_code,
)
from backend.importer import score_small_b2b_sip_aggregators
from backend.sip_classifier import is_major_federal_operator, is_b2b_sip_pool

@pytest.fixture
def client_and_db():
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        db_path = Path(f.name)

    engine = create_engine(f"sqlite:///{db_path.as_posix()}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    s = TestingSessionLocal()
    seed_default_whitelist(s)
    s.close()

    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db_session] = override_get_db
    with TestClient(app) as client:
        yield client, TestingSessionLocal
    app.dependency_overrides.clear()
    engine.dispose()
    if db_path.exists():
        db_path.unlink()

def test_kazakhstan_number_detection():
    # Kazakhstan mobile ranges
    assert is_kazakhstan_number(77011234567) is True
    assert is_kazakhstan_number("+7 (701) 123-45-67") is True
    assert is_kazakhstan_number("8 777 123 45 67") is True
    assert is_kazakhstan_number("77471234567") is True
    assert is_kazakhstan_number("76011234567") is True

    # Russian ranges
    assert is_kazakhstan_number(79031234567) is False
    assert is_kazakhstan_number("+7 (495) 123-45-67") is False
    assert is_kazakhstan_number("88002000000") is False
    assert is_kazakhstan_number("78121234567") is False

def test_country_routing_and_codes():
    assert get_country_and_code(77011234567) == ("KZ", 701)
    assert get_country_and_code(77771234567) == ("KZ", 777)
    assert get_country_and_code(79031234567) == ("RU", 903)
    assert get_country_and_code(74951234567) == ("RU", 495)
    assert get_country_and_code(78001234567) == ("RU", 800)
    assert get_country_and_code("invalid") == ("UNKNOWN", None)

def test_phone_formatting():
    assert format_phone(77011234567) == "+7 (701) 123-45-67 (Казахстан)"
    assert format_phone("+77771234567") == "+7 (777) 123-45-67 (Казахстан)"
    assert format_phone(79031234567) == "+7 (903) 123-45-67"
    assert format_phone(74951234567) == "+7 (495) 123-45-67"

def test_score_small_b2b_sip_aggregators(client_and_db):
    _, session_factory = client_and_db
    session = session_factory()
    try:
        # Staging: small local aggregator (< 50,000 capacity)
        small_agg = RegistryRangeStaging(
            code_abc_def=495,
            range_start=74951110000,
            range_end=74951114999,
            capacity=5000,
            operator_name="ООО СпецТелекомТранзит",
            operator_clean="СпецТелекомТранзит",
            region="г. Москва",
            inn="7701999888",
            is_b2b_sip=False,
        )
        # Staging: federal carrier with small slice in test
        federal = RegistryRangeStaging(
            code_abc_def=916,
            range_start=79161230000,
            range_end=79161239999,
            capacity=10000,
            operator_name='ПАО "Мобильные ТелеСистемы"',
            operator_clean="Мобильные ТелеСистемы",
            region="г. Москва",
            inn="7740000076",
            is_b2b_sip=False,
        )
        session.add_all([small_agg, federal])
        session.commit()

        # Run aggregator scoring
        score_small_b2b_sip_aggregators(session)

        # Re-query
        res_small = session.query(RegistryRangeStaging).filter(RegistryRangeStaging.inn == "7701999888").first()
        res_federal = session.query(RegistryRangeStaging).filter(RegistryRangeStaging.inn == "7740000076").first()

        assert res_small.is_b2b_sip is True
        assert res_federal.is_b2b_sip is False
    finally:
        session.close()

def test_api_phone_info_endpoint(client_and_db):
    client, _ = client_and_db
    # Query Kazakhstan phone
    res_kz = client.get("/api/v1/phone/+77011234567/info")
    assert res_kz.status_code == 200
    data_kz = res_kz.json()
    assert data_kz["country"] == "KZ"
    assert data_kz["geo"] == "Казахстан"
    assert data_kz["is_foreign_plus7"] is True
    assert data_kz["is_blocked"] is False

    # Query Russian clean phone
    res_ru = client.get("/api/v1/phone/89031234567/info")
    assert res_ru.status_code == 200
    data_ru = res_ru.json()
    assert data_ru["country"] == "RU"
    assert data_ru["geo"] == "Россия"
    assert data_ru["is_foreign_plus7"] is False

def test_api_report_returns_geo_kz(client_and_db):
    client, _ = client_and_db
    res = client.post(
        "/api/v1/report",
        json={
            "phone_number": "+7 (701) 555-44-33",
            "device_hash": "a1b2c3d4e5f60718293a4b5c6d7e8f90",
            "category": "SPAM"
        }
    )
    assert res.status_code == 200
    data = res.json()
    assert data["country"] == "KZ"
    assert data["geo"] == "Казахстан"
