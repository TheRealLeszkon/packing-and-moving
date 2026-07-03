"""Media processing job ledger.

A durable record of each unit of pipeline work for a media item (resize, frame
extraction, blur/duplicate detection, AI analysis). Decouples the pipeline's
state from the message broker so we get: idempotency (``idempotency_key`` dedupes
re-enqueues), retry accounting (``attempts``/``max_attempts``), dead-lettering,
and post-hoc failure inspection (``last_error``).
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import JobStatus, JobType

if TYPE_CHECKING:
    from app.models.media import Media


class MediaProcessingJob(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "media_processing_jobs"

    media_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("media.id", ondelete="CASCADE"),
        index=True,
    )
    job_type: Mapped[JobType] = mapped_column(enum_column(JobType, "job_type"))
    status: Mapped[JobStatus] = mapped_column(
        enum_column(JobStatus, "job_status"),
        default=JobStatus.QUEUED,
        index=True,
    )
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    max_attempts: Mapped[int] = mapped_column(Integer, default=3)
    # Unique so an at-least-once broker delivering a message twice cannot create
    # (or run) the same logical job twice.
    idempotency_key: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    last_error: Mapped[str | None] = mapped_column(Text)

    scheduled_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    media: Mapped[Media] = relationship(back_populates="jobs")
