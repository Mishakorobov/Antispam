import asyncio
from datetime import datetime, timedelta
import logging
from sqlalchemy.orm import Session
from backend.clustering import get_next_version
from backend.database import SessionLocal
from backend.models import ActiveBlock, BlockDelta, UserReport, utc_now_naive

logger = logging.getLogger(__name__)

def run_ttl_cleanup(session: Session) -> dict:
    """
    Check for expired blocks and remove them:
    - Finds active blocks where expires_at <= now.
    - Transitions them to EXPIRED.
    - Registers REMOVE action in block_deltas with new delta_version.
    - Purges raw user reports older than 30 days.
    """
    now = utc_now_naive()
    expired_blocks = (
        session.query(ActiveBlock)
        .filter(ActiveBlock.status == "ACTIVE", ActiveBlock.expires_at <= now)
        .all()
    )

    removed_count = 0
    for block in expired_blocks:
        v = get_next_version(session)
        block.status = "EXPIRED"
        block.version = v

        delta = BlockDelta(
            version=v,
            action="REMOVE",
            range_start=block.range_start,
            range_end=block.range_end,
            operator_name=block.operator_name,
            category=block.category,
            created_at=now,
        )
        session.add(delta)
        removed_count += 1
        logger.info(f"UNBLOCKED (TTL EXPIRED): [{block.range_start}..{block.range_end}] version={v}")

    # Purge old raw reports (> 30 days) to keep database lean
    cutoff_date = now - timedelta(days=30)
    purged_reports = (
        session.query(UserReport)
        .filter(UserReport.created_at < cutoff_date)
        .delete()
    )

    session.commit()
    return {
        "expired_blocks_removed": removed_count,
        "old_reports_purged": purged_reports,
    }

async def start_ttl_worker_loop(interval_seconds: int = 60):
    """Asynchronous background loop running inside FastAPI lifespan."""
    logger.info(f"Background TTL Worker started (interval: {interval_seconds}s)")
    while True:
        try:
            session = SessionLocal()
            try:
                result = run_ttl_cleanup(session)
                if result["expired_blocks_removed"] > 0:
                    logger.info(f"TTL Worker completed: {result}")
            finally:
                session.close()
        except Exception as e:
            logger.error(f"Error in TTL worker loop: {e}", exc_info=True)

        await asyncio.sleep(interval_seconds)
