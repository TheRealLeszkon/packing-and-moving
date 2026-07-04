"""Item-level inventory routes.

Operations keyed by a single item id (edit, delete, split) or across items
(merge) live under ``/survey-items``; creating an item and the survey summary are
survey-scoped and live in the surveys router. All mutations are authorised and
state-gated inside ``SurveyItemService``.
"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, status

from app.core.responses import SuccessResponse
from app.dependencies.auth import CurrentUser
from app.dependencies.services import ItemServiceDep
from app.schemas.common import MessageResponse
from app.schemas.item import (
    ItemMergeRequest,
    ItemSplitRequest,
    SurveyItemListResponse,
    SurveyItemResponse,
    SurveyItemUpdate,
)

router = APIRouter(prefix="/survey-items", tags=["survey-items"])


@router.patch(
    "/{item_id}",
    response_model=SuccessResponse[SurveyItemResponse],
    summary="Edit an inventory item",
)
async def update_item(
    item_id: uuid.UUID,
    payload: SurveyItemUpdate,
    user: CurrentUser,
    service: ItemServiceDep,
) -> SuccessResponse[SurveyItemResponse]:
    item = await service.update(user, item_id, payload)
    return SuccessResponse(data=SurveyItemResponse.from_item(item))


@router.delete(
    "/{item_id}",
    response_model=SuccessResponse[MessageResponse],
    summary="Delete an inventory item",
)
async def delete_item(
    item_id: uuid.UUID, user: CurrentUser, service: ItemServiceDep
) -> SuccessResponse[MessageResponse]:
    await service.delete(user, item_id)
    return SuccessResponse(data=MessageResponse(detail="Item deleted."))


@router.post(
    "/merge",
    response_model=SuccessResponse[SurveyItemResponse],
    summary="Merge duplicate items into one",
)
async def merge_items(
    payload: ItemMergeRequest, user: CurrentUser, service: ItemServiceDep
) -> SuccessResponse[SurveyItemResponse]:
    item = await service.merge(user, payload)
    return SuccessResponse(data=SurveyItemResponse.from_item(item))


@router.post(
    "/{item_id}/split",
    response_model=SuccessResponse[SurveyItemListResponse],
    status_code=status.HTTP_201_CREATED,
    summary="Split one item into several",
)
async def split_item(
    item_id: uuid.UUID,
    payload: ItemSplitRequest,
    user: CurrentUser,
    service: ItemServiceDep,
) -> SuccessResponse[SurveyItemListResponse]:
    items = await service.split(user, item_id, payload)
    payload_items = [SurveyItemResponse.from_item(i) for i in items]
    return SuccessResponse(
        data=SurveyItemListResponse(items=payload_items, count=len(payload_items))
    )
