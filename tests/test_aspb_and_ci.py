import tempfile
from pathlib import Path
import pytest
import yaml
from typer.testing import CliRunner
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from datetime import timedelta
from backend.aspb import (
    build_aspb_payload,
    parse_aspb_payload,
    generate_aspb_snapshot,
    CATEGORY_MAP,
    REVERSE_CATEGORY_MAP,
)
from backend.cli import app
from backend.database import Base
from backend.models import ActiveBlock, SystemMetadata, utc_now_naive

runner = CliRunner()

def test_aspb_roundtrip_serialization():
    added = [
        (74951110000, 74951119999, 1, 15),
        (78002000000, 78002009999, 2, 15),
        (77011230000, 77011239999, 7, 15),
    ]
    removed = [
        (74959990000, 74959999999, 12)
    ]

    payload = build_aspb_payload(
        current_ver=15,
        is_full_sync=True,
        has_more=False,
        added_records=added,
        removed_records=removed,
    )

    parsed = parse_aspb_payload(payload)
    assert parsed["magic"] == "ASPB"
    assert parsed["proto_version"] == 1
    assert parsed["current_version"] == 15
    assert parsed["is_full_sync"] is True
    assert parsed["has_more"] is False

    assert len(parsed["added"]) == 3
    assert parsed["added"][0]["range_start"] == 74951110000
    assert parsed["added"][0]["range_end"] == 74951119999
    assert parsed["added"][0]["category"] == "SPAM"
    assert parsed["added"][1]["category"] == "SURVEY"
    assert parsed["added"][2]["category"] == "FOREIGN_PLUS7"

    assert len(parsed["removed"]) == 1
    assert parsed["removed"][0]["range_start"] == 74959990000
    assert parsed["removed"][0]["range_end"] == 74959999999

def test_aspb_invalid_magic_and_length():
    with pytest.raises(ValueError, match="Payload too short"):
        parse_aspb_payload(b"SHORT")

    with pytest.raises(ValueError, match="Invalid ASPB magic"):
        parse_aspb_payload(b"FAIL" + b"\x00" * 20)

def test_generate_aspb_snapshot_from_db():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()

    try:
        # Add metadata and active blocks
        session.add(SystemMetadata(key="delta_version", value_int=25))
        session.add(
            ActiveBlock(
                range_start=74951234500,
                range_end=74951234599,
                block_type="MICRO_100",
                operator_name="МТТ",
                category="SPAM",
                status="ACTIVE",
                expires_at=utc_now_naive() + timedelta(days=30),
                version=25,
            )
        )
        session.commit()

        snapshot_bytes = generate_aspb_snapshot(session)
        assert snapshot_bytes.startswith(b"ASPB")

        parsed = parse_aspb_payload(snapshot_bytes)
        assert parsed["current_version"] == 25
        assert parsed["is_full_sync"] is True
        assert len(parsed["added"]) == 1
        assert parsed["added"][0]["range_start"] == 74951234500
        assert parsed["added"][0]["range_end"] == 74951234599
        assert parsed["added"][0]["category"] == "SPAM"
    finally:
        session.close()

def test_cli_export_aspb_command():
    with tempfile.NamedTemporaryFile(suffix=".aspb", delete=False) as f:
        out_path = Path(f.name)

    try:
        res = runner.invoke(app, ["export-aspb", "--output", str(out_path)])
        assert res.exit_code == 0
        assert out_path.exists()
        assert out_path.stat().st_size > 16  # Has at least header
    finally:
        if out_path.exists():
            out_path.unlink()

def test_github_workflows_yaml_syntax():
    workflow_dir = Path(".github/workflows")
    assert workflow_dir.exists(), ".github/workflows directory must exist"

    sync_wf = workflow_dir / "sync_registry.yml"
    build_wf = workflow_dir / "android_build.yml"

    assert sync_wf.exists(), "sync_registry.yml must exist"
    assert build_wf.exists(), "android_build.yml must exist"

    # Validate YAML structure
    with open(sync_wf, "r", encoding="utf-8") as f:
        sync_data = yaml.safe_load(f)
    assert "name" in sync_data
    assert "jobs" in sync_data
    sync_on = sync_data.get("on") or sync_data.get(True)
    assert "schedule" in sync_on

    with open(build_wf, "r", encoding="utf-8") as f:
        build_data = yaml.safe_load(f)
    assert "name" in build_data
    assert "jobs" in build_data
    build_on = build_data.get("on") or build_data.get(True)
    assert "push" in build_on
