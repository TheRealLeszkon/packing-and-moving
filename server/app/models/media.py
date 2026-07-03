"""Media model.

One row per file that flows through the system: original uploads (images,
videos) *and* their processed derivatives (resized images, frames extracted from
a video). Derivatives point back to their source video via ``parent_video_id``.
Only URLs + metadata live here — binary content stays in Google Cloud Storage.
"""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING, Any

from sqlalchemy import DateTime, ForeignKey, Integer, Numeric, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import MediaType, ProcessingStatus
from app.models.item_media import item_media

if TYPE_CHECKING:
    from app.models.media_processing_job import MediaProcessingJob
    from app.models.survey import Survey
    from app.models.survey_item import SurveyItem
    from app.models.user import User


class Media(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "media"

    survey_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("surveys.id", ondelete="CASCADE"),
        index=True,
    )
    # Null for system-generated derivatives (frames, resized images).
    uploaded_by: Mapped[uuid.UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
    )
    media_type: Mapped[MediaType] = mapped_column(enum_column(MediaType, "media_type"), index=True)

    original_url: Mapped[str | None] = mapped_column(Text)
    processed_url: Mapped[str | None] = mapped_column(Text)
    room_location: Mapped[str | None] = mapped_column(String(255))

    upload_timestamp: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )
    processing_status: Mapped[ProcessingStatus] = mapped_column(
        enum_column(ProcessingStatus, "processing_status"),
        default=ProcessingStatus.PENDING,
        index=True,
    )

    width: Mapped[int | None] = mapped_column(Integer)
    height: Mapped[int | None] = mapped_column(Integer)
    duration_seconds: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    frame_number: Mapped[int | None] = mapped_column(Integer)
    parent_video_id: Mapped[uuid.UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("media.id", ondelete="CASCADE"),
        index=True,
    )
    blur_score: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))

    # Free-form provenance/EXIF/processing metadata. Attribute is renamed because
    # ``metadata`` is reserved on the declarative base; the DB column stays "metadata".
    extra_metadata: Mapped[dict[str, Any]] = mapped_column(
        "metadata", JSONB, default=dict, server_default="{}"
    )

    survey: Mapped[Survey] = relationship(back_populates="media")
    uploader: Mapped[User | None] = relationship()
    parent_video: Mapped[Media | None] = relationship(
        back_populates="frames",
        remote_side="Media.id",
    )
    frames: Mapped[list[Media]] = relationship(
        back_populates="parent_video",
        cascade="all, delete-orphan",
    )
    jobs: Mapped[list[MediaProcessingJob]] = relationship(
        back_populates="media",
        cascade="all, delete-orphan",
    )
    items: Mapped[list[SurveyItem]] = relationship(
        secondary=item_media,
        back_populates="media",
    )
