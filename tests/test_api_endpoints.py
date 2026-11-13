import tempfile
from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.api.app import app
from backend.database import get_db_session
from backend.models import Base
from backend.whitelist import seed_default_whitelist

@pytest.fixture
def client_and_db():
    # Use temporary file so all sessions in test share the exact same DB
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        db_path = Path(f.name)

    engine = create_engine(f"sqlite:///{db_path.as_posix()}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    # Seed default whitelist
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
        yield client
    app.dependency_overrides.clear()
    engine.dispose()
    if db_path.exists():
        db_path.unlink()

def test_api_health(client_and_db):
    client = client_and_db
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok", "service": "antispam-backend"}

def test_api_whitelist_protection_and_toggle(client_and_db):
    client = client_and_db
    valid_device_hash = "a" * 32  # 32-character valid hash

    # 1. Report on T-Bank hotline (78005557778) while protected
    resp = client.post("/api/v1/report", json={
        "phone_number": "78005557778",
        "device_hash": valid_device_hash,
        "category": "FINANCIAL",
        "call_duration_seconds": 15
    })
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "IGNORED_WHITELIST"
    assert data["blocked"] is False

    # 2a. Attempt toggle without admin key -> 403 Forbidden (SEC-3 verification)
    unauthorized_resp = client.post("/api/v1/whitelist/groups/TBANK/toggle", json={"enable": False})
    assert unauthorized_resp.status_code == 403

    # 2b. Attempt toggle with invalid admin key -> 403 Forbidden
    invalid_resp = client.post(
        "/api/v1/whitelist/groups/TBANK/toggle",
        headers={"X-Admin-Api-Key": "wrong-secret"},
        json={"enable": False}
    )
    assert invalid_resp.status_code == 403

    # 2c. Authorized Admin toggles TBANK group OFF with valid X-Admin-Api-Key
    from backend.config import settings
    toggle_resp = client.post(
        "/api/v1/whitelist/groups/TBANK/toggle",
        headers={"X-Admin-Api-Key": settings.admin_api_key},
        json={"enable": False}
    )
    assert toggle_resp.status_code == 200
    assert toggle_resp.json()["is_enabled"] is False

    # 3. Report again on T-Bank hotline -> now ACCEPTED!
    resp2 = client.post("/api/v1/report", json={
        "phone_number": "78005557778",
        "device_hash": valid_device_hash,
        "category": "FINANCIAL",
        "call_duration_seconds": 15
    })
    assert resp2.status_code == 200
    data2 = resp2.json()
    assert data2["status"] == "ACCEPTED"

def test_api_batch_reports_and_delta_sync(client_and_db):
    client = client_and_db
    dev1 = "1" * 32
    dev2 = "2" * 32
    dev3 = "3" * 32

    # Submit 3 reports on bucket 79039999900..99 from 3 devices to trigger block
    batch_payload = {
        "device_hash": dev1,
        "reports": [
            {"phone_number": "79039999911", "category": "SPAM"},
        ]
    }
    r1 = client.post("/api/v1/reports/batch", json=batch_payload)
    assert r1.status_code == 200
    assert r1.json()["accepted"] == 1

    # Dev 2 reports different number in bucket
    client.post("/api/v1/report", json={
        "phone_number": "79039999922",
        "device_hash": dev2,
        "category": "SPAM"
    })
    # Dev 3 reports number in bucket -> triggers block!
    r3 = client.post("/api/v1/report", json={
        "phone_number": "79039999922",
        "device_hash": dev3,
        "category": "SPAM"
    })
    assert r3.status_code == 200
    assert r3.json()["blocked"] is True

    # Trigger another report to increment version so cur_v >= 2
    dev4 = "4" * 32
    dev5 = "5" * 32
    dev6 = "6" * 32
    client.post("/api/v1/report", json={"phone_number": "79038888801", "device_hash": dev4, "category": "SPAM"})
    client.post("/api/v1/report", json={"phone_number": "79038888802", "device_hash": dev5, "category": "SPAM"})
    r6 = client.post("/api/v1/report", json={"phone_number": "79038888802", "device_hash": dev6, "category": "SPAM"})
    assert r6.json()["blocked"] is True

    # Check /api/v1/pools?since=0
    pools_resp = client.get("/api/v1/pools?since=0")
    assert pools_resp.status_code == 200
    pdata = pools_resp.json()
    assert pdata["full_sync_required"] is True
    assert len(pdata["added"]) >= 2

    # Check incremental delta with since=1
    cur_v = pdata["current_version"]
    assert cur_v >= 2
    delta_resp = client.get("/api/v1/pools?since=1")
    assert delta_resp.status_code == 200
    ddata = delta_resp.json()
    assert ddata["full_sync_required"] is False
    assert len(ddata["added"]) >= 1

    # Check HTTP 304 Not Modified when since == current_version
    not_modified_resp = client.get(f"/api/v1/pools?since={cur_v}")
    assert not_modified_resp.status_code == 304
    assert len(not_modified_resp.content) == 0

def test_api_public_stats(client_and_db):
    client = client_and_db
    resp = client.get("/api/v1/stats/public")
    assert resp.status_code == 200
    data = resp.json()
    assert "active_blocked_pools" in data
    assert "delta_version" in data
    assert "whitelist_groups_count" in data

def test_api_false_positive_and_amnesty(client_and_db):
    client = client_and_db
    dev_a = "a" * 32
    dev_b = "b" * 32
    dev_c = "c" * 32

    # Block a single number on DEF code 903
    client.post("/api/v1/report", json={"phone_number": "79037771122", "device_hash": dev_a, "category": "SPAM"})
    client.post("/api/v1/report", json={"phone_number": "79037771122", "device_hash": dev_b, "category": "SPAM"})
    r_block = client.post("/api/v1/report", json={"phone_number": "79037771122", "device_hash": dev_c, "category": "SPAM"})
    assert r_block.status_code == 200
    assert r_block.json()["blocked"] is True

    # 1. First user makes outgoing call -> submits false positive (records vote, anti-sabotage)
    fp_resp1 = client.post(
        "/api/v1/report/false-positive",
        json={
            "phone_number": "79037771122",
            "device_hash": "f" * 32,
            "reason": "OUTGOING_CALL"
        },
        headers={"X-Forwarded-For": "198.51.100.1"}
    )
    assert fp_resp1.status_code == 200
    assert fp_resp1.json()["status"] == "AMNESTY_VOTE_RECORDED"
    assert fp_resp1.json()["unblocked"] is False

    # 2. Second independent confirmation from different subnet -> quorum reached!
    fp_resp2 = client.post(
        "/api/v1/report/false-positive",
        json={
            "phone_number": "79037771122",
            "device_hash": "e" * 32,
            "reason": "OUTGOING_CALL"
        },
        headers={"X-Forwarded-For": "203.0.113.1"}
    )
    assert fp_resp2.status_code == 200
    assert fp_resp2.json()["status"] == "AMNESTY_APPLIED"
    assert fp_resp2.json()["unblocked"] is True

def test_api_pools_binary_protocol(client_and_db):
    import struct
    client = client_and_db

    # Request binary format full sync
    resp = client.get("/api/v1/pools/binary?since=0")
    assert resp.status_code == 200
    assert resp.headers["content-type"] == "application/octet-stream"
    raw = resp.content
    assert len(raw) >= 16

    magic, proto_v, cur_v, flags, _ = struct.unpack(">4sHIHI", raw[:16])
    assert magic == b"ASPB"
    assert proto_v == 1

    # Check HTTP 304 on binary endpoint
    if cur_v > 0:
        resp_304 = client.get(f"/api/v1/pools/binary?since={cur_v}")
        assert resp_304.status_code == 304

def test_api_export_ios_call_directory(client_and_db):
    client = client_and_db

    # 1. Text format export (standard for Apple CXCallDirectoryExtension)
    resp_txt = client.get("/api/v1/export/ios?format=txt")
    assert resp_txt.status_code == 200
    assert "text/plain" in resp_txt.headers["content-type"]
    assert "X-CallKit-Version" in resp_txt.headers

    lines = [line.strip() for line in resp_txt.text.strip().split("\n") if line.strip()]
    if lines:
        numbers = [int(n) for n in lines]
        # Invariant for Apple CallKit: strictly ascending order (N[i] < N[i+1])
        for i in range(len(numbers) - 1):
            assert numbers[i] < numbers[i + 1], f"CallKit violation: {numbers[i]} >= {numbers[i+1]}"

    # 2. JSON format export
    resp_json = client.get("/api/v1/export/ios?format=json")
    assert resp_json.status_code == 200
    data = resp_json.json()
    assert "version" in data
    assert "count" in data
    assert "numbers" in data
    if data["numbers"]:
        nums = data["numbers"]
        for i in range(len(nums) - 1):
            assert nums[i] < nums[i + 1]

def test_api_export_ios_density_prioritization_and_memory_capping(client_and_db):
    client = client_and_db

    # Request with strict limit = 50 numbers
    resp = client.get("/api/v1/export/ios?format=json&limit=50")
    assert resp.status_code == 200
    data = resp.json()

    assert data["count"] <= 50
    assert len(data["numbers"]) <= 50

    # Verify monotonic ascending order without duplicates
    nums = data["numbers"]
    for i in range(len(nums) - 1):
        assert nums[i] < nums[i + 1]
    assert "numbers" in data

    # 3. HTTP 304 when version matches
    cur_v = data["version"]
    if cur_v > 0:
        resp_304 = client.get(f"/api/v1/export/ios?since_version={cur_v}")
        assert resp_304.status_code == 304
