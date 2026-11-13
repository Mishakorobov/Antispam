from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from backend.api.deps import require_admin_auth
from backend.api.schemas import WhitelistGroupResponse, WhitelistToggleRequest
from backend.database import get_db_session
from backend.whitelist import (
    get_whitelist_groups_summary,
    set_whitelist_group_status,
)

router = APIRouter(prefix="/api/v1/whitelist", tags=["Whitelist"])

@router.get("/groups", response_model=list[WhitelistGroupResponse])
def list_whitelist_groups(db: Session = Depends(get_db_session)):
    """List all Server Whitelist organization groups and their enabled status."""
    return get_whitelist_groups_summary(db)

@router.post("/groups/{group_key}/toggle")
def toggle_whitelist_group(
    group_key: str,
    payload: WhitelistToggleRequest,
    db: Session = Depends(get_db_session),
    _admin: str = Depends(require_admin_auth),
):
    """
    Enable or disable an entire whitelist group (e.g. TBANK, SBERBANK).
    Requires administrative authentication (X-Admin-Api-Key).
    When disabled, numbers in that group lose protection and can be reported/blocked.
    """
    updated_count = set_whitelist_group_status(group_key, payload.enable, db)
    if updated_count == 0:
        raise HTTPException(
            status_code=404,
            detail=f"Whitelist group '{group_key}' not found."
        )
    return {
        "status": "UPDATED",
        "group_key": group_key.upper(),
        "is_enabled": payload.enable,
        "affected_ranges": updated_count,
    }
