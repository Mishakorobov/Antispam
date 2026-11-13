import struct
from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session
from backend.api.schemas import BlockItem, PoolsDeltaResponse, PhoneInfoResponse
from backend.clustering import get_current_version
from backend.database import get_db_session
from backend.models import ActiveBlock, BlockDelta, RegistryRange
from backend.normalizer import (
    get_country_and_code,
    is_kazakhstan_number,
    normalize_incoming_phone,
)

router = APIRouter(prefix="/api/v1", tags=["Pools"])

MAX_ALLOWED_DELTA_GAP = 15_000

from backend.aspb import CATEGORY_MAP, build_aspb_payload

@router.get(
    "/pools",
    response_model=PoolsDeltaResponse,
    responses={
        200: {"model": PoolsDeltaResponse, "description": "Delta or full sync payload"},
        304: {"description": "Not Modified: Client is already up-to-date with current version"}
    }
)
def get_pools_delta(
    since: int = Query(0, ge=0, description="Last version seen by client"),
    limit: int = Query(5000, ge=1, le=20000, description="Max deltas to return in one batch"),
    db: Session = Depends(get_db_session)
):
    """
    Incremental delta sync for Android clients:
    Returns HTTP 304 Not Modified when client is already up to date.
    Returns newly added and removed/expired blocked ranges since client's version.
    """
    current_ver = get_current_version(db)

    # HTTP 304 Optimization: Zero traffic when versions match
    if since > 0 and since == current_ver:
        return Response(status_code=304)

    # Full sync trigger: clean install (since=0) or gap is too large
    if since == 0 or (current_ver - since) > MAX_ALLOWED_DELTA_GAP:
        active_blocks = (
            db.query(ActiveBlock)
            .filter(ActiveBlock.status == "ACTIVE")
            .limit(limit)
            .all()
        )
        added_items = [
            BlockItem(
                range_start=b.range_start,
                range_end=b.range_end,
                operator=b.operator_name,
                category=b.category,
                version=b.version,
            )
            for b in active_blocks
        ]
        return PoolsDeltaResponse(
            current_version=current_ver,
            full_sync_required=True,
            has_more=len(added_items) == limit,
            added=added_items,
            removed=[],
        )

    # Incremental sync from block_deltas
    deltas = (
        db.query(BlockDelta)
        .filter(BlockDelta.version > since)
        .order_by(BlockDelta.version.asc())
        .limit(limit + 1)
        .all()
    )

    has_more = len(deltas) > limit
    returned_deltas = deltas[:limit]

    added = []
    removed = []
    for d in returned_deltas:
        item = BlockItem(
            range_start=d.range_start,
            range_end=d.range_end,
            operator=d.operator_name,
            category=d.category,
            version=d.version,
        )
        if d.action == "ADD":
            added.append(item)
        elif d.action == "REMOVE":
            removed.append(item)

    return PoolsDeltaResponse(
        current_version=current_ver,
        full_sync_required=False,
        has_more=has_more,
        added=added,
        removed=removed,
    )

@router.get(
    "/pools/binary",
    responses={
        200: {"description": "Compact binary delta payload (ASPB protocol)"},
        304: {"description": "Not Modified"}
    }
)
def get_pools_binary(
    since: int = Query(0, ge=0, description="Last version seen by client"),
    limit: int = Query(5000, ge=1, le=20000, description="Max deltas to return in one batch"),
    db: Session = Depends(get_db_session)
):
    """
    High-density binary delta endpoint (ASPB Protocol):
    Transfers sorted int64 intervals in big-endian bytes, reducing payload size by 6x–8x
    and parsing overhead to near zero on mobile devices.
    """
    current_ver = get_current_version(db)

    # HTTP 304 Optimization
    if since > 0 and since == current_ver:
        return Response(status_code=304)

    is_full_sync = since == 0 or (current_ver - since) > MAX_ALLOWED_DELTA_GAP

    if is_full_sync:
        active_blocks = (
            db.query(ActiveBlock)
            .filter(ActiveBlock.status == "ACTIVE")
            .limit(limit)
            .all()
        )
        has_more = len(active_blocks) == limit
        added_records = [
            (b.range_start, b.range_end, CATEGORY_MAP.get(b.category, 0), b.version)
            for b in active_blocks
        ]
        removed_records = []
    else:
        deltas = (
            db.query(BlockDelta)
            .filter(BlockDelta.version > since)
            .order_by(BlockDelta.version.asc())
            .limit(limit + 1)
            .all()
        )
        has_more = len(deltas) > limit
        returned = deltas[:limit]
        added_records = [
            (d.range_start, d.range_end, CATEGORY_MAP.get(d.category, 0), d.version)
            for d in returned if d.action == "ADD"
        ]
        removed_records = [
            (d.range_start, d.range_end, d.version)
            for d in returned if d.action == "REMOVE"
        ]

    payload = build_aspb_payload(
        current_ver=current_ver,
        is_full_sync=is_full_sync,
        has_more=has_more,
        added_records=added_records,
        removed_records=removed_records,
    )
    return Response(
        content=payload,
        media_type="application/octet-stream",
        headers={
            "X-ASPB-Version": str(current_ver),
            "X-ASPB-Added": str(len(added_records)),
            "X-ASPB-Removed": str(len(removed_records)),
        }
    )

@router.get("/phone/{phone_number}/info", response_model=PhoneInfoResponse)
def get_phone_info(phone_number: str, db: Session = Depends(get_db_session)):
    """
    Query phone metadata, geographic origin (Russia / Kazakhstan),
    and whether it is currently covered by active blocks or registries.
    """
    norm = normalize_incoming_phone(phone_number)
    if not norm:
        raise HTTPException(status_code=422, detail="Invalid phone number format")

    country, code = get_country_and_code(norm)
    is_kz = is_kazakhstan_number(norm)
    geo_name = "Казахстан" if is_kz else ("Россия" if country == "RU" else "Неизвестно")

    block = (
        db.query(ActiveBlock)
        .filter(
            ActiveBlock.status == "ACTIVE",
            ActiveBlock.range_start <= norm,
            ActiveBlock.range_end >= norm,
        )
        .first()
    )

    reg = (
        db.query(RegistryRange)
        .filter(
            RegistryRange.range_start <= norm,
            RegistryRange.range_end >= norm,
        )
        .first()
    )

    return PhoneInfoResponse(
        phone_number=norm,
        country=country,
        geo=geo_name,
        is_foreign_plus7=is_kz,
        is_blocked=block is not None,
        block_type=block.block_type if block else None,
        operator=reg.operator_clean if reg else (block.operator_name if block else None),
    )
