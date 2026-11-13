import hashlib
import logging
from pathlib import Path
import httpx
from backend.config import settings, CACHE_DIR

logger = logging.getLogger(__name__)

CACHE_DIR.mkdir(parents=True, exist_ok=True)

def calculate_sha256(filepath: Path) -> str:
    """Calculate SHA-256 checksum of a file."""
    sha = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            sha.update(chunk)
    return sha.hexdigest()

def download_file(url: str, target_filename: str, force: bool = False) -> Path:
    """
    Download a remote CSV file to the cache directory.
    Uses streaming and bypasses SSL verification if configured (for Russian Root CAs).
    """
    target_path = CACHE_DIR / target_filename
    
    if target_path.exists() and not force:
        logger.info(f"Using cached file: {target_path} (size: {target_path.stat().st_size} bytes)")
        return target_path

    logger.info(f"Downloading from {url} to {target_path}...")
    headers = {"User-Agent": settings.user_agent}

    # Use custom transport / client with ssl_verify=False for opendata.digital.gov.ru
    with httpx.Client(verify=settings.ssl_verify, timeout=settings.download_timeout_seconds, headers=headers) as client:
        with client.stream("GET", url, follow_redirects=True) as response:
            response.raise_for_status()
            temp_path = target_path.with_suffix(".tmp")
            with open(temp_path, "wb") as f:
                for chunk in response.iter_bytes(chunk_size=65536):
                    f.write(chunk)
            temp_path.replace(target_path)

    logger.info(f"Successfully downloaded {target_filename} ({target_path.stat().st_size} bytes)")
    return target_path

def download_all_registries(force: bool = False) -> dict[str, Path]:
    """Download all 4 official registry CSV files."""
    downloaded = {}
    for name, url in settings.registry_sources.items():
        filename = f"{name}.csv"
        downloaded[name] = download_file(url, filename, force=force)
    return downloaded
