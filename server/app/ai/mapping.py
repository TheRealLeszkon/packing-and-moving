"""Map a validated AI item onto a persistent ``SurveyItem``.

Kept separate from the schema so the anti-corruption boundary is explicit: the AI
schema owns *parsing/normalising* untrusted model output; this module owns
*translating* it into our domain model (enums, Decimals, provenance). Floats are
converted to ``Decimal`` via ``str`` to avoid binary-float rounding artefacts.
"""

from __future__ import annotations

import uuid
from decimal import Decimal

from app.ai.schema import AIDetectedItem
from app.models.enums import Difficulty, ItemCondition
from app.models.survey_item import SurveyItem

_DIFFICULTY = {d.value: d for d in Difficulty}

# The model's condition vocabulary (from the prompt) mapped onto ours; unknown
# values fall back to None rather than guessing.
_CONDITION = {
    "new": ItemCondition.NEW,
    "excellent": ItemCondition.NEW,
    "good": ItemCondition.GOOD,
    "fair": ItemCondition.FAIR,
    "poor": ItemCondition.POOR,
    "damaged": ItemCondition.DAMAGED,
}


def _dec(value: float | None) -> Decimal | None:
    return None if value is None else Decimal(str(value))


def _difficulty(value: str | None) -> Difficulty | None:
    return _DIFFICULTY.get(value) if value else None


def _condition(value: str | None) -> ItemCondition | None:
    return _CONDITION.get(value.strip().lower()) if value else None


def build_survey_item(survey_id: uuid.UUID, item: AIDetectedItem) -> SurveyItem:
    """Construct an unpersisted ``SurveyItem`` (source="ai") from a detected item."""
    return SurveyItem(
        survey_id=survey_id,
        item_name=item.item_name,
        category=item.category,
        quantity=item.quantity or 1,
        room_location=item.room_location,
        height_cm=_dec(item.height_cm),
        width_cm=_dec(item.width_cm),
        depth_cm=_dec(item.depth_cm),
        weight_kg=_dec(item.weight_kg),
        material=item.material,
        fragile=bool(item.fragile),
        needs_disassembly=bool(item.needs_disassembly),
        needs_special_handling=bool(item.needs_special_handling),
        needs_to_ship=True if item.needs_to_ship is None else item.needs_to_ship,
        packing_difficulty=_difficulty(item.packing_difficulty),
        lifting_difficulty=_difficulty(item.lifting_difficulty),
        estimated_value=_dec(item.estimated_value),
        condition=_condition(item.condition),
        remarks=item.remarks,
        confidence_score=_dec(item.confidence_score),
        source="ai",
    )
