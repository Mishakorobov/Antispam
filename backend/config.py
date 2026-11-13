import os
from pathlib import Path
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
CACHE_DIR = DATA_DIR / "downloads"
DEFAULT_DB_PATH = DATA_DIR / "antispam_registry.db"

class Settings(BaseModel):
    # Database configuration (SQLite by default for Android offline compatibility; can be swapped with PostgreSQL)
    database_url: str = f"sqlite:///{DEFAULT_DB_PATH.as_posix()}"

    # Security & Admin Authentication
    admin_api_key: str = os.getenv("ANTISPAM_ADMIN_API_KEY", "antispam-admin-secret-key-change-in-prod")

    # Phase 2 & Phase 5: Anti-Abuse, Sybil Defense, CGNAT allowance & Clustering Thresholds
    default_device_trust_score: float = 1.0
    min_weight_threshold: float = 3.0
    min_distinct_def_for_hundred: int = 6
    max_reports_per_subnet_day: int = 4  # Edge Case 3: CGNAT mobile networks allowance (up to 4 independent devices)

    # Phase 4 & Phase 5: Velocity Spikes & iOS CallKit Export (Apple 50MB memory ceiling)
    velocity_window_minutes: int = 15
    velocity_weight_threshold: float = 1.8
    ios_export_max_numbers: int = 100_000  # Edge Case 4: Capped at 100k numbers to stay well below 50MB CallKit limit

    # Phase 6.3: Trusted Seed Devices ("Founder Mode" for bootstrap fleets)
    trusted_seed_devices: set[str] = {
        h.strip().lower() for h in os.getenv("ANTISPAM_TRUSTED_SEEDS", "").split(",") if h.strip()
    }
    trusted_seed_trust_score: float = 3.0
    
    # Official Mintsifry open data URLs
    registry_sources: dict[str, str] = {
        "ABC-3xx": "https://opendata.digital.gov.ru/downloads/ABC-3xx.csv",
        "ABC-4xx": "https://opendata.digital.gov.ru/downloads/ABC-4xx.csv",
        "ABC-8xx": "https://opendata.digital.gov.ru/downloads/ABC-8xx.csv",
        "ABC-800": "https://opendata.digital.gov.ru/downloads/ABC-800.csv",
        "DEF-9xx": "https://opendata.digital.gov.ru/downloads/DEF-9xx.csv",
    }
    
    # Download settings
    download_timeout_seconds: float = 60.0
    ssl_verify: bool = False  # Set to False to handle Russian Root CA without cert issues
    user_agent: str = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/130.0.0.0 Safari/537.36"
    )
    
    # Validation constraints
    min_expected_records: int = 400_000

settings = Settings()
