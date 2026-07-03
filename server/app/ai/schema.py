"""AI response schema.

Provider-neutral Pydantic DTOs that every ``AIProvider`` must return. The model's
raw JSON (camelCase, loosely typed) is validated and normalised here *before* it
ever reaches the database — untrusted AI output is coerced into known types,
clamped to sane ranges, and mapped onto our domain enums. Anything the model
cannot supply stays ``None`` rather than being fabricated.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator

_DIFFICULTY_ALIASES = {"easy": "easy", "medium": "medium", "hard": "hard"}


class AIDetectedItem(BaseModel):
    """A single inventory item detected by the model.

    Field names are snake_case; the model emits camelCase, mapped via aliases.
    ``populate_by_name`` also lets internal/test code build instances directly.
    """

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    item_name: str = Field(alias="itemName", min_length=1, max_length=255)
    category: str | None = Field(default=None, alias="category", max_length=128)
    quantity: int | None = Field(default=None, alias="quantity")
    room_location: str | None = Field(default=None, alias="roomLocation", max_length=255)

    needs_to_ship: bool | None = Field(default=None, alias="needsToShip")
    confidence_score: float | None = Field(default=None, alias="confidenceScore")

    weight_kg: float | None = Field(default=None, alias="estimatedWeightKg")
    height_cm: float | None = Field(default=None, alias="estimatedHeightCm")
    width_cm: float | None = Field(default=None, alias="estimatedWidthCm")
    depth_cm: float | None = Field(default=None, alias="estimatedDepthCm")
    estimated_value: float | None = Field(default=None, alias="estimatedValue")
    material: str | None = Field(default=None, alias="estimatedMaterial", max_length=128)

    fragile: bool | None = Field(default=None, alias="isFragile")
    needs_disassembly: bool | None = Field(default=None, alias="needsDisassembly")
    needs_special_handling: bool | None = Field(default=None, alias="needsSpecialHandling")

    packing_difficulty: str | None = Field(default=None, alias="packingDifficulty")
    lifting_difficulty: str | None = Field(default=None, alias="liftingDifficulty")

    condition: str | None = Field(default=None, alias="condition", max_length=64)
    remarks: str | None = Field(default=None, alias="remarks", max_length=4000)

    # 0-based indexes into the image batch this item was detected in (see prompt).
    # Used to link the item to its evidencing media; empty if the model omits it.
    source_image_indexes: list[int] = Field(
        default_factory=list, alias="sourceImageIndexes"
    )

    @field_validator("quantity")
    @classmethod
    def _clamp_quantity(cls, value: int | None) -> int | None:
        if value is None:
            return None
        return max(1, value)

    @field_validator("confidence_score")
    @classmethod
    def _clamp_confidence(cls, value: float | None) -> float | None:
        if value is None:
            return None
        return min(1.0, max(0.0, value))

    @field_validator(
        "weight_kg", "height_cm", "width_cm", "depth_cm", "estimated_value"
    )
    @classmethod
    def _drop_non_positive(cls, value: float | None) -> float | None:
        # Negative/zero physical estimates are meaningless; treat as "unknown".
        if value is None or value <= 0:
            return None
        return value

    @field_validator("packing_difficulty", "lifting_difficulty")
    @classmethod
    def _normalise_difficulty(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return _DIFFICULTY_ALIASES.get(value.strip().lower())


class AISurveyAnalysis(BaseModel):
    """The full structured result of one analysis call."""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    items: list[AIDetectedItem] = Field(default_factory=list)
    needs_more_images: bool = Field(default=False, alias="needsMoreImages")
    requested_images: list[str] = Field(default_factory=list, alias="requestedImages")
