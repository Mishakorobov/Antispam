import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.config import settings
from backend.models import Base, ActiveBlock, BlockDelta
from backend.parser import normalize_range
from backend.seeders.vciom_seeder import (
    extract_phones_from_html,
    seed_vciom_pools,
    OFFICIAL_VCIOM_SINGLE_NUMBERS,
)
from backend.sip_classifier import is_b2b_sip_pool

@pytest.fixture
def test_db_session():
    """Isolated SQLite in-memory database session."""
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def test_config_includes_abc_800():
    """Verify that the 5th Mincifry file ABC-800 is configured."""
    assert "ABC-800" in settings.registry_sources
    assert "ABC-800.csv" in settings.registry_sources["ABC-800"]

def test_registry_800_codes_normalization():
    """Verify formula for 800, 804, 809 non-geographic/toll-free codes."""
    start_800, end_800 = normalize_range(800, "1234567", "1234599")
    assert start_800 == 78001234567
    assert end_800 == 78001234599

    start_804, end_804 = normalize_range(804, "0000000", "0000099")
    assert start_804 == 78040000000
    assert end_804 == 78040000099

def test_is_b2b_sip_pool_classifies_800_codes():
    """Verify that federal 800/804/809 codes are automatically classified as B2B service pools."""
    assert is_b2b_sip_pool("Федеральный сервис", 10_000, code=800) is True
    assert is_b2b_sip_pool("Федеральный сервис", 5_000, code=804) is True
    assert is_b2b_sip_pool("Федеральный сервис", 1_000, code=809) is True
    # Standard geographic codes without known SIP provider
    assert is_b2b_sip_pool("ПАО Ростелеком", 100, code=495) is False

def test_extract_phones_from_vciom_html():
    """Verify HTML regex extractor finds Russian phone numbers."""
    html_sample = """
    <html>
        <body>
            <h1>Проверка номера интервьюера</h1>
            <p>Наши интервьюеры звонят с номеров: +7 (495) 787-17-74 и 8 (495) 662-67-10.</p>
            <p>Горячая линия: 8-800-775-87-74. Обычный текст 12345.</p>
        </body>
    </html>
    """
    extracted = extract_phones_from_html(html_sample)
    assert 74957871774 in extracted
    assert 74956626710 in extracted
    assert 78007758774 in extracted
    assert len(extracted) == 3

def test_seed_vciom_creates_active_blocks_and_deltas(test_db_session):
    """
    Verify seed_vciom_pools populates ActiveBlock with SURVEY_VCIOM,
    sets confidence_score = 100, and writes BlockDelta ADD records.
    """
    summary = seed_vciom_pools(test_db_session, fetch_web=False)

    assert summary["status"] == "SUCCESS"
    assert summary["category"] == "SURVEY_VCIOM"
    assert summary["seeded_single_numbers"] >= len(OFFICIAL_VCIOM_SINGLE_NUMBERS)
    assert summary["seeded_micro_pools"] >= 2
    assert summary["total_numbers_covered"] > 200

    # Verify ActiveBlock entries in database
    vciom_blocks = test_db_session.query(ActiveBlock).filter(ActiveBlock.category == "SURVEY_VCIOM").all()
    assert len(vciom_blocks) >= len(OFFICIAL_VCIOM_SINGLE_NUMBERS) + 2

    for b in vciom_blocks:
        assert b.confidence_score == 100
        assert b.status == "ACTIVE"
        assert "ВЦИОМ" in b.operator_name

    # Verify BlockDelta entries were written
    deltas = test_db_session.query(BlockDelta).filter(BlockDelta.category == "SURVEY_VCIOM").all()
    assert len(deltas) == len(vciom_blocks)
    assert all(d.action == "ADD" for d in deltas)
