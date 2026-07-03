"""Storage backend selection."""

from __future__ import annotations

from functools import lru_cache

from app.core.config import StorageBackend, settings
from app.storage.base import StoragePort


@lru_cache
def get_storage() -> StoragePort:
    """Process-wide storage backend chosen from settings."""
    if settings.storage_backend is StorageBackend.MEMORY:
        from app.storage.memory import InMemoryStorage

        return InMemoryStorage()

    from app.storage.gcs import GCSStorage

    return GCSStorage(
        project_id=settings.gcp_project_id,
        bucket_name=settings.gcs_bucket_name,
        credentials_path=settings.google_application_credentials,
    )
