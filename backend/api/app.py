import asyncio
from contextlib import asynccontextmanager
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from backend.api.routes_export import router as export_router
from backend.api.routes_pools import router as pools_router
from backend.api.routes_reports import router as reports_router
from backend.api.routes_stats import router as stats_router
from backend.api.routes_whitelist import router as whitelist_router
from backend.buffer import write_buffer
from backend.database import SessionLocal, init_db
from backend.ttl_worker import start_ttl_worker_loop
from backend.whitelist import seed_default_whitelist

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for database initialization and background workers."""
    # Startup
    init_db()
    session = SessionLocal()
    try:
        seed_default_whitelist(session)
    finally:
        session.close()

    # Start background TTL worker and in-memory WriteBuffer workers
    worker_task = asyncio.create_task(start_ttl_worker_loop(interval_seconds=60))
    write_buffer.start()
    yield
    # Shutdown
    await write_buffer.stop()
    worker_task.cancel()
    try:
        await worker_task
    except asyncio.CancelledError:
        logger.info("Background TTL worker stopped.")

def create_app() -> FastAPI:
    app = FastAPI(
        title="AntiSpam Shield: Блокировка спама и защита от прослушки API",
        description="REST API for AntiSpam Shield: Блокировка спама и защита от прослушки crowd-sourced spam complaints and numbering pool synchronization",
        version="1.2.0",
        lifespan=lifespan,
    )

    # CORS configuration for web and Android emulators
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Register routers
    app.include_router(reports_router)
    app.include_router(pools_router)
    app.include_router(whitelist_router)
    app.include_router(stats_router)
    app.include_router(export_router)

    return app

app = create_app()
