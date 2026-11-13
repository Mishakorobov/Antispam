import re

# Known Virtual PBX / SIP / Business telephony operators in Russia
SIP_KEYWORDS = [
    "манго",
    "мтт",
    "межрегиональный транзиттелеком",
    "телфин",
    "сипнет",
    "зебра телеком",
    "плюстел",
    "метротел",
    "новосистем",
    "кантриком",
    "связьтранскомпани",
    "телефонные системы",
    "сипстар",
    "грандител",
    "билайн бизнес",
    "мтс автосекретарь",
    "uis",
    "юис",
    "zadarma",
    "задарма",
    "comtube",
    "воксбей",
    "онлайн связь",
    "интернод",
    "айпиматика",
    "смиттелеком",
]

PREFIX_REGEX = re.compile(r'^(ооо|пао|ао|зао|оао|ип)\s+["«\']?|["»\']', re.IGNORECASE)

def clean_operator_name(raw_name: str) -> str:
    """Strip legal form prefixes and quotes to get a clean operator brand."""
    if not raw_name:
        return ""
    name = raw_name.strip()
    name = PREFIX_REGEX.sub("", name).strip()
    # Remove surrounding quotes
    name = name.strip('"\'«»')
    return name

def is_sip_provider(operator_name: str) -> bool:
    """Check if the operator matches known SIP/Virtual PBX providers."""
    if not operator_name:
        return False
    lower = operator_name.lower()
    return any(keyword in lower for keyword in SIP_KEYWORDS)

MAJOR_FEDERAL_OPERATORS = [
    "мтс",
    "мобильные телесистемы",
    "мегафон",
    "вымпелком",
    "билайн",
    "т2 мобайл",
    "теле2",
    "ростелеком",
    "тинькофф",
    "т-мобайл",
    "сбермобайл",
    "транстелеком",
    "ттк",
]

def is_major_federal_operator(operator_name: str) -> bool:
    """Check if the operator is a major federal consumer/mobile telco carrier."""
    if not operator_name:
        return False
    lower = operator_name.lower()
    return any(op in lower for op in MAJOR_FEDERAL_OPERATORS)

def is_b2b_sip_pool(
    operator_name: str,
    capacity: int,
    code: int | None = None,
    total_operator_capacity: int | None = None
) -> bool:
    """
    Determine if a range qualifies as a B2B/SIP small pool:
    1. Belongs to federal toll-free/service codes (800, 804, 809).
    2. Belongs to a known SIP/Virtual PBX brand and capacity <= 10_000.
    3. Belongs to an operator with total national capacity < 50_000 numbers
       and not in the Big 4 / Rostelecom (small regional SIP transit aggregator).
    """
    if code in (800, 804, 809):
        return True
    if is_sip_provider(operator_name):
        return capacity <= 10_000
    if total_operator_capacity is not None and total_operator_capacity < 50_000:
        return not is_major_federal_operator(operator_name)
    return False
