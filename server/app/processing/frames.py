"""Video frame extraction (ffmpeg).

Extracts frames at a fixed rate (default ~1 fps) to a temporary directory, reads
them back as JPEG bytes, and cleans up. Runs ffmpeg as a subprocess — this is
worker-side (never in a request handler).
"""

from __future__ import annotations

import subprocess
import tempfile
from collections.abc import Iterator
from pathlib import Path

from app.core.exceptions import ExternalServiceError


def extract_frames(video_path: str, *, fps: float) -> list[bytes]:
    """Return JPEG-encoded frames sampled from ``video_path`` at ``fps``.

    Frames are returned in chronological order.
    """
    return list(_iter_frames(video_path, fps=fps))


def _iter_frames(video_path: str, *, fps: float) -> Iterator[bytes]:
    with tempfile.TemporaryDirectory(prefix="frames_") as tmpdir:
        pattern = str(Path(tmpdir) / "frame_%05d.jpg")
        result = subprocess.run(
            [
                "ffmpeg",
                "-i",
                video_path,
                "-vf",
                f"fps={fps}",
                "-qscale:v",
                "2",  # high-quality JPEG frames
                pattern,
            ],
            capture_output=True,
        )
        if result.returncode != 0:
            raise ExternalServiceError(
                "ffmpeg frame extraction failed.",
                details={"stderr": result.stderr.decode(errors="replace")[-500:]},
            )
        for frame_path in sorted(Path(tmpdir).glob("frame_*.jpg")):
            yield frame_path.read_bytes()
