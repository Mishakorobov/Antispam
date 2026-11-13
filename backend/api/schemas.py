from pydantic import BaseModel, Field

class ReportRequest(BaseModel):
    phone_number: str | int = Field(..., description="Phone number to report (e.g. 79031234567 or +7 (495) 123-45-67)")
    device_hash: str = Field(..., min_length=16, max_length=64, description="Anonymous client device SHA-256 hash")
    category: str = Field(default="SPAM", description="Complaint category: SURVEY, SPAM, FINANCIAL, SILENT")
    call_duration_seconds: int | None = Field(default=None, description="Call duration before hangup (if known)")

class ReportResponse(BaseModel):
    status: str
    message: str
    blocked: bool = False
    block_type: str | None = None
    range_start: int | None = None
    range_end: int | None = None
    bucket_100: int | None = None
    country: str | None = None
    geo: str | None = None

class PhoneInfoResponse(BaseModel):
    phone_number: int
    country: str
    geo: str
    is_foreign_plus7: bool
    is_blocked: bool = False
    block_type: str | None = None
    operator: str | None = None

class ReportItem(BaseModel):
    phone_number: str | int
    category: str = "SPAM"
    call_duration_seconds: int | None = None

class BatchReportRequest(BaseModel):
    device_hash: str = Field(..., min_length=16, max_length=64)
    reports: list[ReportItem]

class BatchReportResponse(BaseModel):
    status: str
    total_received: int
    accepted: int
    ignored: int
    blocked_count: int

class BlockItem(BaseModel):
    range_start: int
    range_end: int
    operator: str | None = None
    category: str | None = None
    version: int

class PoolsDeltaResponse(BaseModel):
    current_version: int
    full_sync_required: bool
    has_more: bool
    added: list[BlockItem]
    removed: list[BlockItem]

class WhitelistRangeItem(BaseModel):
    start: int
    end: int
    name: str

class WhitelistGroupResponse(BaseModel):
    group_key: str
    category: str
    is_enabled: bool
    range_count: int
    description: str
    ranges: list[WhitelistRangeItem]

class FalsePositiveRequest(BaseModel):
    phone_number: str | int = Field(..., description="Phone number to report as legitimate/false positive")
    device_hash: str = Field(..., min_length=16, max_length=64, description="Client device SHA-256 hash")
    reason: str = Field(default="OUTGOING_CALL", description="Reason: OUTGOING_CALL, CONTACT_ADDED, MANUAL_AMNESTY")

class FalsePositiveResponse(BaseModel):
    status: str
    message: str
    unblocked: bool = False
    block_type: str | None = None
    range_start: int | None = None
    range_end: int | None = None

class WhitelistToggleRequest(BaseModel):
    enable: bool = Field(..., description="Whether to enable (protect) or disable (allow complaints/blocking) this group")

class PublicStatsResponse(BaseModel):
    active_blocked_pools: int
    total_blocked_numbers: int
    delta_version: int
    total_registered_ranges: int
    whitelist_groups_count: int
