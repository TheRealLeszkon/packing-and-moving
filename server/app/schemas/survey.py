"""Survey request/response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import ProcessingStage, ReanalysisMode, SurveyStatus


class SurveyCreate(BaseModel):
    """Customer-supplied survey request."""

    name: str = Field(min_length=1, max_length=255)
    origin_address: str = Field(min_length=1, max_length=1000)
    destination_address: str = Field(min_length=1, max_length=1000)
    preferred_datetime: datetime | None = None


class SurveyResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    origin_address: str
    destination_address: str
    customer_id: uuid.UUID
    surveyor_id: uuid.UUID | None
    preferred_datetime: datetime | None
    status: SurveyStatus
    total_volume_estimate: Decimal | None
    total_value_estimate: Decimal | None
    created_at: datetime
    updated_at: datetime


class SurveyStatusResponse(BaseModel):
    """Lightweight workflow view: current status + what the caller can do next."""

    id: uuid.UUID
    status: SurveyStatus
    available_actions: list[str]
    # Observed pipeline stage while ``status`` is PROCESSING; null otherwise.
    processing_stage: ProcessingStage | None = None


class ReanalyzeRequest(BaseModel):
    """Re-run AI analysis on a survey under review."""

    mode: ReanalysisMode = ReanalysisMode.ALL


class ReanalyzeResponse(BaseModel):
    survey_id: uuid.UUID
    status: SurveyStatus
    job_ids: list[uuid.UUID]


class RejectRequest(BaseModel):
    """Customer's revision request — a reason is required so the surveyor knows
    what to change."""

    reason: str = Field(min_length=1, max_length=2000)


class CancelRequest(BaseModel):
    reason: str | None = Field(default=None, max_length=2000)
