"""Object key construction.

Keys are always server-generated (a UUID filename), never derived from
client-supplied filenames — this eliminates path-traversal and collision risks.
Folder layout keeps a survey's assets grouped and separates originals from
processed derivatives:

    surveys/{survey_id}/originals/{uuid}.{ext}
    surveys/{survey_id}/processed/{uuid}.{ext}
    surveys/{survey_id}/frames/{video_id}/{uuid}.{ext}
"""

from __future__ import annotations

import uuid
from enum import StrEnum


class KeyKind(StrEnum):
    ORIGINAL = "originals"
    PROCESSED = "processed"
    FRAME = "frames"


def original_key(survey_id: uuid.UUID, ext: str) -> str:
    return f"surveys/{survey_id}/{KeyKind.ORIGINAL}/{uuid.uuid4()}.{ext}"


def processed_key(survey_id: uuid.UUID, ext: str) -> str:
    return f"surveys/{survey_id}/{KeyKind.PROCESSED}/{uuid.uuid4()}.{ext}"


def frame_key(survey_id: uuid.UUID, video_id: uuid.UUID, ext: str) -> str:
    return f"surveys/{survey_id}/{KeyKind.FRAME}/{video_id}/{uuid.uuid4()}.{ext}"
