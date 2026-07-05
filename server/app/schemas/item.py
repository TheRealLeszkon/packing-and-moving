"""Survey item response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import Difficulty, ItemCondition
from app.models.survey_item import SurveyItem

# Shared field constraints reused by the create/update payloads.
_NAME = Field(min_length=1, max_length=255)
_DIM = Field(default=None, ge=0, le=100000)  # centimetres
_WEIGHT = Field(default=None, ge=0, le=100000)  # kilograms
_VALUE = Field(default=None, ge=0)
_CONFIDENCE = Field(default=None, ge=0, le=1)


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


class SurveyItemCreate(BaseModel):
    """Surveyor-entered inventory item (source="manual")."""

    item_name: str = _NAME
    category: str | None = Field(default=None, max_length=128)
    quantity: int = Field(default=1, ge=1, le=10000)
    room_location: str | None = Field(default=None, max_length=255)

    height_cm: Decimal | None = _DIM
    width_cm: Decimal | None = _DIM
    depth_cm: Decimal | None = _DIM
    weight_kg: Decimal | None = _WEIGHT
    material: str | None = Field(default=None, max_length=128)

    fragile: bool = False
    needs_disassembly: bool = False
    needs_special_handling: bool = False
    needs_to_ship: bool = True

    packing_difficulty: Difficulty | None = None
    lifting_difficulty: Difficulty | None = None

    estimated_value: Decimal | None = _VALUE
    condition: ItemCondition | None = None
    remarks: str | None = Field(default=None, max_length=4000)
    # Media (already belonging to this survey) evidencing the item.
    media_ids: list[uuid.UUID] = Field(default_factory=list)


class SurveyItemUpdate(BaseModel):
    """Partial update: only provided fields are changed (unset fields untouched)."""

    model_config = ConfigDict(extra="forbid")

    item_name: str | None = Field(default=None, min_length=1, max_length=255)
    category: str | None = Field(default=None, max_length=128)
    quantity: int | None = Field(default=None, ge=1, le=10000)
    room_location: str | None = Field(default=None, max_length=255)

    height_cm: Decimal | None = _DIM
    width_cm: Decimal | None = _DIM
    depth_cm: Decimal | None = _DIM
    weight_kg: Decimal | None = _WEIGHT
    material: str | None = Field(default=None, max_length=128)

    fragile: bool | None = None
    needs_disassembly: bool | None = None
    needs_special_handling: bool | None = None
    needs_to_ship: bool | None = None

    packing_difficulty: Difficulty | None = None
    lifting_difficulty: Difficulty | None = None

    estimated_value: Decimal | None = _VALUE
    condition: ItemCondition | None = None
    remarks: str | None = Field(default=None, max_length=4000)
    # Surveyors may adjust the AI's confidence after reviewing the item (0.0–1.0).
    confidence_score: Decimal | None = _CONFIDENCE
    media_ids: list[uuid.UUID] | None = None


class ItemMergeRequest(BaseModel):
    """Collapse duplicate detections into one item.

    The first id is the survivor; the rest are deleted and their media re-linked
    onto it. ``quantity``/``item_name`` optionally override the survivor's values.
    """

    item_ids: list[uuid.UUID] = Field(min_length=2)
    item_name: str | None = Field(default=None, min_length=1, max_length=255)
    quantity: int | None = Field(default=None, ge=1, le=10000)


class ItemSplitRequest(BaseModel):
    """Replace one item with several. Each part starts as a copy of the original
    (media included) with the supplied fields overridden."""

    parts: list[SurveyItemUpdate] = Field(min_length=2)


class CategoryBreakdown(BaseModel):
    category: str | None
    distinct_items: int
    quantity: int


class RoomBreakdown(BaseModel):
    room_location: str | None
    distinct_items: int
    quantity: int


class AIFeedback(BaseModel):
    """Outcome of the latest AI analysis run, so the app can explain an empty
    or thin inventory (e.g. the model asked for more/better images)."""

    run_status: str              # pending | succeeded | failed
    needs_more_images: bool
    requested_images: list[str]


class SurveySummaryResponse(BaseModel):
    survey_id: uuid.UUID
    distinct_items: int          # number of item rows
    total_quantity: int          # sum of quantities
    total_volume_m3: Decimal
    total_value: Decimal
    fragile_items: int
    needs_disassembly_items: int
    needs_special_handling_items: int
    by_category: list[CategoryBreakdown]
    by_room: list[RoomBreakdown]
    ai_feedback: AIFeedback | None = None  # None when no AI run has happened
