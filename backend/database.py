from typing import Generator
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import NullPool
from backend.config import settings, DEFAULT_DB_PATH
from backend.models import Base

# Ensure parent directory for database exists
DEFAULT_DB_PATH.parent.mkdir(parents=True, exist_ok=True)

connect_args = {}
if settings.database_url.startswith("sqlite"):
    connect_args["check_same_thread"] = False

engine = create_engine(
    settings.database_url,
    connect_args=connect_args,
    pool_pre_ping=True,
    poolclass=NullPool if settings.database_url.startswith("sqlite") else None,
)

# Apply SQLite performance optimizations if using SQLite
if settings.database_url.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode = WAL")
        cursor.execute("PRAGMA synchronous = NORMAL")
        cursor.execute("PRAGMA cache_size = -64000")  # 64MB cache
        cursor.execute("PRAGMA temp_store = MEMORY")
        cursor.close()

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def init_db():
    """Create all tables if they do not exist."""
    Base.metadata.create_all(bind=engine)

def get_db_session() -> Generator[Session, None, None]:
    """Yield a database session and ensure it is closed after request."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
