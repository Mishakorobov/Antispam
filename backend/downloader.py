import gzip
import hashlib
import logging
import os
import subprocess
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

def get_github_token() -> str:
    """Retrieve GitHub token from env, settings, or git remote url."""
    token = os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN") or getattr(settings, "github_token", "")
    if token:
        return token
    try:
        remote = subprocess.check_output(
            ["git", "config", "remote.origin.url"],
            text=True,
            timeout=2,
            stderr=subprocess.DEVNULL
        ).strip()
        if "@github.com" in remote and "://" in remote:
            user_part = remote.split("://")[1].split("@github.com")[0]
            if user_part.startswith("ghp_") or user_part.startswith("github_pat_"):
                return user_part
    except Exception:
        pass
    return ""

def download_from_github_mirror(target_filename: str) -> bytes | None:
    """
    Download CSV (or .csv.gz) mirror from GitHub Releases 'latest-data'.
    Handles both private repositories (using GitHub API + token) and public repositories.
    """
    repo = os.getenv("GITHUB_REPOSITORY") or getattr(settings, "github_repository", "Mishakorobov/Antispam")
    token = get_github_token()
    tag = "latest-data"
    candidates = [f"{target_filename}.gz", target_filename]

    # Attempt 1: Authenticated GitHub API (mandatory for private repositories)
    if token:
        try:
            logger.info(f"Querying GitHub API releases for {repo} tag {tag}...")
            api_url = f"https://api.github.com/repos/{repo}/releases/tags/{tag}"
            api_headers = {
                "Authorization": f"Bearer {token}",
                "User-Agent": "AntiSpam-Mirror-Fallback/1.0",
                "Accept": "application/vnd.github+json"
            }
            with httpx.Client(headers=api_headers, timeout=30.0, follow_redirects=True) as client:
                resp = client.get(api_url)
                if resp.status_code == 200:
                    release_data = resp.json()
                    assets = {a["name"]: a for a in release_data.get("assets", [])}
                    for cand in candidates:
                        if cand in assets:
                            asset = assets[cand]
                            logger.info(f"Downloading asset {cand} (id={asset['id']}) via GitHub API...")
                            dl_headers = {
                                "Authorization": f"Bearer {token}",
                                "User-Agent": "AntiSpam-Mirror-Fallback/1.0",
                                "Accept": "application/octet-stream",
                            }
                            dl_resp = client.get(asset["url"], headers=dl_headers)
                            if dl_resp.status_code == 200:
                                content = dl_resp.content
                                if cand.endswith(".gz"):
                                    content = gzip.decompress(content)
                                stripped = content.lstrip()
                                if stripped.startswith(b"<!doctype") or stripped.startswith(b"<html"):
                                    logger.warning("Mirror returned HTML error page instead of CSV")
                                    continue
                                logger.info(f"Successfully retrieved {cand} from GitHub API ({len(content)} bytes uncompressed)")
                                return content
        except Exception as api_err:
            logger.warning(f"GitHub API release download failed: {api_err}")

    # Attempt 2: Public GitHub Release direct download URL
    for cand in candidates:
        mirror_url = f"https://github.com/{repo}/releases/download/{tag}/{cand}"
        try:
            logger.info(f"Attempting public mirror download: {mirror_url}")
            headers = {"User-Agent": "AntiSpam-Mirror-Fallback/1.0"}
            if token:
                headers["Authorization"] = f"Bearer {token}"
            with httpx.Client(timeout=45.0, headers=headers, follow_redirects=True) as client:
                m_resp = client.get(mirror_url)
                if m_resp.status_code == 200:
                    content = m_resp.content
                    if cand.endswith(".gz"):
                        content = gzip.decompress(content)
                    stripped = content.lstrip()
                    if stripped.startswith(b"<!doctype") or stripped.startswith(b"<html"):
                        logger.warning("Mirror returned HTML error page instead of CSV")
                        continue
                    logger.info(f"Successfully retrieved {cand} from public URL ({len(content)} bytes uncompressed)")
                    return content
        except Exception as m_err:
            logger.warning(f"Public mirror {mirror_url} failed: {m_err}")

    return None

def download_file(url: str, target_filename: str, force: bool = False) -> Path:
    """
    Download a remote CSV file to the cache directory.
    Uses streaming and bypasses SSL verification if configured (for Russian Root CAs).
    If the primary Mincifry portal is blocked (HTTP 403 on foreign cloud runners) or times out,
    automatically falls back to GitHub Releases mirrors with on-the-fly GZIP decompression.
    """
    target_path = CACHE_DIR / target_filename
    
    if target_path.exists() and not force:
        logger.info(f"Using cached file: {target_path} (size: {target_path.stat().st_size} bytes)")
        return target_path

    logger.info(f"Downloading from {url} to {target_path}...")
    headers = {"User-Agent": settings.user_agent}

    # Primary attempt: Official Mincifry portal
    try:
        with httpx.Client(verify=settings.ssl_verify, timeout=settings.download_timeout_seconds, headers=headers) as client:
            with client.stream("GET", url, follow_redirects=True) as response:
                response.raise_for_status()
                content_type = response.headers.get("content-type", "").lower()
                if "text/html" in content_type:
                    raise ValueError(f"Portal returned HTML page instead of CSV (content-type: {content_type})")
                temp_path = target_path.with_suffix(".tmp")
                with open(temp_path, "wb") as f:
                    first = True
                    for chunk in response.iter_bytes(chunk_size=65536):
                        if first:
                            first = False
                            stripped = chunk.lstrip()
                            if stripped.startswith(b"<!doctype") or stripped.startswith(b"<html"):
                                raise ValueError("Portal returned HTML body instead of CSV")
                        f.write(chunk)
                temp_path.replace(target_path)
                logger.info(f"Successfully downloaded {target_filename} ({target_path.stat().st_size} bytes)")
                return target_path
    except (httpx.HTTPStatusError, httpx.RequestError, ValueError) as e:
        logger.warning(f"Primary registry source ({url}) failed: {e}. Checking fallback mirrors...")

        # Fallback to GitHub latest-data mirrors
        content = download_from_github_mirror(target_filename)
        if content is not None:
            temp_path = target_path.with_suffix(".tmp")
            with open(temp_path, "wb") as f:
                f.write(content)
            temp_path.replace(target_path)
            logger.info(f"Successfully recovered {target_filename} from mirror ({target_path.stat().st_size} bytes)")
            return target_path

        # If existing local file exists, use it
        if target_path.exists():
            logger.info(f"Falling back to existing local file: {target_path}")
            return target_path

        # If all fail, re-raise original error
        raise e

def download_all_registries(force: bool = False) -> dict[str, Path]:
    """Download all official registry CSV files."""
    downloaded = {}
    for name, url in settings.registry_sources.items():
        filename = f"{name}.csv"
        downloaded[name] = download_file(url, filename, force=force)
    return downloaded
