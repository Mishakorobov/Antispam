import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.models import Base
from backend.whitelist import (
    seed_default_whitelist,
    is_number_whitelisted,
    set_whitelist_group_status,
    get_whitelist_groups_summary,
)

@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    seed_default_whitelist(session)
    yield session
    session.close()

def test_whitelist_initial_seeding(db_session):
    groups = get_whitelist_groups_summary(db_session)
    group_keys = {g["group_key"] for g in groups}
    assert "TBANK" in group_keys
    assert "SBERBANK" in group_keys
    assert "EMERGENCY" in group_keys
    assert "GOV" in group_keys

def test_whitelist_protection_active(db_session):
    # T-Bank hotline: 8-800-555-77-78 -> 78005557778
    is_wl, entry = is_number_whitelisted(78005557778, db_session)
    assert is_wl is True
    assert entry.group_key == "TBANK"

    # Sberbank 900 -> 70000000900
    is_wl, entry = is_number_whitelisted(70000000900, db_session)
    assert is_wl is True
    assert entry.group_key == "SBERBANK"

    # Emergency 112 -> 70000000112
    is_wl, entry = is_number_whitelisted(70000000112, db_session)
    assert is_wl is True

    # Random spam number
    is_wl, entry = is_number_whitelisted(79991234567, db_session)
    assert is_wl is False

def test_whitelist_group_toggle(db_session):
    # Initially TBANK is protected
    is_wl, _ = is_number_whitelisted(78005557778, db_session)
    assert is_wl is True

    # User toggles TBANK OFF (e.g. tired of bank calls and wants them reported)
    updated = set_whitelist_group_status("TBANK", enable=False, session=db_session)
    assert updated > 0

    # Now TBANK is no longer whitelisted!
    is_wl, entry = is_number_whitelisted(78005557778, db_session)
    assert is_wl is False
    assert entry is None

    # But Sberbank and 112 are still protected
    is_sber, _ = is_number_whitelisted(70000000900, db_session)
    assert is_sber is True

    # User toggles TBANK back ON
    set_whitelist_group_status("TBANK", enable=True, session=db_session)
    is_wl_again, _ = is_number_whitelisted(78005557778, db_session)
    assert is_wl_again is True
