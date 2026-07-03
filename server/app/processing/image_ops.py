"""Image resizing (Pillow)."""

from __future__ import annotations

import io
from dataclasses import dataclass

from PIL import Image, ImageOps


@dataclass(frozen=True, slots=True)
class ResizedImage:
    data: bytes
    width: int
    height: int


def resize_to_bounds(data: bytes, *, max_width: int, max_height: int) -> ResizedImage:
    """Resize an image to fit within ``max_width`` x ``max_height``.

    Aspect ratio is preserved (never upscales), EXIF orientation is applied, and
    the result is re-encoded as JPEG. Returns the encoded bytes and final size.
    """
    with Image.open(io.BytesIO(data)) as img:
        img = ImageOps.exif_transpose(img)  # honour camera orientation
        img = img.convert("RGB")            # drop alpha/palette for JPEG
        img.thumbnail((max_width, max_height), Image.Resampling.LANCZOS)
        out = io.BytesIO()
        img.save(out, format="JPEG", quality=85, optimize=True)
        return ResizedImage(data=out.getvalue(), width=img.width, height=img.height)
