"""In-memory storage backend.

For local development and tests: keeps object bytes in a process-local dict and
mints fake ``memory://`` URIs. Implements the same ``StoragePort`` contract as
GCS so nothing above the storage layer can tell the difference. Not for
production (no persistence, single process).
"""

from __future__ import annotations

from typing import BinaryIO

from app.storage.base import StorageError

_SCHEME = "memory://"


class InMemoryStorage:
    def __init__(self) -> None:
        self._objects: dict[str, tuple[bytes, str]] = {}

    async def upload(
        self, key: str, data: BinaryIO, *, content_type: str, size: int
    ) -> str:
        self._objects[key] = (data.read(), content_type)
        return self.uri_for(key)

    async def download(self, key: str) -> bytes:
        try:
            return self._objects[key][0]
        except KeyError as exc:
            raise StorageError(f"Object not found: {key}") from exc

    async def signed_url(self, key: str, *, expires_in: int) -> str:
        if key not in self._objects:
            raise StorageError(f"Object not found: {key}")
        return f"{self.uri_for(key)}?signed=1&expires_in={expires_in}"

    async def delete(self, key: str) -> None:
        self._objects.pop(key, None)

    def uri_for(self, key: str) -> str:
        return f"{_SCHEME}{key}"

    def key_from_uri(self, uri: str) -> str:
        if not uri.startswith(_SCHEME):
            raise StorageError(f"Not a memory URI: {uri}")
        return uri.removeprefix(_SCHEME)
