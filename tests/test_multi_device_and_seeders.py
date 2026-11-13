import pytest
from starlette.requests import Request
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.api.routes_reports import get_client_ip
from backend.config import settings
from backend.clustering import (
    evaluate_report,
    get_or_create_device_reputation,
    handle_false_positive_report,
    create_active_block,
)
from backend.models import Base, ActiveBlock, BlockDelta, DeviceReputation

@pytest.fixture
def test_db_session():
    """Isolated SQLite in-memory database session."""
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    yield session
    session.close()

def make_mock_request(headers: dict[str, str], client_host: str = "127.0.0.1") -> Request:
    """Helper to mock a Starlette Request with specific headers and socket client."""
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/api/v1/report",
        "headers": [(k.lower().encode("latin-1"), v.encode("latin-1")) for k, v in headers.items()],
        "client": (client_host, 54321),
    }
    return Request(scope)

def test_get_client_ip_priority_cloudflare():
    """Verify CF-Connecting-IP takes precedence over X-Forwarded-For and X-Real-IP."""
    req = make_mock_request({
        "CF-Connecting-IP": "198.51.100.1",
        "X-Forwarded-For": "203.0.113.5, 10.0.0.1",
        "X-Real-IP": "10.0.0.1",
    })
    assert get_client_ip(req) == "198.51.100.1"

def test_get_client_ip_x_forwarded_for_first_client():
    """Verify X-Forwarded-For extracts the first client IP in the proxy chain and strips port."""
    req = make_mock_request({
        "X-Forwarded-For": "203.0.113.195:49152, 70.41.3.18, 150.172.238.178",
    })
    assert get_client_ip(req) == "203.0.113.195"

def test_get_client_ip_x_real_ip():
    """Verify X-Real-IP is used when CF-Connecting-IP and X-Forwarded-For are absent."""
    req = make_mock_request({
        "X-Real-IP": "198.51.100.42",
    })
    assert get_client_ip(req) == "198.51.100.42"

def test_get_client_ip_fallback_to_socket_client():
    """Verify direct socket IP is used when no reverse proxy headers are present."""
    req = make_mock_request({}, client_host="192.168.1.100")
    assert get_client_ip(req) == "192.168.1.100"

def test_trusted_seed_device_reputation_initialized_at_high_trust(test_db_session):
    """Verify trusted seed devices initialize with trust_score = 3.0."""
    founder_hash = "founder-device-hash-12345"
    settings.trusted_seed_devices.add(founder_hash)

    rep = get_or_create_device_reputation(founder_hash, test_db_session)
    assert rep.trust_score == 3.0
    assert rep.device_hash == founder_hash

    # Clean up setting after test
    settings.trusted_seed_devices.remove(founder_hash)

def test_trusted_seed_device_immediate_micro_100_block(test_db_session):
    """
    Verify that in Founder Mode (trusted seed devices), a single complaint
    on an ABC geographic number immediately triggers a MICRO_100 block
    without waiting for multiple independent numbers or devices.
    """
    founder_hash = "seed-founder-abc-blocker"
    settings.trusted_seed_devices.add(founder_hash)

    target_phone = 74951234567  # 495 is geographic ABC

    res = evaluate_report(
        phone_number=target_phone,
        device_hash=founder_hash,
        category="SURVEY_VCIOM",
        call_duration=12,
        session=test_db_session,
        client_ip="203.0.113.10",
    )

    assert res["status"] == "ACCEPTED"
    assert res["blocked"] is True
    assert res["block_type"] == "MICRO_100"
    assert res["range_start"] == 74951234500
    assert res["range_end"] == 74951234599

    # Clean up setting
    settings.trusted_seed_devices.remove(founder_hash)

def test_trusted_seed_device_immediate_false_positive_amnesty(test_db_session):
    """
    Verify that a trusted seed device has administrative amnesty authority:
    a single false-positive vote immediately unblocks an active pool.
    """
    founder_hash = "seed-founder-amnesty-admin"
    settings.trusted_seed_devices.add(founder_hash)

    # 1. Create an active block
    block, _ = create_active_block(
        range_start=74959990000,
        range_end=74959990099,
        block_type="MICRO_100",
        operator_name="Тестовый оператор",
        category="SPAM",
        session=test_db_session,
    )
    assert block.status == "ACTIVE"

    # 2. Founder reports false positive
    res = handle_false_positive_report(
        phone_number=74959990050,
        device_hash=founder_hash,
        reason="FOUNDER_OVERRIDE_LEGITIMATE",
        session=test_db_session,
        client_ip="198.51.100.99",
    )

    assert res["status"] == "AMNESTY_APPLIED"
    assert res["unblocked"] is True

    # Check block status in DB
    updated_block = test_db_session.query(ActiveBlock).filter(ActiveBlock.id == block.id).first()
    assert updated_block.status == "EXPIRED"

    # Clean up setting
    settings.trusted_seed_devices.remove(founder_hash)
