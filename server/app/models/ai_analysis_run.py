"""AI analysis run.

Audit + observability record for every call to the AI provider (Gemini). Stores
the raw provider response (for reproducibility and debugging malformed output),
the prompt version (so results are traceable to a prompt), token usage (cost
tracking) and latency. Kept separate from survey_items so we retain provenance
even after a surveyor edits the generated inventory.
"""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING, Any

from sqlalchemy import ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import AIRunStatus

if TYPE_CHECKING:
    from app.models.survey import Survey


class AIAnalysisRun(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "ai_analysis_runs"

    survey_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("surveys.id", ondelete="CASCADE"),
        index=True,
    )
    provider: Mapped[str] = mapped_column(String(64), default="gemini")
    model: Mapped[str] = mapped_column(String(128))
    prompt_version: Mapped[str] = mapped_column(String(64))
    status: Mapped[AIRunStatus] = mapped_column(
        enum_column(AIRunStatus, "ai_run_status"),
        default=AIRunStatus.PENDING,
        index=True,
    )

    request_media_count: Mapped[int | None] = mapped_column(Integer)
    raw_response: Mapped[dict[str, Any] | None] = mapped_column(JSONB)

    # Model feedback surfaced to the app: whether it wants more/better images,
    # and what it asked for. Duplicated out of raw_response so it is queryable.
    needs_more_images: Mapped[bool | None] = mapped_column()
    requested_images: Mapped[list[str] | None] = mapped_column(JSONB)

    prompt_tokens: Mapped[int | None] = mapped_column(Integer)
    completion_tokens: Mapped[int | None] = mapped_column(Integer)
    total_tokens: Mapped[int | None] = mapped_column(Integer)
    latency_ms: Mapped[int | None] = mapped_column(Integer)
    error: Mapped[str | None] = mapped_column(Text)

    survey: Mapped[Survey] = relationship(back_populates="ai_runs")
