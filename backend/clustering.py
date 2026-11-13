import ipaddress
from datetime import datetime, timedelta
import logging
from sqlalchemy import func
from sqlalchemy.orm import Session
from backend.config import settings
from backend.models import (
    ActiveBlock,
    BlockDelta,
    DeviceReputation,
    FalsePositiveReport,
    RegistryRange,
    SystemMetadata,
    UserReport,
    utc_now_naive,
)
from backend.whitelist import is_number_whitelisted
from backend.normalizer import normalize_incoming_phone, is_kazakhstan_number, get_country_and_code

logger = logging.getLogger(__name__)

DAILY_DEVICE_REPORT_LIMIT = 50
EVALUATION_WINDOW_DAYS = 7
MIN_VOTES_THRESHOLD = 3
MIN_DISTINCT_NUMBERS_THRESHOLD = 2
MIN_HUNDREDS_FOR_THOUSAND_ESCALATION = 3

def extract_subnet_prefix(ip_str: str | None) -> str:
    """Normalize client IP address to subnet prefix (/24 for IPv4, /48 for IPv6)."""
    if not ip_str:
        return "unknown"
    if ip_str in ("testclient", "localhost", "127.0.0.1", "::1"):
        return "local_dev"
    try:
        ip = ipaddress.ip_address(ip_str)
        if ip.version == 4:
            return str(ipaddress.ip_network(f"{ip_str}/24", strict=False))
        elif ip.version == 6:
            return str(ipaddress.ip_network(f"{ip_str}/48", strict=False))
    except ValueError:
        pass
    return "unknown"

def get_or_create_device_reputation(device_hash: str, session: Session) -> DeviceReputation:
    """Retrieve or initialize trust score record for reporting device."""
    clean_hash = device_hash.lower().strip()
    is_trusted_seed = clean_hash in settings.trusted_seed_devices
    initial_score = settings.trusted_seed_trust_score if is_trusted_seed else settings.default_device_trust_score

    rep = session.query(DeviceReputation).filter(DeviceReputation.device_hash == clean_hash).first()
    if not rep:
        rep = DeviceReputation(
            device_hash=clean_hash,
            trust_score=initial_score,
            reports_count=1,
            confirmed_count=0,
            first_seen_at=utc_now_naive(),
            last_seen_at=utc_now_naive(),
        )
        session.add(rep)
        session.commit()
    else:
        if is_trusted_seed and rep.trust_score < settings.trusted_seed_trust_score:
            rep.trust_score = settings.trusted_seed_trust_score
        rep.reports_count += 1
        rep.last_seen_at = utc_now_naive()
        session.commit()
    return rep

def reward_device_reputations(device_hashes: set[str], session: Session):
    """Reward devices whose complaints led to a confirmed spam pool block."""
    if not device_hashes:
        return
    reps = session.query(DeviceReputation).filter(DeviceReputation.device_hash.in_(device_hashes)).all()
    for r in reps:
        r.confirmed_count += 1
        # Increase trust score towards 1.0
        r.trust_score = min(1.0, round(r.trust_score + 0.1, 2))
    session.commit()

def get_next_version(session: Session) -> int:
    """Increment and return the global monotonic delta version."""
    meta = session.query(SystemMetadata).filter(SystemMetadata.key == "delta_version").first()
    if not meta:
        meta = SystemMetadata(key="delta_version", value_int=1, updated_at=utc_now_naive())
        session.add(meta)
        session.commit()
        return 1
    meta.value_int = (meta.value_int or 0) + 1
    meta.updated_at = utc_now_naive()
    session.commit()
    return meta.value_int

def get_current_version(session: Session) -> int:
    """Get the current delta version without incrementing."""
    meta = session.query(SystemMetadata).filter(SystemMetadata.key == "delta_version").first()
    return meta.value_int if meta and meta.value_int else 0

def create_active_block(
    range_start: int,
    range_end: int,
    block_type: str,
    operator_name: str | None,
    category: str,
    session: Session,
) -> tuple[ActiveBlock, bool]:
    """
    Create or update an active block and write to block_deltas.
    Applies TTL: 30 days for new blocks, 60 days for repeat offenders.
    """
    now = utc_now_naive()

    # Edge Case 1: Check if an enclosing active block already covers this range
    enclosing = (
        session.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.range_start <= range_start,
            ActiveBlock.range_end >= range_end,
            not (ActiveBlock.range_start == range_start and ActiveBlock.range_end == range_end),
        )
        .first()
    )
    if enclosing:
        logger.info(f"Range [{range_start}..{range_end}] is already covered by active block [{enclosing.range_start}..{enclosing.range_end}]")
        return enclosing, False

    existing = (
        session.query(ActiveBlock)
        .filter(ActiveBlock.range_start == range_start, ActiveBlock.range_end == range_end)
        .first()
    )

    if existing and existing.status == "ACTIVE":
        existing.expires_at = max(existing.expires_at, now + timedelta(days=30))
        session.commit()
        return existing, False

    # Edge Case 1: Absorb any strictly contained active sub-blocks to guarantee disjoint intervals
    contained_blocks = (
        session.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.range_start >= range_start,
            ActiveBlock.range_end <= range_end,
            not (ActiveBlock.range_start == range_start and ActiveBlock.range_end == range_end),
        )
        .all()
    )
    for cb in contained_blocks:
        v_rem = get_next_version(session)
        cb.status = "ABSORBED"
        cb.version = v_rem
        session.add(BlockDelta(
            version=v_rem,
            action="REMOVE",
            range_start=cb.range_start,
            range_end=cb.range_end,
            operator_name=cb.operator_name,
            category=cb.category,
            created_at=now,
        ))
        logger.info(f"ABSORBED sub-block [{cb.range_start}..{cb.range_end}] into wider block [{range_start}..{range_end}]")

    repeat_count = 1
    ttl_days = 30
    if existing and existing.status == "EXPIRED":
        if (now - existing.expires_at).days <= 14:
            repeat_count = existing.repeat_count + 1
            ttl_days = 60

    version = get_next_version(session)
    expires_at = now + timedelta(days=ttl_days)

    if existing:
        existing.status = "ACTIVE"
        existing.expires_at = expires_at
        existing.repeat_count = repeat_count
        existing.version = version
        block = existing
    else:
        block = ActiveBlock(
            range_start=range_start,
            range_end=range_end,
            block_type=block_type,
            operator_name=operator_name,
            category=category,
            confidence_score=100,
            created_at=now,
            expires_at=expires_at,
            repeat_count=repeat_count,
            status="ACTIVE",
            version=version,
        )
        session.add(block)

    delta = BlockDelta(
        version=version,
        action="ADD",
        range_start=range_start,
        range_end=range_end,
        operator_name=operator_name,
        category=category,
        created_at=now,
    )
    session.add(delta)
    session.commit()
    logger.info(f"BLOCKED: [{range_start}..{range_end}] type={block_type} version={version} TTL={ttl_days}d")
    return block, True

def absorb_single_numbers_in_hundred(bucket_100: int, session: Session):
    """Absorb any existing SINGLE_NUMBER blocks into a full MICRO_100 escalation."""
    start_100 = bucket_100 * 100
    end_100 = start_100 + 99
    singles = (
        session.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.block_type == "SINGLE_NUMBER",
            ActiveBlock.range_start >= start_100,
            ActiveBlock.range_end <= end_100,
        )
        .all()
    )
    for sb in singles:
        v_rem = get_next_version(session)
        sb.status = "ABSORBED"
        sb.version = v_rem
        session.add(BlockDelta(
            version=v_rem,
            action="REMOVE",
            range_start=sb.range_start,
            range_end=sb.range_end,
            operator_name=sb.operator_name,
            category=sb.category,
            created_at=utc_now_naive(),
        ))
    if singles:
        session.commit()

def escalate_thousand_if_needed(bucket_1000: int, operator_name: str | None, category: str, session: Session) -> bool:
    """Escalate: If >= 3 micro-hundreds are blocked within one thousand [X000..X999]."""
    start_1000 = bucket_1000 * 1000
    end_1000 = start_1000 + 999

    micro_blocks = (
        session.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.block_type == "MICRO_100",
            ActiveBlock.range_start >= start_1000,
            ActiveBlock.range_end <= end_1000,
        )
        .all()
    )

    if len(micro_blocks) >= MIN_HUNDREDS_FOR_THOUSAND_ESCALATION:
        logger.info(f"Escalating bucket_1000={bucket_1000} to SUBPOOL_1000 ({len(micro_blocks)} hundreds blocked)")
        for mb in micro_blocks:
            v_rem = get_next_version(session)
            mb.status = "ABSORBED"
            mb.version = v_rem
            session.add(BlockDelta(
                version=v_rem,
                action="REMOVE",
                range_start=mb.range_start,
                range_end=mb.range_end,
                operator_name=mb.operator_name,
                category=mb.category,
                created_at=utc_now_naive(),
            ))
        session.commit()

        create_active_block(
            range_start=start_1000,
            range_end=end_1000,
            block_type="SUBPOOL_1000",
            operator_name=operator_name,
            category=category,
            session=session,
        )
        return True
    return False

def evaluate_report(
    phone_number: int,
    device_hash: str,
    category: str,
    call_duration: int | None,
    session: Session,
    client_ip: str | None = None,
) -> dict:
    """
    Main evaluation pipeline when a report arrives:
    1. Check Server Whitelist.
    2. Check Device Rate Limits & Subnet Rate Limits.
    3. Update Device Trust Score reputation.
    4. Save UserReport.
    5. Evaluate SIP Rule / DEF MNP Targeted vs Hundred Rule / Escalation.
    """
    now = utc_now_naive()
    today_str = now.strftime("%Y-%m-%d")

    country, _ = get_country_and_code(phone_number)
    geo_label = "Казахстан" if country == "KZ" else ("Россия" if country == "RU" else "Неизвестно")

    def with_geo(result_dict: dict) -> dict:
        result_dict["country"] = country
        result_dict["geo"] = geo_label
        return result_dict

    # Step 1: Whitelist check
    is_wl, wl_entry = is_number_whitelisted(phone_number, session)
    if is_wl and wl_entry:
        return with_geo({
            "status": "IGNORED_WHITELIST",
            "message": f"Number is in protected whitelist group '{wl_entry.group_key}' ({wl_entry.organization_name})",
            "blocked": False,
        })

    # Step 2: Rate limit check (Max 50 reports/day per device)
    daily_reports_count = (
        session.query(func.count(UserReport.id))
        .filter(UserReport.device_hash == device_hash, UserReport.report_day == today_str)
        .scalar() or 0
    )
    if daily_reports_count >= DAILY_DEVICE_REPORT_LIMIT:
        return with_geo({
            "status": "IGNORED_RATE_LIMIT",
            "message": "Daily device complaint limit exceeded (max 50/day)",
            "blocked": False,
        })

    bucket_100 = phone_number // 100
    bucket_1000 = phone_number // 1000

    # Step 3: Subnet rate limiting (/24 prefix) to block single-machine Sybil botnets
    subnet_prefix = extract_subnet_prefix(client_ip)
    if subnet_prefix not in ("unknown", "local_dev"):
        subnet_reports = (
            session.query(func.count(UserReport.id))
            .filter(
                UserReport.subnet_prefix == subnet_prefix,
                UserReport.bucket_100 == bucket_100,
                UserReport.report_day == today_str,
            )
            .scalar() or 0
        )
        if subnet_reports >= settings.max_reports_per_subnet_day:
            return with_geo({
                "status": "IGNORED_SUBNET_LIMIT",
                "message": f"Daily limit for subnet {subnet_prefix} on this 100-number pool exceeded",
                "blocked": False,
            })

    # Step 4: Check 1 vote per device per bucket_100 per day
    existing_vote = (
        session.query(UserReport)
        .filter(
            UserReport.device_hash == device_hash,
            UserReport.bucket_100 == bucket_100,
            UserReport.report_day == today_str,
        )
        .first()
    )
    if existing_vote:
        return with_geo({
            "status": "IGNORED_DUPLICATE_VOTE",
            "message": "Device already voted for this 100-number bucket today",
            "blocked": False,
        })

    # Update or register device reputation
    clean_device_hash = device_hash.lower().strip()
    is_trusted_seed = clean_device_hash in settings.trusted_seed_devices
    get_or_create_device_reputation(clean_device_hash, session)

    # Insert valid report
    report = UserReport(
        phone_number=phone_number,
        bucket_100=bucket_100,
        bucket_1000=bucket_1000,
        device_hash=device_hash,
        client_ip=client_ip,
        subnet_prefix=subnet_prefix,
        category=category,
        call_duration_seconds=call_duration,
        created_at=now,
        report_day=today_str,
    )
    session.add(report)
    session.commit()

    # Step 5: Clustering evaluation
    time_window_start = now - timedelta(days=EVALUATION_WINDOW_DAYS)

    str_num = str(phone_number)
    code = int(str_num[1:4]) if len(str_num) == 11 and str_num.startswith("7") else None
    is_def = code is not None and 900 <= code <= 999

    reg_query = session.query(RegistryRange)
    if code:
        reg_query = reg_query.filter(RegistryRange.code_abc_def == code)
    reg_row = (
        reg_query
        .filter(RegistryRange.range_start <= phone_number, RegistryRange.range_end >= phone_number)
        .first()
    )

    operator_name = reg_row.operator_clean if reg_row else None
    is_sip = reg_row.is_b2b_sip if reg_row else False

    # Check SIP Rule ( емкость <= 1000 или SIP-провайдер )
    if is_sip and reg_row:
        sip_reports = (
            session.query(UserReport.device_hash)
            .filter(
                UserReport.phone_number >= reg_row.range_start,
                UserReport.phone_number <= reg_row.range_end,
                UserReport.created_at >= time_window_start,
            )
            .distinct()
            .all()
        )
        sip_devices = {r[0] for r in sip_reports}
        reps = (
            session.query(DeviceReputation.device_hash, DeviceReputation.trust_score)
            .filter(DeviceReputation.device_hash.in_(sip_devices))
            .all()
        )
        rep_map = {r[0]: r[1] for r in reps}
        sip_weight = sum(rep_map.get(d, settings.default_device_trust_score) for d in sip_devices)

        if sip_weight >= settings.min_weight_threshold:
            reward_device_reputations(sip_devices, session)
            block, created = create_active_block(
                range_start=reg_row.range_start,
                range_end=reg_row.range_end,
                block_type="SIP_OFFICIAL",
                operator_name=operator_name,
                category=category,
                session=session,
            )
            return with_geo({
                "status": "ACCEPTED",
                "message": f"Official B2B/SIP pool [{reg_row.range_start}..{reg_row.range_end}] blocked",
                "blocked": True,
                "block_type": "SIP_OFFICIAL",
                "range_start": reg_row.range_start,
                "range_end": reg_row.range_end,
            })

    # Evaluate reports in bucket_100
    recent_reports = (
        session.query(UserReport.device_hash, UserReport.phone_number)
        .filter(
            UserReport.bucket_100 == bucket_100,
            UserReport.created_at >= time_window_start,
        )
        .all()
    )

    unique_devices = {r[0] for r in recent_reports}
    distinct_numbers = {r[1] for r in recent_reports}

    reps = (
        session.query(DeviceReputation.device_hash, DeviceReputation.trust_score)
        .filter(DeviceReputation.device_hash.in_(unique_devices))
        .all()
    )
    rep_map = {r[0]: r[1] for r in reps}
    total_trust_weight = sum(rep_map.get(d, settings.default_device_trust_score) for d in unique_devices)

    # Phase 4: Velocity Detector (Predictive Dialer surge in <= 15 mins)
    velocity_start = now - timedelta(minutes=settings.velocity_window_minutes)
    velocity_reports = [r for r in recent_reports if r[0] in unique_devices]
    # Query reports with timestamp in velocity window
    v_rows = (
        session.query(UserReport.device_hash, UserReport.subnet_prefix)
        .filter(
            UserReport.bucket_100 == bucket_100,
            UserReport.created_at >= velocity_start,
        )
        .all()
    )
    v_devices = {r[0] for r in v_rows}
    v_subnets = {r[1] for r in v_rows if r[1] not in (None, "unknown", "local_dev")}

    # Surge detected if >= 2 devices from >= 2 distinct subnets reported rapidly
    is_velocity_spike = len(v_devices) >= 2 and len(v_subnets) >= 2

    required_weight = settings.velocity_weight_threshold if is_velocity_spike else settings.min_weight_threshold
    has_sufficient_weight = total_trust_weight >= required_weight

    if has_sufficient_weight:
        reward_device_reputations(unique_devices, session)

        if is_def:
            # DEF MNP Protection:
            # If high-velocity dialer surge is detected, escalate at 3 numbers instead of 6
            def_hundred_threshold = 3 if is_velocity_spike else settings.min_distinct_def_for_hundred
            if len(distinct_numbers) >= def_hundred_threshold:
                start_100 = bucket_100 * 100
                end_100 = start_100 + 99
                absorb_single_numbers_in_hundred(bucket_100, session)
                block, created = create_active_block(
                    range_start=start_100,
                    range_end=end_100,
                    block_type="MICRO_100",
                    operator_name=operator_name,
                    category=category,
                    session=session,
                )
                escalated = escalate_thousand_if_needed(bucket_1000, operator_name, category, session)
                return with_geo({
                    "status": "ACCEPTED",
                    "message": "DEF pool escalated to MICRO_100 (6+ numbers)" if not escalated else "Escalated to 1000 pool",
                    "blocked": True,
                    "block_type": "SUBPOOL_1000" if escalated else "MICRO_100",
                    "range_start": bucket_1000 * 1000 if escalated else start_100,
                    "range_end": bucket_1000 * 1000 + 999 if escalated else end_100,
                })
            else:
                # Targeted block for single phone number: protects innocent MNP subscribers in the hundred
                block, created = create_active_block(
                    range_start=phone_number,
                    range_end=phone_number,
                    block_type="SINGLE_NUMBER",
                    operator_name=operator_name,
                    category=category,
                    session=session,
                )
                return with_geo({
                    "status": "ACCEPTED",
                    "message": f"Targeted single number +{phone_number} blocked (DEF MNP protection)",
                    "blocked": True,
                    "block_type": "SINGLE_NUMBER",
                    "range_start": phone_number,
                    "range_end": phone_number,
                })
        else:
            # ABC (Geographic: 495, 812, etc.): MNP is negligible, block entire hundred if >= 2 numbers
            # In Founder Mode (trusted seeders), a single verified report immediately triggers MICRO_100 block
            if len(distinct_numbers) >= MIN_DISTINCT_NUMBERS_THRESHOLD or is_trusted_seed:
                start_100 = bucket_100 * 100
                end_100 = start_100 + 99
                block, created = create_active_block(
                    range_start=start_100,
                    range_end=end_100,
                    block_type="MICRO_100",
                    operator_name=operator_name,
                    category=category,
                    session=session,
                )
                escalated = escalate_thousand_if_needed(bucket_1000, operator_name, category, session)
                return with_geo({
                    "status": "ACCEPTED",
                    "message": "Micro-100 pool blocked (Trusted Seed trigger)" if is_trusted_seed else ("Micro-100 pool blocked" if not escalated else "Escalated to 1000 pool"),
                    "blocked": True,
                    "block_type": "SUBPOOL_1000" if escalated else "MICRO_100",
                    "range_start": bucket_1000 * 1000 if escalated else start_100,
                    "range_end": bucket_1000 * 1000 + 999 if escalated else end_100,
                })

    return with_geo({
        "status": "ACCEPTED",
        "message": f"Report recorded (weight: {total_trust_weight:.1f}/{settings.min_weight_threshold:.1f}, numbers: {len(distinct_numbers)})",
        "blocked": False,
        "bucket_100": bucket_100,
    })

def handle_false_positive_report(
    phone_number: int,
    device_hash: str,
    reason: str,
    session: Session,
    client_ip: str | None = None,
) -> dict:
    """
    Process false positive / amnesty signal:
    1. Records FalsePositiveReport with subnet and device trust weight.
    2. Edge Case 2: Requires independent confirmation quorum (>= 2 devices & subnets)
       to lift global server blocks, preventing single-actor / spammer sabotage.
    """
    now = utc_now_naive()
    subnet_prefix = extract_subnet_prefix(client_ip)

    # Check duplicate submission from same device in the last 7 days
    existing_fp = (
        session.query(FalsePositiveReport)
        .filter(
            FalsePositiveReport.phone_number == phone_number,
            FalsePositiveReport.device_hash == device_hash,
            FalsePositiveReport.created_at >= now - timedelta(days=7),
        )
        .first()
    )

    if not existing_fp:
        rep = get_or_create_device_reputation(device_hash, session)
        trust_weight = rep.trust_score if rep else settings.default_device_trust_score
        fp = FalsePositiveReport(
            phone_number=phone_number,
            device_hash=device_hash,
            client_ip=client_ip,
            subnet_prefix=subnet_prefix,
            trust_weight=trust_weight,
            reason=reason,
            created_at=now,
        )
        session.add(fp)
        session.commit()

    block = (
        session.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.range_start <= phone_number,
            ActiveBlock.range_end >= phone_number,
        )
        .first()
    )

    if not block:
        return {
            "status": "RECORDED",
            "message": "False positive recorded (number was not in active block)",
            "unblocked": False,
        }

    # Query all active false positive reports on this block within 7 days
    fp_rows = (
        session.query(
            FalsePositiveReport.device_hash,
            FalsePositiveReport.subnet_prefix,
            FalsePositiveReport.trust_weight,
        )
        .filter(
            FalsePositiveReport.phone_number >= block.range_start,
            FalsePositiveReport.phone_number <= block.range_end,
            FalsePositiveReport.created_at >= now - timedelta(days=7),
        )
        .all()
    )
    unique_devices = {r[0] for r in fp_rows}
    unique_subnets = {r[1] for r in fp_rows if r[1] not in (None, "unknown", "local_dev")}
    total_trust_weight = sum(r[2] for r in fp_rows)

    # Edge Case 2 & Phase 6.3: Quorum evaluation
    # Founder / Trusted seed devices possess immediate administrative amnesty authority
    clean_fp_hash = device_hash.lower().strip()
    is_trusted_seed = clean_fp_hash in settings.trusted_seed_devices

    if is_trusted_seed:
        quorum_met = True
    elif block.block_type == "SINGLE_NUMBER":
        quorum_met = (len(unique_devices) >= 2 and len(unique_subnets) >= 2) or (total_trust_weight >= 2.0 and len(unique_devices) >= 2)
    else:
        quorum_met = len(unique_devices) >= 2 and len(unique_subnets) >= 2 and total_trust_weight >= 2.0

    if quorum_met:
        v_rem = get_next_version(session)
        block.status = "EXPIRED"
        block.version = v_rem
        session.add(BlockDelta(
            version=v_rem,
            action="REMOVE",
            range_start=block.range_start,
            range_end=block.range_end,
            operator_name=block.operator_name,
            category=block.category,
            created_at=now,
        ))
        session.commit()
        logger.info(
            f"AMNESTY QUORUM MET: Unblocked {block.block_type} [{block.range_start}..{block.range_end}] "
            f"(devices={len(unique_devices)}, subnets={len(unique_subnets)}, weight={total_trust_weight:.1f})"
        )
        return {
            "status": "AMNESTY_APPLIED",
            "message": f"Block [{block.range_start}..{block.range_end}] unblocked via independent confirmation quorum",
            "unblocked": True,
            "block_type": block.block_type,
            "range_start": block.range_start,
            "range_end": block.range_end,
        }

    return {
        "status": "AMNESTY_VOTE_RECORDED",
        "message": (
            f"False positive recorded for [{block.range_start}..{block.range_end}]. "
            f"Confirmation quorum: {len(unique_devices)}/2 devices, weight: {total_trust_weight:.1f}/2.0."
        ),
        "unblocked": False,
        "block_type": block.block_type,
        "range_start": block.range_start,
        "range_end": block.range_end,
    }
