from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.orm import Session
from backend.clustering import get_current_version
from backend.config import settings
from backend.database import get_db_session
from backend.models import ActiveBlock

router = APIRouter(prefix="/api/v1/export", tags=["Export"])

@router.get(
    "/ios",
    responses={
        200: {"description": "Strictly ascending array of 64-bit numbers for Apple CXCallDirectoryExtension"},
        304: {"description": "Not Modified: CallKit database is already up to date"}
    }
)
def export_ios_call_directory(
    format: str = Query("txt", pattern="^(txt|json)$", description="Format: 'txt' (one number per line) or 'json'"),
    since_version: int = Query(0, ge=0, description="Last synchronized delta version"),
    limit: int = Query(50_000, ge=1, le=100_000, description="Maximum total phone numbers to export (capped at 100k for Apple CallKit 50MB limit)"),
    db: Session = Depends(get_db_session)
):
    """
    Apple iOS CallKit CXCallDirectoryExtension Export Endpoint.
    Generates a strictly ascending, deduplicated sequence of int64 phone numbers
    suitable for CXCallDirectoryExtensionContext.addBlockingEntry(withNextSequentialPhoneNumber:).
    Returns HTTP 304 Not Modified when version has not changed.
    Edge Case 4: Applies smart density-based prioritization and strict 100k capping
    to ensure the iOS extension stays well below Apple's 50MB memory ceiling.
    """
    current_ver = get_current_version(db)
    if since_version > 0 and since_version == current_ver:
        return Response(status_code=status.HTTP_304_NOT_MODIFIED)

    # Fetch active blocks
    raw_blocks = (
        db.query(ActiveBlock)
        .filter(ActiveBlock.status == "ACTIVE")
        .all()
    )

    # Edge Case 4 Prioritization:
    # 1. Smaller, higher-precision pools first (SINGLE_NUMBER span 1, MICRO_100 span 100 before SUBPOOL_1000)
    # 2. Repeat offenders (repeat_count desc) and higher confidence score
    def block_priority_key(b: ActiveBlock):
        span = (b.range_end - b.range_start) + 1
        # Priority tier: single=0, micro_100=1, others=2
        tier = 0 if span == 1 else (1 if span <= 100 else 2)
        return (tier, -b.repeat_count, -b.confidence_score, b.range_start)

    sorted_blocks = sorted(raw_blocks, key=block_priority_key)

    # Select numbers from prioritized blocks up to limit
    selected_set: set[int] = set()
    for b in sorted_blocks:
        if len(selected_set) >= limit:
            break
        span = (b.range_end - b.range_start) + 1
        if span == 1:
            selected_set.add(b.range_start)
        else:
            for num in range(b.range_start, b.range_end + 1):
                selected_set.add(num)
                if len(selected_set) >= limit:
                    break

    # Strictly ascending sorted list for Apple CallKit requirement
    numbers = sorted(selected_set)

    headers = {
        "X-CallKit-Version": str(current_ver),
        "X-CallKit-Count": str(len(numbers)),
        "ETag": f'"v{current_ver}"'
    }

    if format == "json":
        return Response(
            content=f'{{"version": {current_ver}, "count": {len(numbers)}, "numbers": {numbers}}}',
            media_type="application/json",
            headers=headers
        )
    else:
        # Plain text: one int64 number per line (optimal for iOS StreamReader)
        body = "\n".join(str(n) for n in numbers)
        return Response(
            content=body,
            media_type="text/plain; charset=utf-8",
            headers=headers
        )
