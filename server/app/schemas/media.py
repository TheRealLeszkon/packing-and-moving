"""Media request/response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict

from app.models.enums import MediaType, ProcessingStatus


class MediaResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    survey_id: uuid.UUID
    media_type: MediaType
    processing_status: ProcessingStatus
    room_location: str | None
    width: int | None
    height: int | None
    duration_seconds: Decimal | None
    frame_number: int | None
    parent_video_id: uuid.UUID | None
    upload_timestamp: datetime
    # Short-lived signed URL for reading the object; populated on detail/list,
    # omitted on the raw upload acknowledgement.
    url: str | None = None


class MediaUploadResponse(BaseModel):
    items: list[MediaResponse]
    count: int
