"""Admin routes (ADMIN role only).

Fleet-wide read access for operations/support plus a job-requeue action for
recovering failed or dead-lettered processing jobs. Every route requires the
ADMIN role via ``RequireAdmin``; business logic lives in ``AdminService``.
"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Query

from app.core.metrics import metrics
from app.core.responses import SuccessResponse
from app.dependencies.auth import RequireAdmin
from app.dependencies.services import AdminServiceDep
from app.schemas.admin import AIRunResponse, MetricsResponse, ProcessingJobResponse
from app.schemas.pagination import Page, PageParams
from app.schemas.survey import SurveyResponse
from app.schemas.user import UserResponse

router = APIRouter(prefix="/admin", tags=["admin"])

_PageQuery = Annotated[PageParams, Query()]


@router.get("/surveys", response_model=SuccessResponse[Page[SurveyResponse]])
async def list_all_surveys(
    _admin: RequireAdmin, service: AdminServiceDep, params: _PageQuery
) -> SuccessResponse[Page[SurveyResponse]]:
    rows, total = await service.list_surveys(limit=params.limit, offset=params.offset)
    items = [SurveyResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.get("/users", response_model=SuccessResponse[Page[UserResponse]])
async def list_all_users(
    _admin: RequireAdmin, service: AdminServiceDep, params: _PageQuery
) -> SuccessResponse[Page[UserResponse]]:
    rows, total = await service.list_users(limit=params.limit, offset=params.offset)
    items = [UserResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.get("/ai-runs", response_model=SuccessResponse[Page[AIRunResponse]])
async def list_ai_runs(
    _admin: RequireAdmin, service: AdminServiceDep, params: _PageQuery
) -> SuccessResponse[Page[AIRunResponse]]:
    rows, total = await service.list_ai_runs(limit=params.limit, offset=params.offset)
    items = [AIRunResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.get("/jobs", response_model=SuccessResponse[Page[ProcessingJobResponse]])
async def list_failed_jobs(
    _admin: RequireAdmin, service: AdminServiceDep, params: _PageQuery
) -> SuccessResponse[Page[ProcessingJobResponse]]:
    """Failed or dead-lettered processing jobs awaiting attention."""
    rows, total = await service.list_failed_jobs(limit=params.limit, offset=params.offset)
    items = [ProcessingJobResponse.model_validate(r) for r in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.post("/jobs/{job_id}/requeue", response_model=SuccessResponse[ProcessingJobResponse])
async def requeue_job(
    job_id: uuid.UUID, _admin: RequireAdmin, service: AdminServiceDep
) -> SuccessResponse[ProcessingJobResponse]:
    job = await service.requeue_job(job_id)
    return SuccessResponse(data=ProcessingJobResponse.model_validate(job))


@router.get("/metrics", response_model=SuccessResponse[MetricsResponse])
async def get_metrics(_admin: RequireAdmin) -> SuccessResponse[MetricsResponse]:
    return SuccessResponse(data=MetricsResponse.from_snapshot(metrics.snapshot()))
