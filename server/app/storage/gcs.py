"""Google Cloud Storage backend.

The ``google-cloud-storage`` client is synchronous and does blocking network I/O,
so every operation runs in a worker thread (``asyncio.to_thread``) to keep the
event loop free. Credentials are loaded explicitly from the service-account file
when configured (also required for V4 signed-URL generation); otherwise the
library's Application Default Credentials are used.
"""

from __future__ import annotations

import asyncio
from datetime import timedelta
from typing import TYPE_CHECKING, BinaryIO

from app.storage.base import StorageError

if TYPE_CHECKING:
    from google.cloud.storage import Bucket, Client

_SCHEME = "gs://"


class GCSStorage:
    def __init__(
        self,
        *,
        project_id: str,
        bucket_name: str,
        credentials_path: str | None = None,
    ) -> None:
        self._bucket_name = bucket_name
        self._client = self._build_client(project_id, credentials_path)

    @staticmethod
    def _build_client(project_id: str, credentials_path: str | None) -> Client:
        from google.cloud import storage

        if credentials_path:
            from google.oauth2 import service_account

            credentials = service_account.Credentials.from_service_account_file(
                credentials_path
            )
            return storage.Client(project=project_id, credentials=credentials)
        return storage.Client(project=project_id)

    @property
    def _bucket(self) -> Bucket:
        return self._client.bucket(self._bucket_name)

    async def upload(
        self, key: str, data: BinaryIO, *, content_type: str, size: int
    ) -> str:
        def _do() -> None:
            blob = self._bucket.blob(key)
            # rewind defensively; caller may have read the header for validation.
            data.seek(0)
            blob.upload_from_file(data, content_type=content_type, size=size)

        await asyncio.to_thread(_do)
        return self.uri_for(key)

    async def download(self, key: str) -> bytes:
        def _do() -> bytes:
            return self._bucket.blob(key).download_as_bytes()

        try:
            return await asyncio.to_thread(_do)
        except Exception as exc:  # noqa: BLE001 - normalise backend errors
            raise StorageError(f"Failed to download {key}: {exc}") from exc

    async def signed_url(self, key: str, *, expires_in: int) -> str:
        def _do() -> str:
            return self._bucket.blob(key).generate_signed_url(
                version="v4",
                expiration=timedelta(seconds=expires_in),
                method="GET",
            )

        return await asyncio.to_thread(_do)

    async def delete(self, key: str) -> None:
        def _do() -> None:
            # Idempotent: don't error if the object is already gone.
            self._bucket.blob(key).delete(if_generation_match=None)

        try:
            await asyncio.to_thread(_do)
        except Exception as exc:  # noqa: BLE001
            from google.cloud.exceptions import NotFound

            if isinstance(exc, NotFound):
                return
            raise StorageError(f"Failed to delete {key}: {exc}") from exc

    def uri_for(self, key: str) -> str:
        return f"{_SCHEME}{self._bucket_name}/{key}"

    def key_from_uri(self, uri: str) -> str:
        prefix = f"{_SCHEME}{self._bucket_name}/"
        if not uri.startswith(prefix):
            raise StorageError(f"URI does not belong to bucket {self._bucket_name}: {uri}")
        return uri.removeprefix(prefix)
