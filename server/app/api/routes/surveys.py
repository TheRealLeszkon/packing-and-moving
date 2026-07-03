"""Survey routes: CRUD + workflow transitions.

Routes stay thin — auth/role via dependencies, everything else delegated to
``SurveyService``. Specific paths (``/my``) are declared before the ``/{id}``
path so they are matched first.
"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Query, status

from app.core.responses import SuccessResponse
from app.dependencies.auth import CurrentUser, RequireCustomer
from app.dependencies.services import SurveyServiceDep
from app.schemas.common import MessageResponse
from app.schemas.item import SurveyItemListResponse, SurveyItemResponse
from app.schemas.pagination import Page, PageParams
from app.schemas.survey import (
    CancelRequest,
    RejectRequest,
    SurveyCreate,
    SurveyResponse,
    SurveyStatusResponse,
)
from app.services.survey_state_machine import SurveyAction, available_actions

router = APIRouter(prefix="/surveys", tags=["surveys"])

_PageQuery = Annotated[PageParams, Query()]


def _page(rows, total: int, params: PageParams) -> SuccessResponse[Page[SurveyResponse]]:
    items = [SurveyResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


def _one(survey) -> SuccessResponse[SurveyResponse]:
    return SuccessResponse(data=SurveyResponse.model_validate(survey))


@router.post(
    "",
    response_model=SuccessResponse[SurveyResponse],
    status_code=status.HTTP_201_CREATED,
    summary="Create a survey request",
)
async def create_survey(
    payload: SurveyCreate, customer: RequireCustomer, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    survey = await service.create(customer, payload)
    return _one(survey)


@router.get(
    "/my",
    response_model=SuccessResponse[Page[SurveyResponse]],
    summary="List the caller's surveys (owned or assigned)",
)
async def my_surveys(
    user: CurrentUser, service: SurveyServiceDep, params: _PageQuery
) -> SuccessResponse[Page[SurveyResponse]]:
    rows, total = await service.list_mine(user, params)
    return _page(rows, total, params)


@router.get(
    "/{survey_id}",
    response_model=SuccessResponse[SurveyResponse],
    summary="Retrieve a survey",
)
async def get_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    return _one(await service.get_visible(user, survey_id))


@router.get(
    "/{survey_id}/status",
    response_model=SuccessResponse[SurveyStatusResponse],
    summary="Get workflow status and available actions",
)
async def survey_status(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyStatusResponse]:
    survey = await service.get_visible(user, survey_id)
    return SuccessResponse(
        data=SurveyStatusResponse(
            id=survey.id,
            status=survey.status,
            available_actions=[a.value for a in available_actions(survey, user)],
        )
    )


@router.get(
    "/{survey_id}/items",
    response_model=SuccessResponse[SurveyItemListResponse],
    summary="List the survey's inventory items (AI-generated + manual)",
)
async def survey_items(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyItemListResponse]:
    items = await service.list_items(user, survey_id)
    payload = [SurveyItemResponse.from_item(item) for item in items]
    return SuccessResponse(
        data=SurveyItemListResponse(items=payload, count=len(payload))
    )


@router.delete(
    "/{survey_id}",
    response_model=SuccessResponse[MessageResponse],
    summary="Delete an unassigned survey",
)
async def delete_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[MessageResponse]:
    await service.delete(user, survey_id)
    return SuccessResponse(data=MessageResponse(detail="Survey deleted."))


# ---- workflow transitions -------------------------------------------------
@router.post(
    "/{survey_id}/start",
    response_model=SuccessResponse[SurveyResponse],
    summary="Surveyor: begin the on-site survey",
)
async def start_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    return _one(await service.perform(user, survey_id, SurveyAction.START))


@router.post(
    "/{survey_id}/complete",
    response_model=SuccessResponse[SurveyResponse],
    summary="Surveyor: finish on-site capture (hands off to processing)",
)
async def complete_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    return _one(await service.perform(user, survey_id, SurveyAction.COMPLETE))


@router.post(
    "/{survey_id}/submit",
    response_model=SuccessResponse[SurveyResponse],
    summary="Surveyor: submit the reviewed inventory for customer approval",
)
async def submit_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    return _one(await service.perform(user, survey_id, SurveyAction.SUBMIT))


@router.post(
    "/{survey_id}/approve",
    response_model=SuccessResponse[SurveyResponse],
    summary="Customer: approve the survey",
)
async def approve_survey(
    survey_id: uuid.UUID, user: CurrentUser, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    return _one(await service.perform(user, survey_id, SurveyAction.APPROVE))


@router.post(
    "/{survey_id}/reject",
    response_model=SuccessResponse[SurveyResponse],
    summary="Customer: request revisions",
)
async def reject_survey(
    survey_id: uuid.UUID,
    payload: RejectRequest,
    user: CurrentUser,
    service: SurveyServiceDep,
) -> SuccessResponse[SurveyResponse]:
    survey = await service.perform(user, survey_id, SurveyAction.REJECT, reason=payload.reason)
    return _one(survey)


@router.post(
    "/{survey_id}/cancel",
    response_model=SuccessResponse[SurveyResponse],
    summary="Customer: cancel the survey",
)
async def cancel_survey(
    survey_id: uuid.UUID,
    user: CurrentUser,
    service: SurveyServiceDep,
    payload: CancelRequest | None = None,
) -> SuccessResponse[SurveyResponse]:
    reason = payload.reason if payload else None
    survey = await service.perform(user, survey_id, SurveyAction.CANCEL, reason=reason)
    return _one(survey)
