from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session
from backend.api.schemas import PublicStatsResponse
from backend.clustering import get_current_version
from backend.database import get_db_session
from backend.models import ActiveBlock, RegistryRange, ServerWhitelist

router = APIRouter(prefix="/api/v1", tags=["Stats"])

@router.get("/health")
def healthcheck():
    return {"status": "ok", "service": "antispam-backend"}

@router.get("/stats/public", response_model=PublicStatsResponse)
def get_public_stats(db: Session = Depends(get_db_session)):
    """Public metrics regarding blocked spam pools and registry coverage."""
    active_pools = (
        db.query(func.count(ActiveBlock.id))
        .filter(ActiveBlock.status == "ACTIVE")
        .scalar() or 0
    )

    # Calculate total blocked individual phone numbers
    active_ranges = (
        db.query(ActiveBlock.range_start, ActiveBlock.range_end)
        .filter(ActiveBlock.status == "ACTIVE")
        .all()
    )
    total_blocked_numbers = sum(r[1] - r[0] + 1 for r in active_ranges)

    total_registered = db.query(func.count(RegistryRange.id)).scalar() or 0
    whitelist_groups = db.query(ServerWhitelist.group_key).distinct().count()

    return PublicStatsResponse(
        active_blocked_pools=active_pools,
        total_blocked_numbers=total_blocked_numbers,
        delta_version=get_current_version(db),
        total_registered_ranges=total_registered,
        whitelist_groups_count=whitelist_groups,
    )
