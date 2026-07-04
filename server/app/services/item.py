"""Survey item service — inventory CRUD, merge/split, and summary.

Owns the rules for editing a survey's inventory. Mutations are restricted to the
assigned surveyor (or an admin) and only while the survey is in a review state
(READY_FOR_REVIEW or REVISION_REQUIRED) — the window in which a surveyor corrects
the AI-generated inventory before submitting it. Every mutation recomputes the
survey's aggregate totals so they stay consistent with the items.
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from decimal import Decimal
from typing import Any

from app.core.exceptions import (
    AuthorizationError,
    ConflictError,
    NotFoundError,
    ValidationError,
)
from app.models.enums import SurveyStatus, UserRole
from app.models.survey import Survey
from app.models.survey_item import SurveyItem
from app.models.user import User
from app.repositories.item import SurveyItemRepository
from app.repositories.survey import SurveyRepository
from app.schemas.item import (
    CategoryBreakdown,
    ItemMergeRequest,
    ItemSplitRequest,
    RoomBreakdown,
    SurveyItemCreate,
    SurveyItemUpdate,
    SurveySummaryResponse,
)
from app.services.visibility import can_view_survey

# Scalar columns copied when cloning an item for a split.
_CLONE_FIELDS = (
    "item_name", "category", "quantity", "room_location",
    "height_cm", "width_cm", "depth_cm", "weight_kg", "material",
    "fragile", "needs_disassembly", "needs_special_handling", "needs_to_ship",
    "packing_difficulty", "lifting_difficulty",
    "estimated_value", "condition", "remarks",
)

_UNSET = object()


class SurveyItemService:
    _EDITABLE_STATES = frozenset(
        {SurveyStatus.READY_FOR_REVIEW, SurveyStatus.REVISION_REQUIRED}
    )

    def __init__(self, *, items: SurveyItemRepository, surveys: SurveyRepository) -> None:
        self._items = items
        self._surveys = surveys
        self._session = items.session

    # ---- reads ------------------------------------------------------------
    async def summary(self, user: User, survey_id: uuid.UUID) -> SurveySummaryResponse:
        survey = await self._surveys.get(survey_id)
        if survey is None or not can_view_survey(survey, user):
            raise NotFoundError("Survey not found.")
        items = await self._items.list_for_survey(survey_id)
        return _build_summary(survey_id, items)

    # ---- mutations --------------------------------------------------------
    async def create(
        self, user: User, survey_id: uuid.UUID, data: SurveyItemCreate
    ) -> SurveyItem:
        survey = await self._editable_survey(user, survey_id)
        item = SurveyItem(
            survey_id=survey_id,
            source="manual",
            **data.model_dump(exclude={"media_ids"}),
        )
        item.media = await self._resolve_media(survey_id, data.media_ids)
        self._items.add(item)
        await self._session.flush()
        await self._recompute_totals(survey)
        return item

    async def update(
        self, user: User, item_id: uuid.UUID, data: SurveyItemUpdate
    ) -> SurveyItem:
        item = await self._require_item(item_id)
        survey = await self._editable_survey(user, item.survey_id)
        changes = data.model_dump(exclude_unset=True)
        media_ids = changes.pop("media_ids", _UNSET)
        for field, value in changes.items():
            setattr(item, field, value)
        if media_ids is not _UNSET:
            item.media = await self._resolve_media(item.survey_id, media_ids or [])
        await self._session.flush()
        await self._recompute_totals(survey)
        return item

    async def delete(self, user: User, item_id: uuid.UUID) -> None:
        item = await self._require_item(item_id)
        survey = await self._editable_survey(user, item.survey_id)
        await self._items.delete(item)
        await self._recompute_totals(survey)

    async def merge(self, user: User, req: ItemMergeRequest) -> SurveyItem:
        if len(set(req.item_ids)) != len(req.item_ids):
            raise ValidationError("item_ids must be distinct.")
        items = [await self._require_item(i) for i in req.item_ids]
        if len({i.survey_id for i in items}) != 1:
            raise ValidationError("All items must belong to the same survey.")
        survey = await self._editable_survey(user, items[0].survey_id)

        survivor, others = items[0], items[1:]
        # Union the evidencing media onto the survivor (dedup by id, keep order).
        merged: dict[uuid.UUID, Any] = {m.id: m for m in survivor.media}
        for other in others:
            for media in other.media:
                merged.setdefault(media.id, media)
        survivor.media = list(merged.values())
        if req.item_name is not None:
            survivor.item_name = req.item_name
        if req.quantity is not None:
            survivor.quantity = req.quantity
        for other in others:
            await self._items.delete(other)
        await self._session.flush()
        await self._recompute_totals(survey)
        return survivor

    async def split(
        self, user: User, item_id: uuid.UUID, req: ItemSplitRequest
    ) -> list[SurveyItem]:
        original = await self._require_item(item_id)
        survey = await self._editable_survey(user, original.survey_id)
        inherited_media = list(original.media)

        new_items: list[SurveyItem] = []
        for part in req.parts:
            clone = SurveyItem(
                survey_id=original.survey_id,
                source="manual",
                **{f: getattr(original, f) for f in _CLONE_FIELDS},
            )
            changes = part.model_dump(exclude_unset=True)
            media_ids = changes.pop("media_ids", _UNSET)
            for field, value in changes.items():
                setattr(clone, field, value)
            clone.media = (
                inherited_media
                if media_ids is _UNSET
                else await self._resolve_media(original.survey_id, media_ids or [])
            )
            self._items.add(clone)
            new_items.append(clone)

        await self._items.delete(original)
        await self._session.flush()
        await self._recompute_totals(survey)
        return new_items

    # ---- internals --------------------------------------------------------
    async def _require_item(self, item_id: uuid.UUID) -> SurveyItem:
        item = await self._items.get_with_media(item_id)
        if item is None:
            raise NotFoundError("Item not found.")
        return item

    async def _editable_survey(self, user: User, survey_id: uuid.UUID) -> Survey:
        survey = await self._surveys.get(survey_id)
        if survey is None or not can_view_survey(survey, user):
            raise NotFoundError("Survey not found.")
        is_admin = user.role is UserRole.ADMIN
        if not (is_admin or survey.surveyor_id == user.id):
            raise AuthorizationError("Only the assigned surveyor may edit this inventory.")
        if survey.status not in self._EDITABLE_STATES:
            raise ConflictError(
                "Inventory can only be edited while the survey is under review."
            )
        return survey

    async def _resolve_media(
        self, survey_id: uuid.UUID, media_ids: Sequence[uuid.UUID]
    ) -> list[Any]:
        """Load the requested media, rejecting any id that isn't in this survey."""
        wanted = list(dict.fromkeys(media_ids))  # dedupe, preserve order
        found = await self._items.media_in_survey(survey_id, wanted)
        if len(found) != len(wanted):
            raise ValidationError("One or more media_ids are invalid for this survey.")
        by_id = {m.id: m for m in found}
        return [by_id[mid] for mid in wanted]

    async def _recompute_totals(self, survey: Survey) -> None:
        items = await self._items.list_for_survey(survey.id)
        total_value = Decimal("0")
        total_volume = Decimal("0")  # cubic metres
        for item in items:
            qty = item.quantity or 1
            if item.estimated_value is not None:
                total_value += item.estimated_value * qty
            if item.height_cm and item.width_cm and item.depth_cm:
                volume_m3 = (item.height_cm / 100) * (item.width_cm / 100) * (item.depth_cm / 100)
                total_volume += volume_m3 * qty
        survey.total_value_estimate = total_value
        survey.total_volume_estimate = total_volume
        await self._session.flush()


def _build_summary(
    survey_id: uuid.UUID, items: Sequence[SurveyItem]
) -> SurveySummaryResponse:
    total_quantity = 0
    total_value = Decimal("0")
    total_volume = Decimal("0")
    fragile = disassembly = special = 0
    by_category: dict[str | None, list[int]] = {}
    by_room: dict[str | None, list[int]] = {}

    for item in items:
        qty = item.quantity or 1
        total_quantity += qty
        if item.estimated_value is not None:
            total_value += item.estimated_value * qty
        if item.height_cm and item.width_cm and item.depth_cm:
            volume_m3 = (item.height_cm / 100) * (item.width_cm / 100) * (item.depth_cm / 100)
            total_volume += volume_m3 * qty
        fragile += 1 if item.fragile else 0
        disassembly += 1 if item.needs_disassembly else 0
        special += 1 if item.needs_special_handling else 0
        by_category.setdefault(item.category, [0, 0])
        by_category[item.category][0] += 1
        by_category[item.category][1] += qty
        by_room.setdefault(item.room_location, [0, 0])
        by_room[item.room_location][0] += 1
        by_room[item.room_location][1] += qty

    return SurveySummaryResponse(
        survey_id=survey_id,
        distinct_items=len(items),
        total_quantity=total_quantity,
        total_volume_m3=total_volume,
        total_value=total_value,
        fragile_items=fragile,
        needs_disassembly_items=disassembly,
        needs_special_handling_items=special,
        by_category=[
            CategoryBreakdown(category=c, distinct_items=v[0], quantity=v[1])
            for c, v in by_category.items()
        ],
        by_room=[
            RoomBreakdown(room_location=r, distinct_items=v[0], quantity=v[1])
            for r, v in by_room.items()
        ],
    )
