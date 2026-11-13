from datetime import datetime, timedelta
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from backend.clustering import (
    evaluate_report,
    create_active_block,
    escalate_thousand_if_needed,
)
from backend.models import ActiveBlock, Base, BlockDelta, UserReport, utc_now_naive
from backend.ttl_worker import run_ttl_cleanup
from backend.whitelist import seed_default_whitelist

@pytest.fixture
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    seed_default_whitelist(session)
    yield session
    session.close()

def test_anti_poisoning_single_vote_per_device_day(db_session):
    phone = 79031234501
    dev1 = "device_alpha_123"

    res1 = evaluate_report(phone, dev1, "SPAM", None, db_session)
    assert res1["status"] == "ACCEPTED"
    assert res1["blocked"] is False

    # Second report from the same device for the same 100-bucket today
    res2 = evaluate_report(phone, dev1, "SPAM", None, db_session)
    assert res2["status"] == "IGNORED_DUPLICATE_VOTE"

def test_clustering_micro_100_trigger(db_session):
    # Geographic ABC code (495): 3 distinct devices, 2 distinct numbers in bucket 749512345
    res1 = evaluate_report(74951234510, "dev_1", "SURVEY", 10, db_session)
    assert res1["blocked"] is False

    res2 = evaluate_report(74951234520, "dev_2", "SURVEY", 5, db_session)
    assert res2["blocked"] is False

    # 3rd device reports -> ABC threshold met! Full hundred blocked
    res3 = evaluate_report(74951234520, "dev_3", "SURVEY", 4, db_session)
    assert res3["blocked"] is True
    assert res3["block_type"] == "MICRO_100"
    assert res3["range_start"] == 74951234500
    assert res3["range_end"] == 74951234599

    # Check block exists in ActiveBlock
    block = db_session.query(ActiveBlock).filter(ActiveBlock.range_start == 74951234500).first()
    assert block is not None
    assert block.status == "ACTIVE"

    # Check delta was emitted
    delta = db_session.query(BlockDelta).filter(BlockDelta.range_start == 74951234500).first()
    assert delta is not None
    assert delta.action == "ADD"

def test_def_mnp_targeted_block_then_escalate(db_session):
    # DEF (Mobile 903): MNP protection
    # 3 reports on number 79031234510
    evaluate_report(79031234510, "dev_m1", "SPAM", None, db_session)
    evaluate_report(79031234510, "dev_m2", "SPAM", None, db_session)
    res_single = evaluate_report(79031234510, "dev_m3", "SPAM", None, db_session)

    # Must be targeted SINGLE_NUMBER block to protect other subscribers in the hundred
    assert res_single["blocked"] is True
    assert res_single["block_type"] == "SINGLE_NUMBER"
    assert res_single["range_start"] == 79031234510
    assert res_single["range_end"] == 79031234510

    # Report on 5 more distinct numbers in same hundred -> escalates to MICRO_100!
    for i in range(1, 6):
        num = 79031234500 + i * 10
        evaluate_report(num, f"dev_esc_{i}", "SPAM", None, db_session)

    # 6th number in hundred triggers full escalation
    res_esc = evaluate_report(79031234590, "dev_esc_final", "SPAM", None, db_session)
    assert res_esc["blocked"] is True
    assert res_esc["block_type"] == "MICRO_100"
    assert res_esc["range_start"] == 79031234500
    assert res_esc["range_end"] == 79031234599

def test_sybil_defense_trust_score_and_subnet(db_session):
    from backend.models import DeviceReputation

    # 1. Test Subnet Rate Limiting (/24 prefix) with Edge Case 3 CGNAT mobile allowance (max 4/subnet/day)
    ip_same_subnet = "198.51.100.10"
    evaluate_report(74957770001, "sybil_dev1", "SPAM", None, db_session, client_ip=ip_same_subnet)
    evaluate_report(74957770002, "sybil_dev2", "SPAM", None, db_session, client_ip="198.51.100.20")
    evaluate_report(74957770003, "sybil_dev3", "SPAM", None, db_session, client_ip="198.51.100.30")
    evaluate_report(74957770004, "sybil_dev4", "SPAM", None, db_session, client_ip="198.51.100.40")

    # 5th report on same bucket from same /24 subnet is rejected!
    res_sub = evaluate_report(74957770005, "sybil_dev5", "SPAM", None, db_session, client_ip="198.51.100.50")
    assert res_sub["status"] == "IGNORED_SUBNET_LIMIT"
    assert res_sub["blocked"] is False

    # 2. Test Low Trust Score Sybil attack
    # 3 attacker devices with low trust (0.3)
    dev_a1 = db_session.query(DeviceReputation).filter(DeviceReputation.device_hash == "sybil_dev1").first()
    dev_a2 = db_session.query(DeviceReputation).filter(DeviceReputation.device_hash == "sybil_dev2").first()
    if dev_a1: dev_a1.trust_score = 0.3
    if dev_a2: dev_a2.trust_score = 0.3
    db_session.commit()

    # Even from distinct subnets, 3 low-trust devices (sum = 0.9) cannot reach threshold 3.0
    r1 = evaluate_report(74958880001, "sybil_low1", "SPAM", None, db_session, client_ip="203.0.113.1")
    r2 = evaluate_report(74958880002, "sybil_low2", "SPAM", None, db_session, client_ip="203.0.113.2")
    # Manually lower their scores
    db_session.query(DeviceReputation).filter(DeviceReputation.device_hash.in_(["sybil_low1", "sybil_low2"])).update({"trust_score": 0.3})
    db_session.commit()

    r3 = evaluate_report(74958880002, "sybil_low3", "SPAM", None, db_session, client_ip="203.0.113.3")
    db_session.query(DeviceReputation).filter(DeviceReputation.device_hash == "sybil_low3").update({"trust_score": 0.3})
    db_session.commit()

    # Re-evaluate reports: total weight = 0.3 + 0.3 + 0.3 = 0.9 < 3.0 -> NOT blocked!
    r4 = evaluate_report(74958880005, "sybil_low4", "SPAM", None, db_session, client_ip="203.0.113.4")
    db_session.query(DeviceReputation).filter(DeviceReputation.device_hash == "sybil_low4").update({"trust_score": 0.3})
    db_session.commit()
    assert r4["blocked"] is False

def test_auto_amnesty_deactivates_block(db_session):
    from backend.clustering import handle_false_positive_report

    # Create a targeted single number block
    create_active_block(79039991122, 79039991122, "SINGLE_NUMBER", "Beeline", "SPAM", db_session)

    # 1. First user reports false positive -> vote recorded, not unblocked yet (Edge Case 2 anti-sabotage)
    res1 = handle_false_positive_report(79039991122, "dev_legit_user1", "OUTGOING_CALL", db_session, client_ip="198.51.100.1")
    assert res1["status"] == "AMNESTY_VOTE_RECORDED"
    assert res1["unblocked"] is False

    # Block is still active
    block = db_session.query(ActiveBlock).filter(ActiveBlock.range_start == 79039991122).first()
    assert block.status == "ACTIVE"

    # 2. Second independent user confirms false positive -> quorum met, amnesty applied!
    res2 = handle_false_positive_report(79039991122, "dev_legit_user2", "OUTGOING_CALL", db_session, client_ip="203.0.113.1")
    assert res2["status"] == "AMNESTY_APPLIED"
    assert res2["unblocked"] is True

    # Block should now be EXPIRED and REMOVE delta emitted
    db_session.refresh(block)
    assert block.status == "EXPIRED"

    rem_delta = db_session.query(BlockDelta).filter(BlockDelta.action == "REMOVE", BlockDelta.range_start == 79039991122).first()
    assert rem_delta is not None

def test_write_buffer_async_flush(db_session):
    import asyncio
    from backend.buffer import WriteBuffer, BufferedReportItem

    buf = WriteBuffer(max_batch_size=10, flush_interval=1.0)
    buf.enqueue(BufferedReportItem(74953331122, "buf_dev1", "SPAM", 10))
    buf.enqueue(BufferedReportItem(74953331133, "buf_dev2", "SPAM", 5))

    assert buf.pending_count() == 2

    processed = asyncio.run(buf.flush(session=db_session))
    assert processed == 2
    assert buf.pending_count() == 0

def test_velocity_spike_lowers_threshold_and_accelerates_escalation(db_session):
    # Bucket: 79034440000..79034440099 (DEF code 903)
    # Fast surge: 2 reports in <= 15 minutes from 2 distinct devices & subnets
    evaluate_report(79034440011, "surge_dev1", "SPAM", None, db_session, client_ip="198.51.100.1")
    evaluate_report(79034440022, "surge_dev2", "SPAM", None, db_session, client_ip="203.0.113.1")

    # 3rd report arrives rapidly on 3rd number in hundred -> velocity spike accelerates escalation to MICRO_100!
    res_surge = evaluate_report(79034440033, "surge_dev3", "SPAM", None, db_session, client_ip="192.0.2.1")
    assert res_surge["blocked"] is True
    assert res_surge["block_type"] == "MICRO_100"
    assert res_surge["range_start"] == 79034440000
    assert res_surge["range_end"] == 79034440099

def test_thousand_escalation(db_session):
    # Bucket thousand: 79031230000 .. 79031230999 (thousand = 79031230)
    # Create 3 micro-100 blocks directly
    create_active_block(79031230000, 79031230099, "MICRO_100", "Beeline", "SPAM", db_session)
    create_active_block(79031230100, 79031230199, "MICRO_100", "Beeline", "SPAM", db_session)
    create_active_block(79031230200, 79031230299, "MICRO_100", "Beeline", "SPAM", db_session)

    # Check that thousand escalation triggers
    escalated = escalate_thousand_if_needed(79031230, "Beeline", "SPAM", db_session)
    assert escalated is True

    # The 1000 block should now be ACTIVE
    block_1000 = db_session.query(ActiveBlock).filter(
        ActiveBlock.range_start == 79031230000,
        ActiveBlock.range_end == 79031230999
    ).first()
    assert block_1000 is not None
    assert block_1000.block_type == "SUBPOOL_1000"

    # The individual 100 blocks should be ABSORBED
    micro_active = db_session.query(ActiveBlock).filter(
        ActiveBlock.block_type == "MICRO_100",
        ActiveBlock.status == "ACTIVE",
        ActiveBlock.range_start >= 79031230000,
        ActiveBlock.range_end <= 79031230999,
    ).count()
    assert micro_active == 0

def test_ttl_cleanup_removes_expired_blocks(db_session):
    # Create block that expired yesterday
    now = utc_now_naive()
    block = ActiveBlock(
        range_start=79035550000,
        range_end=79035550099,
        block_type="MICRO_100",
        operator_name="TestOp",
        category="SPAM",
        confidence_score=100,
        created_at=now - timedelta(days=31),
        expires_at=now - timedelta(days=1),
        repeat_count=1,
        status="ACTIVE",
        version=1,
    )
    db_session.add(block)
    db_session.commit()

    # Run cleanup
    res = run_ttl_cleanup(db_session)
    assert res["expired_blocks_removed"] == 1

    # Status should now be EXPIRED
    db_session.refresh(block)
    assert block.status == "EXPIRED"

    # Delta REMOVE should exist
    delta_remove = (
        db_session.query(BlockDelta)
        .filter(BlockDelta.action == "REMOVE", BlockDelta.range_start == 79035550000)
        .first()
    )
    assert delta_remove is not None

def test_range_absorption_guarantees_disjoint_intervals(db_session):
    # Edge Case 1: Range Absorption
    # 1. Create a targeted single number block inside a hundred: [79031112250, 79031112250]
    single, _ = create_active_block(79031112250, 79031112250, "SINGLE_NUMBER", "Beeline", "SPAM", db_session)
    assert single.status == "ACTIVE"

    # 2. Now escalate/create full hundred: [79031112200, 79031112299]
    micro, _ = create_active_block(79031112200, 79031112299, "MICRO_100", "Beeline", "SPAM", db_session)
    assert micro.status == "ACTIVE"

    # 3. The child single-number block MUST be absorbed and removed to maintain disjoint invariant
    db_session.refresh(single)
    assert single.status == "ABSORBED"

    # Delta REMOVE for the single number must exist
    rem_single = (
        db_session.query(BlockDelta)
        .filter(BlockDelta.action == "REMOVE", BlockDelta.range_start == 79031112250, BlockDelta.range_end == 79031112250)
        .first()
    )
    assert rem_single is not None

    # 4. Attempting to create another nested SINGLE_NUMBER inside the active hundred must NOT create a nested block
    sub_block, created = create_active_block(79031112280, 79031112280, "SINGLE_NUMBER", "Beeline", "SPAM", db_session)
    assert created is False
    assert sub_block.range_start == 79031112200
    assert sub_block.range_end == 79031112299

def test_cgnat_multi_device_allowance(db_session):
    # Edge Case 3: CGNAT Mobile Operator Allowance
    # Up to 4 distinct devices sharing one mobile /24 CGNAT subnet can report today
    cgnat_ip_base = "100.64.1."
    for i in range(1, 5):
        res = evaluate_report(
            74959990000 + i,
            f"cgnat_dev_{i}",
            "SPAM",
            None,
            db_session,
            client_ip=f"{cgnat_ip_base}{i}"
        )
        assert res["status"] in ("ACCEPTED", "IGNORED_DUPLICATE_VOTE")

    # 5th report on the same bucket from the same /24 CGNAT subnet hits the ceiling
    res_5 = evaluate_report(
        74959990099,
        "cgnat_dev_5",
        "SPAM",
        None,
        db_session,
        client_ip=f"{cgnat_ip_base}5"
    )
    assert res_5["status"] == "IGNORED_SUBNET_LIMIT"

def test_false_positive_requires_quorum_and_prevents_sabotage(db_session):
    from backend.clustering import handle_false_positive_report

    # Edge Case 2: Spammer Sabotage Protection
    # Active micro-100 pool
    create_active_block(79038880000, 79038880099, "MICRO_100", "Beeline", "SPAM", db_session)

    # Spammer tries to unblock via single automated report
    res_spammer = handle_false_positive_report(79038880010, "spammer_bot_1", "MANUAL_AMNESTY", db_session, client_ip="198.51.100.1")
    assert res_spammer["status"] == "AMNESTY_VOTE_RECORDED"
    assert res_spammer["unblocked"] is False

    # Block is still active and protective!
    block = db_session.query(ActiveBlock).filter(ActiveBlock.range_start == 79038880000).first()
    assert block.status == "ACTIVE"

    # Second independent user confirms from different subnet -> quorum met!
    res_legit = handle_false_positive_report(79038880020, "user_legit_2", "OUTGOING_CALL", db_session, client_ip="203.0.113.1")
    assert res_legit["status"] == "AMNESTY_APPLIED"
    assert res_legit["unblocked"] is True
