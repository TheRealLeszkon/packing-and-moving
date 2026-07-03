"""Video metadata probing via ffprobe.

Used at upload time to reject over-length videos early, and reused by the Phase 5
processing worker. Only reads container metadata (fast); it does not decode
frames.
"""

from __future__ import annotations

import asyncio

from app.core.exceptions import ValidationError

_PROBE_TIMEOUT_SECONDS = 15.0


async def probe_duration_seconds(path: str) -> float:
    """Return a video's duration in seconds.

    Raises ``ValidationError`` if ffprobe cannot read the file (corrupt or not a
    real video), which is the correct outcome for upload validation.
    """
    proc = await asyncio.create_subprocess_exec(
        "ffprobe",
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        path,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        async with asyncio.timeout(_PROBE_TIMEOUT_SECONDS):
            stdout, _ = await proc.communicate()
    except TimeoutError as exc:
        proc.kill()
        await proc.wait()
        raise ValidationError("Timed out reading video metadata.") from exc

    if proc.returncode != 0:
        raise ValidationError("Could not read video; the file may be corrupt.")

    try:
        return float(stdout.decode().strip())
    except ValueError as exc:
        raise ValidationError("Could not determine video duration.") from exc
