"""Survey request board — how surveyors find and claim work."""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Query

from app.core.responses import SuccessResponse
from app.dependencies.auth import RequireSurveyor
from app.dependencies.services import SurveyServiceDep
from app.schemas.pagination import Page, PageParams
from app.schemas.survey import SurveyResponse

router = APIRouter(prefix="/survey-requests", tags=["survey-requests"])

_PageQuery = Annotated[PageParams, Query()]


def _page(rows, total: int, params: PageParams) -> SuccessResponse[Page[SurveyResponse]]:
    items = [SurveyResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.get(
    "/available",
    response_model=SuccessResponse[Page[SurveyResponse]],
    summary="List unassigned survey requests",
)
async def available_requests(
    surveyor: RequireSurveyor, service: SurveyServiceDep, params: _PageQuery
) -> SuccessResponse[Page[SurveyResponse]]:
    rows, total = await service.list_available(params)
    return _page(rows, total, params)


@router.get(
    "/assigned",
    response_model=SuccessResponse[Page[SurveyResponse]],
    summary="List surveys assigned to the surveyor",
)
async def assigned_requests(
    surveyor: RequireSurveyor, service: SurveyServiceDep, params: _PageQuery
) -> SuccessResponse[Page[SurveyResponse]]:
    rows, total = await service.list_assigned(surveyor, params)
    return _page(rows, total, params)


@router.post(
    "/{survey_id}/accept",
    response_model=SuccessResponse[SurveyResponse],
    summary="Accept (claim) a survey request",
)
async def accept_request(
    survey_id: uuid.UUID, surveyor: RequireSurveyor, service: SurveyServiceDep
) -> SuccessResponse[SurveyResponse]:
    survey = await service.accept(surveyor, survey_id)
    return SuccessResponse(data=SurveyResponse.model_validate(survey))
