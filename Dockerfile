# Production Dockerfile for AntiSpam Backend
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DATA_DIR=/app/data

WORKDIR /app

# Install minimal system packages for health check
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend application source
COPY backend/ ./backend/
COPY README.md .

# Ensure data mount point exists for SQLite WAL persistence
RUN mkdir -p /app/data
VOLUME ["/app/data"]

EXPOSE 8000

# Healthcheck validating FastAPI /api/v1/health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/api/v1/health || exit 1

# CRITICAL ARCHITECTURAL RULE:
# Strictly single-worker mode (--workers 1) to protect in-memory WriteBuffer (asyncio.Queue)
# and guarantee zero-contention atomic WAL version increments in SQLite!
CMD ["uvicorn", "backend.api.app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--proxy-headers", "--forwarded-allow-ips=*"]
