from datetime import datetime, timezone
from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    DateTime,
    Float,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.orm import declarative_base

Base = declarative_base()

def utc_now_naive():
    return datetime.now(timezone.utc).replace(tzinfo=None)

# --- Stage 1 Models ---

class RegistrySyncLog(Base):
    """Audit log of registry synchronizations."""
    __tablename__ = "registry_sync_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    started_at = Column(DateTime, default=utc_now_naive, nullable=False)
    finished_at = Column(DateTime, nullable=True)
    status = Column(String(32), nullable=False)  # 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    total_records = Column(Integer, default=0)
    records_abc = Column(Integer, default=0)
    records_def = Column(Integer, default=0)
    error_message = Column(Text, nullable=True)


class RegistryRangeBase:
    """Shared columns for primary and staging tables."""
    id = Column(Integer, primary_key=True, autoincrement=True)
    code_abc_def = Column(Integer, nullable=False, index=True)
    range_start = Column(BigInteger, nullable=False)
    range_end = Column(BigInteger, nullable=False)
    capacity = Column(Integer, nullable=False)
    operator_name = Column(Text, nullable=False)
    operator_clean = Column(Text, nullable=True)
    region = Column(Text, nullable=True)
    inn = Column(String(16), nullable=True)
    is_b2b_sip = Column(Boolean, default=False, nullable=False)
    updated_at = Column(DateTime, default=utc_now_naive, nullable=False)


class RegistryRange(Base, RegistryRangeBase):
    """Main lookup table for phone numbering ranges."""
    __tablename__ = "registry_ranges"

    __table_args__ = (
        Index("idx_registry_range_lookup", "range_start", "range_end"),
        Index("idx_registry_code_lookup", "code_abc_def", "range_start", "range_end"),
        Index("idx_registry_operator", "operator_clean"),
    )


class RegistryRangeStaging(Base, RegistryRangeBase):
    """Staging table for atomic swaps during import."""
    __tablename__ = "registry_ranges_staging"

    __table_args__ = (
        Index("idx_staging_code_lookup", "code_abc_def", "range_start", "range_end"),
    )


# --- Stage 2 Models: Reports, Clustering, Whitelist, Deltas ---

class UserReport(Base):
    """Raw crowd-sourced spam/survey complaints from clients."""
    __tablename__ = "user_reports"

    id = Column(Integer, primary_key=True, autoincrement=True)
    phone_number = Column(BigInteger, nullable=False, index=True)
    bucket_100 = Column(BigInteger, nullable=False)
    bucket_1000 = Column(BigInteger, nullable=False)
    device_hash = Column(String(64), nullable=False)
    client_ip = Column(String(45), nullable=True)
    subnet_prefix = Column(String(45), nullable=True)  # /24 for IPv4, /48 for IPv6
    category = Column(String(32), default="SPAM", nullable=False)
    call_duration_seconds = Column(Integer, nullable=True)
    created_at = Column(DateTime, default=utc_now_naive, nullable=False)
    report_day = Column(String(10), nullable=False)  # 'YYYY-MM-DD' for anti-poisoning

    __table_args__ = (
        # Anti-poisoning: 1 vote per device per bucket_100 per day
        Index("uidx_device_bucket_day", "device_hash", "bucket_100", "report_day", unique=True),
        Index("idx_reports_bucket_100_time", "bucket_100", "created_at"),
        Index("idx_reports_bucket_1000_time", "bucket_1000", "created_at"),
        Index("idx_reports_device_day", "device_hash", "report_day"),
        Index("idx_reports_subnet_bucket_day", "subnet_prefix", "bucket_100", "report_day"),
    )


class DeviceReputation(Base):
    """Track reputation and trust score of reporting devices to combat Sybil attacks."""
    __tablename__ = "device_reputations"

    device_hash = Column(String(64), primary_key=True)
    trust_score = Column(Float, default=0.3, nullable=False)  # Range [0.1, 1.0]
    reports_count = Column(Integer, default=0, nullable=False)
    confirmed_count = Column(Integer, default=0, nullable=False)
    first_seen_at = Column(DateTime, default=utc_now_naive, nullable=False)
    last_seen_at = Column(DateTime, default=utc_now_naive, nullable=False)


class FalsePositiveReport(Base):
    """Audit log of user amnesty / false positive reports (outgoing calls, whitelist additions)."""
    __tablename__ = "false_positive_reports"

    id = Column(Integer, primary_key=True, autoincrement=True)
    phone_number = Column(BigInteger, nullable=False, index=True)
    device_hash = Column(String(64), nullable=False)
    client_ip = Column(String(45), nullable=True)
    subnet_prefix = Column(String(45), nullable=True)  # /24 for IPv4, /48 for IPv6
    trust_weight = Column(Float, default=1.0, nullable=False)
    reason = Column(String(32), default="OUTGOING_CALL", nullable=False)
    created_at = Column(DateTime, default=utc_now_naive, nullable=False)

    __table_args__ = (
        Index("idx_fp_phone_time", "phone_number", "created_at"),
        Index("idx_fp_device_phone", "device_hash", "phone_number"),
    )


class ActiveBlock(Base):
    """Active blocked pool rules generated by the clustering engine."""
    __tablename__ = "active_blocks"

    id = Column(Integer, primary_key=True, autoincrement=True)
    range_start = Column(BigInteger, nullable=False)
    range_end = Column(BigInteger, nullable=False)
    block_type = Column(String(24), nullable=False)  # 'MICRO_100', 'SUBPOOL_1000', 'SIP_OFFICIAL'
    operator_name = Column(Text, nullable=True)
    category = Column(String(32), default="SPAM", nullable=False)
    confidence_score = Column(Integer, default=100, nullable=False)
    created_at = Column(DateTime, default=utc_now_naive, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    repeat_count = Column(Integer, default=1, nullable=False)
    status = Column(String(16), default="ACTIVE", nullable=False)  # 'ACTIVE', 'EXPIRED', 'WHITELISTED'
    version = Column(BigInteger, nullable=False)

    __table_args__ = (
        Index("uidx_active_blocks_range", "range_start", "range_end", unique=True),
        Index("idx_active_blocks_version", "version"),
        Index("idx_active_blocks_status_expires", "status", "expires_at"),
    )


class BlockDelta(Base):
    """Changelog (deltas) of additions and removals for incremental client syncing."""
    __tablename__ = "block_deltas"

    id = Column(Integer, primary_key=True, autoincrement=True)
    version = Column(BigInteger, nullable=False, index=True)
    action = Column(String(8), nullable=False)  # 'ADD' or 'REMOVE'
    range_start = Column(BigInteger, nullable=False)
    range_end = Column(BigInteger, nullable=False)
    operator_name = Column(Text, nullable=True)
    category = Column(String(32), nullable=True)
    created_at = Column(DateTime, default=utc_now_naive, nullable=False)


class ServerWhitelist(Base):
    """
    Server-level whitelist protecting essential services.
    Supports organization groups (TBANK, SBER, GOV, etc.) that can be toggled on/off.
    """
    __tablename__ = "server_whitelist"

    id = Column(Integer, primary_key=True, autoincrement=True)
    range_start = Column(BigInteger, nullable=False)
    range_end = Column(BigInteger, nullable=False)
    organization_name = Column(Text, nullable=False)
    group_key = Column(String(32), nullable=False)  # 'TBANK', 'SBER', 'GOV', 'EMERGENCY'
    category = Column(String(32), nullable=False)   # 'BANK', 'EMERGENCY', 'GOV', 'DELIVERY'
    is_enabled = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=utc_now_naive, nullable=False)

    __table_args__ = (
        Index("idx_whitelist_range_lookup", "range_start", "range_end"),
        Index("idx_whitelist_group_key", "group_key"),
    )


class SystemMetadata(Base):
    """Key-value metadata storage, including global delta version counter."""
    __tablename__ = "system_metadata"

    key = Column(String(64), primary_key=True)
    value_int = Column(BigInteger, nullable=True)
    value_str = Column(Text, nullable=True)
    updated_at = Column(DateTime, default=utc_now_naive, nullable=False)
