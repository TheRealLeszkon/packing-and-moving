"""Survey item — a single inventory entry.

Produced by AI analysis or added manually by the surveyor (``source``). Physical
estimates are stored as granular numeric columns (per-dimension cm, weight kg)
rather than free text so summaries can aggregate volume/weight/value. Linked to
the evidencing media via the ``item_media`` junction.
"""

from __future__ import annotations

import uuid
from decimal import Decimal
from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, Integer, Numeric, String, Text
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import Difficulty, ItemCondition
from app.models.item_media import item_media

if TYPE_CHECKING:
    from app.models.media import Media
    from app.models.survey import Survey


class SurveyItem(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "survey_items"

    survey_id: Mapped[uuid.UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("surveys.id", ondelete="CASCADE"),
        index=True,
    )

    item_name: Mapped[str] = mapped_column(String(255))
    category: Mapped[str | None] = mapped_column(String(128))
    quantity: Mapped[int] = mapped_column(Integer, default=1)
    room_location: Mapped[str | None] = mapped_column(String(255))

    # Estimated physical dimensions (cm) and weight (kg).
    height_cm: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    width_cm: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    depth_cm: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    weight_kg: Mapped[Decimal | None] = mapped_column(Numeric(8, 3))
    material: Mapped[str | None] = mapped_column(String(128))

    fragile: Mapped[bool] = mapped_column(default=False)
    needs_disassembly: Mapped[bool] = mapped_column(default=False)
    needs_special_handling: Mapped[bool] = mapped_column(default=False)
    needs_to_ship: Mapped[bool] = mapped_column(default=True)

    packing_difficulty: Mapped[Difficulty | None] = mapped_column(
        enum_column(Difficulty, "difficulty")
    )
    lifting_difficulty: Mapped[Difficulty | None] = mapped_column(
        enum_column(Difficulty, "difficulty")
    )

    estimated_value: Mapped[Decimal | None] = mapped_column(Numeric(12, 2))
    condition: Mapped[ItemCondition | None] = mapped_column(
        enum_column(ItemCondition, "item_condition")
    )
    remarks: Mapped[str | None] = mapped_column(Text)
    # AI confidence in [0, 1]; null for manually-entered items.
    confidence_score: Mapped[Decimal | None] = mapped_column(Numeric(4, 3))
    # Provenance: "ai" (from a Gemini run) or "manual" (surveyor-entered).
    source: Mapped[str] = mapped_column(String(16), default="ai")

    survey: Mapped[Survey] = relationship(back_populates="items")
    media: Mapped[list[Media]] = relationship(
        secondary=item_media,
        back_populates="items",
    )
