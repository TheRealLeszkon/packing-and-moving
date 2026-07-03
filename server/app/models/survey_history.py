"""Survey state-transition audit log.

Append-only record of every survey status change: who changed it, from/to, and
an optional reason (e.g. the customer's revision request). Provides an auditable
trail for disputes and debugging the workflow. No ``updated_at`` — rows are never
modified after creation.
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, ForeignKey, Text, func
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import SurveyStatus

if TYPE_CHECKING:
    from app.models.survey import Survey


class SurveyHistory(Base, UUIDPrimaryKeyMixin):
    __tablename__ = "survey_history"

    survey_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("surveys.id", ondelete="CASCADE"),
        index=True,
    )
    from_status: Mapped[SurveyStatus | None] = mapped_column(
        enum_column(SurveyStatus, "survey_status")
    )
    to_status: Mapped[SurveyStatus] = mapped_column(enum_column(SurveyStatus, "survey_status"))
    # Null actor = automated transition (e.g. a worker moving PROCESSING -> READY).
    changed_by: Mapped[uuid.UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
    )
    reason: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )

    survey: Mapped[Survey] = relationship(back_populates="history")
