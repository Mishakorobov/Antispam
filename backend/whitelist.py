import logging
from sqlalchemy.orm import Session
from backend.models import ServerWhitelist, utc_now_naive

logger = logging.getLogger(__name__)

# Initial default whitelist seeds
INITIAL_WHITELIST_DATA = [
    # --- Экстренные службы ---
    {
        "group_key": "EMERGENCY",
        "organization_name": "Единая служба спасения (112)",
        "category": "EMERGENCY",
        "range_start": 70000000112,
        "range_end": 70000000112,
    },
    {
        "group_key": "EMERGENCY",
        "organization_name": "Службы 101, 102, 103, 104",
        "category": "EMERGENCY",
        "range_start": 70000000101,
        "range_end": 70000000104,
    },
    # --- Государственные сервисы ---
    {
        "group_key": "GOV",
        "organization_name": "Госуслуги (Короткие номера 0919, 0920)",
        "category": "GOV",
        "range_start": 70000000919,
        "range_end": 70000000920,
    },
    {
        "group_key": "GOV",
        "organization_name": "Госуслуги (Горячая линия 8-800-100-70-10)",
        "category": "GOV",
        "range_start": 78001007010,
        "range_end": 78001007010,
    },
    {
        "group_key": "GOV",
        "organization_name": "Социальный фонд России (8-800-100-00-01)",
        "category": "GOV",
        "range_start": 78001000001,
        "range_end": 78001000001,
    },
    # --- Сбербанк ---
    {
        "group_key": "SBERBANK",
        "organization_name": "Сбербанк (Сервисный номер 900)",
        "category": "BANK",
        "range_start": 70000000900,
        "range_end": 70000000900,
    },
    {
        "group_key": "SBERBANK",
        "organization_name": "Сбербанк (Горячая линия 8-800-555-55-50)",
        "category": "BANK",
        "range_start": 78005555550,
        "range_end": 78005555550,
    },
    {
        "group_key": "SBERBANK",
        "organization_name": "Сбербанк Москва (+7-495-500-55-50)",
        "category": "BANK",
        "range_start": 74955005550,
        "range_end": 74955005550,
    },
    # --- Т-Банк (Т-Технологии) ---
    {
        "group_key": "TBANK",
        "organization_name": "Т-Банк (Поддержка 8-800-555-77-78)",
        "category": "BANK",
        "range_start": 78005557778,
        "range_end": 78005557778,
    },
    {
        "group_key": "TBANK",
        "organization_name": "Т-Банк (Линия 8-800-755-77-78)",
        "category": "BANK",
        "range_start": 78007557778,
        "range_end": 78007557778,
    },
    {
        "group_key": "TBANK",
        "organization_name": "Т-Банк (Линия 8-800-333-33-33)",
        "category": "BANK",
        "range_start": 78003333333,
        "range_end": 78003333333,
    },
    {
        "group_key": "TBANK",
        "organization_name": "Т-Банк Москва (+7-495-648-11-11)",
        "category": "BANK",
        "range_start": 74956481111,
        "range_end": 74956481111,
    },
    # --- ВТБ ---
    {
        "group_key": "VTB",
        "organization_name": "ВТБ (Короткий номер 1000)",
        "category": "BANK",
        "range_start": 70000001000,
        "range_end": 70000001000,
    },
    {
        "group_key": "VTB",
        "organization_name": "ВТБ (Горячая линия 8-800-100-24-24)",
        "category": "BANK",
        "range_start": 78001002424,
        "range_end": 78001002424,
    },
    # --- Альфа-Банк ---
    {
        "group_key": "ALFABANK",
        "organization_name": "Альфа-Банк (Горячая линия 8-800-200-00-00)",
        "category": "BANK",
        "range_start": 78002000000,
        "range_end": 78002000000,
    },
    {
        "group_key": "ALFABANK",
        "organization_name": "Альфа-Банк Москва (+7-495-788-88-78)",
        "category": "BANK",
        "range_start": 74957888878,
        "range_end": 74957888878,
    },
]

def seed_default_whitelist(session: Session) -> int:
    """Pre-seed essential whitelist groups if table is empty."""
    count = session.query(ServerWhitelist).count()
    if count > 0:
        return 0

    inserted = 0
    for item in INITIAL_WHITELIST_DATA:
        record = ServerWhitelist(
            group_key=item["group_key"],
            organization_name=item["organization_name"],
            category=item["category"],
            range_start=item["range_start"],
            range_end=item["range_end"],
            is_enabled=True,
            created_at=utc_now_naive(),
        )
        session.add(record)
        inserted += 1

    session.commit()
    logger.info(f"Seeded {inserted} default Server Whitelist records.")
    return inserted

def is_number_whitelisted(phone_num: int, session: Session) -> tuple[bool, ServerWhitelist | None]:
    """
    Check if a number falls into an ENABLED whitelist range.
    Disabled groups (e.g. if user/admin turned off TBANK) will return False.
    """
    row = (
        session.query(ServerWhitelist)
        .filter(
            ServerWhitelist.is_enabled == True,
            ServerWhitelist.range_start <= phone_num,
            ServerWhitelist.range_end >= phone_num,
        )
        .first()
    )
    if row:
        return True, row
    return False, None

def set_whitelist_group_status(group_key: str, enable: bool, session: Session) -> int:
    """Toggle an entire organization group on or off (e.g. TBANK, SBERBANK)."""
    norm_key = group_key.upper().strip()
    updated = (
        session.query(ServerWhitelist)
        .filter(ServerWhitelist.group_key == norm_key)
        .update({"is_enabled": enable})
    )
    session.commit()
    logger.info(f"Updated group {norm_key} is_enabled={enable} (affected {updated} ranges)")
    return updated

def get_whitelist_groups_summary(session: Session) -> list[dict]:
    """Get list of whitelist groups with status and number of ranges."""
    groups = (
        session.query(
            ServerWhitelist.group_key,
            ServerWhitelist.category,
            ServerWhitelist.is_enabled,
        )
        .distinct()
        .all()
    )

    result = []
    for g in groups:
        ranges = (
            session.query(ServerWhitelist)
            .filter(ServerWhitelist.group_key == g.group_key)
            .all()
        )
        names = list({r.organization_name for r in ranges})
        result.append({
            "group_key": g.group_key,
            "category": g.category,
            "is_enabled": g.is_enabled,
            "range_count": len(ranges),
            "description": "; ".join(names),
            "ranges": [{"start": r.range_start, "end": r.range_end, "name": r.organization_name} for r in ranges],
        })
    return result
