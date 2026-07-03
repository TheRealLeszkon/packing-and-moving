"""Storage abstraction.

Decouples the app from any specific object store. Services depend on
``StoragePort``; the concrete backend (GCS in production, in-memory for tests) is
chosen by configuration. Objects are addressed by an opaque *key* (a path within
the bucket); persisted references use a backend *URI* (e.g. ``gs://bucket/key``)
so a stored reference is self-describing and portable.
"""

from __future__ import annotations

from typing import BinaryIO, Protocol, runtime_checkable


class StorageError(Exception):
    """Raised when a storage operation fails."""


@runtime_checkable
class StoragePort(Protocol):
    async def upload(
        self, key: str, data: BinaryIO, *, content_type: str, size: int
    ) -> str:
        """Store ``data`` under ``key`` and return its backend URI."""
        ...

    async def download(self, key: str) -> bytes:
        """Return the full object bytes for ``key``."""
        ...

    async def signed_url(self, key: str, *, expires_in: int) -> str:
        """A time-limited URL granting temporary read access to ``key``."""
        ...

    async def delete(self, key: str) -> None:
        """Delete ``key``. Missing keys are treated as already-deleted."""
        ...

    def uri_for(self, key: str) -> str:
        """The backend URI that addresses ``key``."""
        ...

    def key_from_uri(self, uri: str) -> str:
        """Inverse of :meth:`uri_for`."""
        ...
