"""Admin response schemas: AI-run and processing-job views + metrics."""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict

from app.models.enums import AIRunStatus, JobStatus, JobType


class AIRunResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    survey_id: uuid.UUID
    provider: str
    model: str
    prompt_version: str
    status: AIRunStatus
    request_media_count: int | None
    prompt_tokens: int | None
    completion_tokens: int | None
    total_tokens: int | None
    latency_ms: int | None
    error: str | None
    created_at: datetime


class ProcessingJobResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    media_id: uuid.UUID
    job_type: JobType
    status: JobStatus
    attempts: int
    max_attempts: int
    last_error: str | None
    scheduled_at: datetime | None
    started_at: datetime | None
    finished_at: datetime | None
    created_at: datetime


class MetricsResponse(BaseModel):
    requests_total: int
    requests_in_flight: int
    errors_total: int
    by_status_class: dict[str, int]
    avg_latency_ms: float

    @classmethod
    def from_snapshot(cls, snapshot: dict[str, Any]) -> MetricsResponse:
        return cls(**snapshot)
