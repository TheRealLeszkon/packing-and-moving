"""AI response schema.

Provider-neutral Pydantic DTOs that every ``AIProvider`` must return. The model's
raw JSON (camelCase, loosely typed) is validated and normalised here *before* it
ever reaches the database — untrusted AI output is coerced into known types,
clamped to sane ranges, and mapped onto our domain enums. Anything the model
cannot supply stays ``None`` rather than being fabricated.

This layer is also a **guardrail** against adversarial or malformed output (the
model sees untrusted image content and could be prompt-injected): free text is
stripped of control characters and truncated, over-long strings are cut rather
than rejected, blank-named items are dropped, and the item list is capped — so a
hostile response degrades gracefully instead of failing the whole run or
inserting junk.
"""

from __future__ import annotations

import re

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.core.config import settings

_DIFFICULTY_ALIASES = {"easy": "easy", "medium": "medium", "hard": "hard"}
# Control characters except tab/newline/carriage-return.
_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_NAME_MAX = 255
_TEXT_MAX = 4000


def sanitize_text(value: str | None, *, max_len: int) -> str | None:
    """Strip control chars, collapse surrounding whitespace, truncate. ``None``/blank -> None."""
    if value is None:
        return None
    cleaned = _CONTROL_CHARS.sub("", value).strip()
    if not cleaned:
        return None
    return cleaned[:max_len]


class AIDetectedItem(BaseModel):
    """A single inventory item detected by the model.

    Field names are snake_case; the model emits camelCase, mapped via aliases.
    ``populate_by_name`` also lets internal/test code build instances directly.
    """

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    item_name: str = Field(default="", alias="itemName")
    category: str | None = Field(default=None, alias="category")
    quantity: int | None = Field(default=None, alias="quantity")
    room_location: str | None = Field(default=None, alias="roomLocation")

    needs_to_ship: bool | None = Field(default=None, alias="needsToShip")
    confidence_score: float | None = Field(default=None, alias="confidenceScore")

    weight_kg: float | None = Field(default=None, alias="estimatedWeightKg")
    height_cm: float | None = Field(default=None, alias="estimatedHeightCm")
    width_cm: float | None = Field(default=None, alias="estimatedWidthCm")
    depth_cm: float | None = Field(default=None, alias="estimatedDepthCm")
    estimated_value: float | None = Field(default=None, alias="estimatedValue")
    material: str | None = Field(default=None, alias="estimatedMaterial")

    fragile: bool | None = Field(default=None, alias="isFragile")
    needs_disassembly: bool | None = Field(default=None, alias="needsDisassembly")
    needs_special_handling: bool | None = Field(default=None, alias="needsSpecialHandling")

    packing_difficulty: str | None = Field(default=None, alias="packingDifficulty")
    lifting_difficulty: str | None = Field(default=None, alias="liftingDifficulty")

    condition: str | None = Field(default=None, alias="condition")
    remarks: str | None = Field(default=None, alias="remarks")

    # 0-based indexes into the image batch this item was detected in (see prompt).
    # Used to link the item to its evidencing media; empty if the model omits it.
    source_image_indexes: list[int] = Field(default_factory=list, alias="sourceImageIndexes")

    @field_validator("item_name", mode="before")
    @classmethod
    def _clean_name(cls, value: object) -> str:
        return sanitize_text(value if isinstance(value, str) else None, max_len=_NAME_MAX) or ""

    @field_validator("category", "room_location", "material", "condition", mode="before")
    @classmethod
    def _clean_short_text(cls, value: object) -> str | None:
        return sanitize_text(value if isinstance(value, str) else None, max_len=_NAME_MAX)

    @field_validator("remarks", mode="before")
    @classmethod
    def _clean_long_text(cls, value: object) -> str | None:
        return sanitize_text(value if isinstance(value, str) else None, max_len=_TEXT_MAX)

    @field_validator("quantity")
    @classmethod
    def _clamp_quantity(cls, value: int | None) -> int | None:
        return None if value is None else max(1, value)

    @field_validator("confidence_score")
    @classmethod
    def _clamp_confidence(cls, value: float | None) -> float | None:
        return None if value is None else min(1.0, max(0.0, value))

    @field_validator("weight_kg", "height_cm", "width_cm", "depth_cm", "estimated_value")
    @classmethod
    def _drop_non_positive(cls, value: float | None) -> float | None:
        # Negative/zero physical estimates are meaningless; treat as "unknown".
        return None if value is None or value <= 0 else value

    @field_validator("packing_difficulty", "lifting_difficulty", mode="before")
    @classmethod
    def _normalise_difficulty(cls, value: object) -> str | None:
        if not isinstance(value, str):
            return None
        return _DIFFICULTY_ALIASES.get(value.strip().lower())


class AISurveyAnalysis(BaseModel):
    """The full structured result of one analysis call."""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    items: list[AIDetectedItem] = Field(default_factory=list)
    needs_more_images: bool = Field(default=False, alias="needsMoreImages")
    requested_images: list[str] = Field(default_factory=list, alias="requestedImages")

    @model_validator(mode="after")
    def _guardrails(self) -> AISurveyAnalysis:
        # Drop items the model left unnamed (blank after sanitising), then cap the
        # list so a runaway/adversarial response can't insert unbounded rows.
        named = [item for item in self.items if item.item_name]
        self.items = named[: settings.ai_max_items]
        self.requested_images = [
            cleaned
            for raw in self.requested_images
            if (cleaned := sanitize_text(raw, max_len=_NAME_MAX)) is not None
        ][:50]
        return self
