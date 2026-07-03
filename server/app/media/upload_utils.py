"""Streaming upload helper.

Copies an incoming ``UploadFile`` to a temporary file on disk in bounded-size
chunks, enforcing the maximum size *while streaming* (so an oversized upload is
rejected without ever being fully buffered in memory). Returns the temp path, the
byte size, and the leading header bytes (for magic-byte validation). The caller
owns the temp file and must delete it.
"""

from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass

from fastapi import UploadFile

from app.core.exceptions import ValidationError

_CHUNK = 1024 * 1024  # 1 MiB
_HEADER_BYTES = 16


@dataclass(frozen=True, slots=True)
class SpooledUpload:
    path: str
    size: int
    header: bytes


async def spool_to_tempfile(upload: UploadFile, *, max_bytes: int) -> SpooledUpload:
    """Stream ``upload`` to a temp file, enforcing ``max_bytes``."""
    await upload.seek(0)
    fd, path = tempfile.mkstemp(prefix="upload_")
    size = 0
    header = b""
    try:
        with os.fdopen(fd, "wb") as tmp:
            while chunk := await upload.read(_CHUNK):
                size += len(chunk)
                if size > max_bytes:
                    raise ValidationError(
                        f"File exceeds the maximum allowed size of {max_bytes} bytes."
                    )
                if len(header) < _HEADER_BYTES:
                    header += chunk[: _HEADER_BYTES - len(header)]
                tmp.write(chunk)
    except BaseException:
        os.unlink(path)
        raise

    if size == 0:
        os.unlink(path)
        raise ValidationError("Uploaded file is empty.")
    return SpooledUpload(path=path, size=size, header=header)
