"""Upload content validation.

Never trust the client-declared ``Content-Type``. Each file is validated by
sniffing its leading *magic bytes* against an allow-list, and the declared MIME
type must agree. Returns the canonical media kind + file extension used to build
the storage key.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.core.exceptions import ValidationError
from app.models.enums import MediaType

# Declared MIME -> (extension, magic-byte predicate)
_JPEG = "image/jpeg"
_PNG = "image/png"
_MP4 = "video/mp4"

ALLOWED_IMAGE_TYPES = frozenset({_JPEG, _PNG})
ALLOWED_VIDEO_TYPES = frozenset({_MP4})


def _is_jpeg(h: bytes) -> bool:
    return h[:3] == b"\xff\xd8\xff"


def _is_png(h: bytes) -> bool:
    return h[:8] == b"\x89PNG\r\n\x1a\n"


def _is_mp4(h: bytes) -> bool:
    # ISO base media: a box whose type (bytes 4-8) is 'ftyp'.
    return len(h) >= 12 and h[4:8] == b"ftyp"


_IMAGE_RULES = {_JPEG: ("jpg", _is_jpeg), _PNG: ("png", _is_png)}
_VIDEO_RULES = {_MP4: ("mp4", _is_mp4)}


@dataclass(frozen=True, slots=True)
class ValidatedUpload:
    media_type: MediaType
    extension: str


def validate_image(content_type: str | None, header: bytes) -> ValidatedUpload:
    return _validate(content_type, header, _IMAGE_RULES, MediaType.IMAGE, "image")


def validate_video(content_type: str | None, header: bytes) -> ValidatedUpload:
    return _validate(content_type, header, _VIDEO_RULES, MediaType.VIDEO, "video")


def _validate(
    content_type: str | None,
    header: bytes,
    rules: dict,
    media_type: MediaType,
    label: str,
) -> ValidatedUpload:
    normalized = (content_type or "").split(";")[0].strip().lower()
    if normalized not in rules:
        raise ValidationError(
            f"Unsupported {label} type '{content_type}'. Allowed: {', '.join(sorted(rules))}."
        )
    extension, sniff = rules[normalized]
    if not sniff(header):
        raise ValidationError(
            f"File content does not match its declared type '{normalized}'."
        )
    return ValidatedUpload(media_type=media_type, extension=extension)
