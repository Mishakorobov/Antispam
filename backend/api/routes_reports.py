from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.orm import Session
from backend.api.schemas import (
    BatchReportRequest,
    BatchReportResponse,
    FalsePositiveRequest,
    FalsePositiveResponse,
    ReportRequest,
    ReportResponse,
)
from backend.buffer import BufferedReportItem, write_buffer
from backend.clustering import (
    evaluate_report,
    handle_false_positive_report,
    normalize_incoming_phone,
)
from backend.database import get_db_session

router = APIRouter(prefix="/api/v1", tags=["Reports"])

def get_client_ip(request: Request) -> str | None:
    """
    Extract the real client IP address when running behind reverse proxies
    (Cloudflare, Nginx, Caddy, AWS ALB).
    Priority:
    1. CF-Connecting-IP (Cloudflare authenticated origin IP)
    2. X-Forwarded-For (first IP in client, proxy1, proxy2 chain)
    3. X-Real-IP (standard Nginx proxy header)
    4. request.client.host (fallback direct socket address)
    """
    cf_ip = request.headers.get("cf-connecting-ip")
    if cf_ip and cf_ip.strip():
        return cf_ip.strip()

    forwarded = request.headers.get("x-forwarded-for")
    if forwarded and forwarded.strip():
        first_ip = forwarded.split(",")[0].strip()
        if ":" in first_ip and not first_ip.startswith("[") and first_ip.count(":") == 1:
            first_ip = first_ip.split(":")[0].strip()
        if first_ip:
            return first_ip

    real_ip = request.headers.get("x-real-ip")
    if real_ip and real_ip.strip():
        clean_real = real_ip.strip()
        if ":" in clean_real and not clean_real.startswith("[") and clean_real.count(":") == 1:
            clean_real = clean_real.split(":")[0].strip()
        return clean_real

    return request.client.host if request.client else None

@router.post("/report", response_model=ReportResponse)
def submit_report(
    payload: ReportRequest,
    request: Request,
    buffered: bool = Query(False, description="Use in-memory WriteBuffer for high-concurrency ingestion"),
    db: Session = Depends(get_db_session)
):
    """
    Submit a single spam / survey complaint.
    Supports subnet rate limiting and device trust score evaluation.
    """
    norm_phone = normalize_incoming_phone(payload.phone_number)
    if not norm_phone:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid phone number '{payload.phone_number}'. Expected Russian 11-digit format."
        )

    client_ip = get_client_ip(request)

    if buffered and write_buffer.is_running():
        enqueued = write_buffer.enqueue(
            BufferedReportItem(
                phone_number=norm_phone,
                device_hash=payload.device_hash.lower().strip(),
                category=payload.category.upper().strip(),
                call_duration=payload.call_duration_seconds,
                client_ip=client_ip,
            )
        )
        if enqueued:
            return ReportResponse(
                status="QUEUED",
                message="Report buffered for background batch processing",
                blocked=False,
            )

    result = evaluate_report(
        phone_number=norm_phone,
        device_hash=payload.device_hash.lower().strip(),
        category=payload.category.upper().strip(),
        call_duration=payload.call_duration_seconds,
        client_ip=client_ip,
        session=db,
    )
    return ReportResponse(**result)

@router.post("/reports/batch", response_model=BatchReportResponse)
def submit_reports_batch(
    payload: BatchReportRequest,
    request: Request,
    db: Session = Depends(get_db_session)
):
    """Submit a batch of accumulated offline complaints (e.g. from Android WorkManager)."""
    device_hash = payload.device_hash.lower().strip()
    client_ip = get_client_ip(request)
    accepted = 0
    ignored = 0
    blocked_count = 0

    for item in payload.reports:
        norm_phone = normalize_incoming_phone(item.phone_number)
        if not norm_phone:
            ignored += 1
            continue

        res = evaluate_report(
            phone_number=norm_phone,
            device_hash=device_hash,
            category=item.category.upper().strip(),
            call_duration=item.call_duration_seconds,
            client_ip=client_ip,
            session=db,
        )
        if res["status"] == "ACCEPTED":
            accepted += 1
            if res.get("blocked"):
                blocked_count += 1
        else:
            ignored += 1

    return BatchReportResponse(
        status="PROCESSED",
        total_received=len(payload.reports),
        accepted=accepted,
        ignored=ignored,
        blocked_count=blocked_count,
    )

@router.post("/report/false-positive", response_model=FalsePositiveResponse)
def submit_false_positive(
    payload: FalsePositiveRequest,
    request: Request,
    db: Session = Depends(get_db_session)
):
    """
    Submit user false positive / amnesty feedback:
    Triggered when user makes outgoing call to number or adds it to contacts.
    Lifts blocks when confirmation quorum from independent devices/subnets is reached.
    """
    norm_phone = normalize_incoming_phone(payload.phone_number)
    if not norm_phone:
        raise HTTPException(
            status_code=422,
            detail=f"Invalid phone number '{payload.phone_number}'."
        )

    client_ip = get_client_ip(request)

    res = handle_false_positive_report(
        phone_number=norm_phone,
        device_hash=payload.device_hash.lower().strip(),
        reason=payload.reason.upper().strip(),
        session=db,
        client_ip=client_ip,
    )
    return FalsePositiveResponse(**res)
