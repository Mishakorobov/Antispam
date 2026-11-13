import time
from datetime import datetime
import logging
from pathlib import Path
from sqlalchemy import text, func
from sqlalchemy.orm import Session
from backend.config import settings
from backend.database import engine, init_db, SessionLocal
from backend.downloader import download_all_registries
from backend.models import RegistryRange, RegistryRangeStaging, RegistrySyncLog, utc_now_naive
from backend.parser import parse_registry_csv
from backend.sip_classifier import is_major_federal_operator

logger = logging.getLogger(__name__)

BATCH_SIZE = 10_000

def score_small_b2b_sip_aggregators(session: Session) -> None:
    """
    Enrich staging table:
    Calculate aggregate capacity per operator across all ranges.
    Any operator with total national capacity < 50,000 numbers
    that is not in the Major Federal Carriers (MTS, MegaFon, Beeline, Tele2, Rostelecom, etc.)
    is automatically classified as a small B2B SIP/transit aggregator (is_b2b_sip = True).
    """
    query = (
        session.query(
            RegistryRangeStaging.inn,
            RegistryRangeStaging.operator_clean,
            RegistryRangeStaging.operator_name,
            func.sum(RegistryRangeStaging.capacity).label("total_cap")
        )
        .group_by(RegistryRangeStaging.inn, RegistryRangeStaging.operator_clean)
        .all()
    )

    small_sip_inns = set()
    small_sip_cleans = set()

    for row in query:
        inn, op_clean, op_raw, total_cap = row[0], row[1], row[2], row[3]
        if total_cap and total_cap < 50_000:
            if not is_major_federal_operator(op_raw) and not is_major_federal_operator(op_clean):
                if inn:
                    small_sip_inns.add(inn)
                elif op_clean:
                    small_sip_cleans.add(op_clean)

    if small_sip_inns:
        inn_list = list(small_sip_inns)
        for i in range(0, len(inn_list), 900):
            chunk = inn_list[i:i + 900]
            session.query(RegistryRangeStaging).filter(
                RegistryRangeStaging.inn.in_(chunk),
                RegistryRangeStaging.is_b2b_sip == False
            ).update({"is_b2b_sip": True}, synchronize_session=False)

    if small_sip_cleans:
        clean_list = list(small_sip_cleans)
        for i in range(0, len(clean_list), 900):
            chunk = clean_list[i:i + 900]
            session.query(RegistryRangeStaging).filter(
                RegistryRangeStaging.operator_clean.in_(chunk),
                RegistryRangeStaging.is_b2b_sip == False
            ).update({"is_b2b_sip": True}, synchronize_session=False)

    session.commit()
    logger.info(f"Scored {len(small_sip_inns)} INNs and {len(small_sip_cleans)} operator brands as small B2B SIP (<50k capacity)")

def run_sync_pipeline(force_download: bool = False, specific_files: dict[str, Path] | None = None) -> dict:
    """
    Execute full ETL pipeline:
    1. Initialize tables.
    2. Download registry CSV files.
    3. Parse & stream into staging table.
    4. Validate records via Data Quality Gate.
    5. Atomically replace data in registry_ranges.
    6. Record sync log.
    """
    init_db()
    session: Session = SessionLocal()

    sync_log = RegistrySyncLog(
        started_at=utc_now_naive(),
        status="IN_PROGRESS"
    )
    session.add(sync_log)
    session.commit()

    start_perf = time.perf_counter()
    records_abc = 0
    records_def = 0
    total_records = 0

    try:
        # Step 1: Download files
        if specific_files:
            downloaded = specific_files
        else:
            downloaded = download_all_registries(force=force_download)

        # Step 2: Clear staging table
        session.query(RegistryRangeStaging).delete()
        session.commit()

        # Step 3: Parse and bulk insert into staging
        batch = []
        for name, filepath in downloaded.items():
            file_count = 0
            is_def = "DEF" in name.upper()

            for record in parse_registry_csv(filepath):
                batch.append(record)
                file_count += 1
                if is_def:
                    records_def += 1
                else:
                    records_abc += 1

                if len(batch) >= BATCH_SIZE:
                    session.bulk_insert_mappings(RegistryRangeStaging, batch)
                    session.commit()
                    batch.clear()

            if batch:
                session.bulk_insert_mappings(RegistryRangeStaging, batch)
                session.commit()
                batch.clear()

            logger.info(f"Loaded {file_count} records from {name} into staging")

        total_records = records_abc + records_def
        logger.info(f"Total staged records: {total_records} (ABC: {records_abc}, DEF: {records_def})")

        # Step 3.5: Score small B2B/SIP aggregators (national capacity < 50,000 numbers)
        score_small_b2b_sip_aggregators(session)

        # Step 4: Data Quality Gate
        staged_count = session.query(func.count(RegistryRangeStaging.id)).scalar()
        if staged_count < 100:  # Minimum sanity threshold
            raise ValueError(f"Data Quality Gate Failed: Too few records ({staged_count}) in staging table.")

        # Step 5: Atomic Swap
        with engine.begin() as conn:
            # Delete existing records in production table
            conn.execute(text("DELETE FROM registry_ranges;"))
            # Copy all validated records from staging to production
            conn.execute(text("""
                INSERT INTO registry_ranges (
                    code_abc_def, range_start, range_end, capacity,
                    operator_name, operator_clean, region, inn, is_b2b_sip, updated_at
                )
                SELECT 
                    code_abc_def, range_start, range_end, capacity,
                    operator_name, operator_clean, region, inn, is_b2b_sip, updated_at
                FROM registry_ranges_staging;
            """))
            # Clean staging table
            conn.execute(text("DELETE FROM registry_ranges_staging;"))

        # Step 6: Log success
        duration = time.perf_counter() - start_perf
        sync_log.status = "SUCCESS"
        sync_log.finished_at = utc_now_naive()
        sync_log.total_records = total_records
        sync_log.records_abc = records_abc
        sync_log.records_def = records_def
        session.commit()

        logger.info(f"Sync successfully completed in {duration:.2f}s with {total_records} records.")

        return {
            "status": "SUCCESS",
            "total_records": total_records,
            "records_abc": records_abc,
            "records_def": records_def,
            "duration_seconds": duration,
        }

    except Exception as e:
        session.rollback()
        sync_log.status = "FAILED"
        sync_log.finished_at = utc_now_naive()
        sync_log.error_message = str(e)
        session.commit()
        logger.error(f"Sync pipeline failed: {e}", exc_info=True)
        return {
            "status": "FAILED",
            "error": str(e),
            "records_staged_before_failure": total_records,
        }
    finally:
        session.close()

_LOOKUP_STMT = text("""
    SELECT code_abc_def, range_start, range_end, capacity, operator_name, operator_clean, region, inn, is_b2b_sip
    FROM registry_ranges
    WHERE code_abc_def = :code AND range_start <= :num AND range_end >= :num
    LIMIT 1
""")

_LOOKUP_FALLBACK_STMT = text("""
    SELECT code_abc_def, range_start, range_end, capacity, operator_name, operator_clean, region, inn, is_b2b_sip
    FROM registry_ranges
    WHERE range_start <= :num AND range_end >= :num
    LIMIT 1
""")

def lookup_phone_number(phone_num: int, session: Session | None = None) -> dict | None:
    """Fast sub-millisecond lookup of phone number in registry_ranges."""
    close_session = False
    if session is None:
        session = SessionLocal()
        close_session = True

    try:
        str_num = str(phone_num)
        if len(str_num) == 11 and str_num.startswith("7"):
            code = int(str_num[1:4])
            row = session.execute(_LOOKUP_STMT, {"code": code, "num": phone_num}).first()
        else:
            row = session.execute(_LOOKUP_FALLBACK_STMT, {"num": phone_num}).first()

        if not row:
            return None
        return {
            "code": row[0],
            "range_start": row[1],
            "range_end": row[2],
            "capacity": row[3],
            "operator_name": row[4],
            "operator_clean": row[5],
            "region": row[6],
            "inn": row[7],
            "is_b2b_sip": bool(row[8]),
        }
    finally:
        if close_session:
            session.close()
