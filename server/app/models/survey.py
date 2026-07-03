"""Survey model — the aggregate root of the platform.

A survey is created by a customer and later assigned to a surveyor. It carries an
integer ``version_id`` used by SQLAlchemy for optimistic concurrency control:
state transitions on a survey are contended (surveyor vs. customer vs. worker),
so we detect lost updates rather than silently overwriting.
"""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING

from sqlalchemy import DateTime, ForeignKey, Numeric, String, Text
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import SurveyStatus

if TYPE_CHECKING:
    from app.models.ai_analysis_run import AIAnalysisRun
    from app.models.media import Media
    from app.models.survey_history import SurveyHistory
    from app.models.survey_item import SurveyItem
    from app.models.user import User


class Survey(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "surveys"

    name: Mapped[str] = mapped_column(String(255))
    origin_address: Mapped[str] = mapped_column(Text)
    destination_address: Mapped[str] = mapped_column(Text)

    customer_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        # Keep survey history intact: block deleting a customer with surveys.
        ForeignKey("users.id", ondelete="RESTRICT"),
        index=True,
    )
    surveyor_id: Mapped[uuid.UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        index=True,
    )
    preferred_datetime: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    status: Mapped[SurveyStatus] = mapped_column(
        enum_column(SurveyStatus, "survey_status"),
        default=SurveyStatus.SCHEDULED,
        index=True,
    )

    # Aggregate estimates, recomputed from survey_items after AI/review.
    total_volume_estimate: Mapped[Decimal | None] = mapped_column(Numeric(12, 4))
    total_value_estimate: Mapped[Decimal | None] = mapped_column(Numeric(14, 2))

    # Optimistic locking discriminator.
    version_id: Mapped[int] = mapped_column(nullable=False, default=0)

    __mapper_args__ = {"version_id_col": version_id}

    customer: Mapped[User] = relationship(
        back_populates="surveys_as_customer",
        foreign_keys=[customer_id],
    )
    surveyor: Mapped[User | None] = relationship(
        back_populates="surveys_as_surveyor",
        foreign_keys=[surveyor_id],
    )
    media: Mapped[list[Media]] = relationship(
        back_populates="survey",
        cascade="all, delete-orphan",
    )
    items: Mapped[list[SurveyItem]] = relationship(
        back_populates="survey",
        cascade="all, delete-orphan",
    )
    history: Mapped[list[SurveyHistory]] = relationship(
        back_populates="survey",
        cascade="all, delete-orphan",
        order_by="SurveyHistory.created_at",
    )
    ai_runs: Mapped[list[AIAnalysisRun]] = relationship(
        back_populates="survey",
        cascade="all, delete-orphan",
    )
