"""Survey item response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import Difficulty, ItemCondition
from app.models.survey_item import SurveyItem


class SurveyItemResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    survey_id: uuid.UUID
    item_name: str
    category: str | None
    quantity: int
    room_location: str | None

    height_cm: Decimal | None
    width_cm: Decimal | None
    depth_cm: Decimal | None
    weight_kg: Decimal | None
    material: str | None

    fragile: bool
    needs_disassembly: bool
    needs_special_handling: bool
    needs_to_ship: bool

    packing_difficulty: Difficulty | None
    lifting_difficulty: Difficulty | None

    estimated_value: Decimal | None
    condition: ItemCondition | None
    remarks: str | None
    confidence_score: Decimal | None
    source: str
    created_at: datetime
    updated_at: datetime

    # IDs of the media evidencing this item (fetch signed URLs via /media/{id}).
    media_ids: list[uuid.UUID] = Field(default_factory=list)

    @classmethod
    def from_item(cls, item: SurveyItem) -> SurveyItemResponse:
        """Build from an ORM item whose ``media`` relationship is eagerly loaded."""
        model = cls.model_validate(item)
        model.media_ids = [m.id for m in item.media]
        return model


class SurveyItemListResponse(BaseModel):
    items: list[SurveyItemResponse]
    count: int
