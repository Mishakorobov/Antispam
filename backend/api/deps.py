from fastapi import HTTPException, Security, status
from fastapi.security import APIKeyHeader
from backend.config import settings

admin_api_key_header = APIKeyHeader(name="X-Admin-Api-Key", auto_error=False)

def require_admin_auth(api_key: str | None = Security(admin_api_key_header)) -> str:
    """
    Validate administrator API key passed in X-Admin-Api-Key header.
    Protects administrative operations like toggling whitelist groups.
    """
    if not api_key or api_key != settings.admin_api_key:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: Invalid or missing X-Admin-Api-Key header"
        )
    return api_key
