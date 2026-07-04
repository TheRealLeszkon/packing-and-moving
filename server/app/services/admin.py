"""Admin service — cross-cutting reads and operational actions.

Backs the ADMIN-only endpoints: fleet-wide listings (surveys, users, AI runs,
failing jobs) and the ability to requeue a job that failed or dead-lettered.
Requeuing resets the ledger row and re-dispatches it (via a post-commit hook, so
the broker only sees it once the reset is durable) and puts the media back to
PENDING.
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from datetime import UTC, datetime
from functools import partial
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import register_after_commit
from app.models.ai_analysis_run import AIAnalysisRun
from app.models.enums import JobStatus, ProcessingStatus
from app.models.media import Media
from app.models.media_processing_job import MediaProcessingJob
from app.models.survey import Survey
from app.models.user import User
from app.workers.dispatch import ProcessingDispatcher

_REQUEUABLE = frozenset({JobStatus.FAILED, JobStatus.DEAD_LETTER})


class AdminService:
    def __init__(self, session: AsyncSession, dispatcher: ProcessingDispatcher) -> None:
        self._session = session
        self._dispatcher = dispatcher

    async def list_surveys(self, *, limit: int, offset: int) -> tuple[Sequence[Survey], int]:
        return await self._page(Survey, Survey.created_at.desc(), limit, offset)

    async def list_users(self, *, limit: int, offset: int) -> tuple[Sequence[User], int]:
        return await self._page(User, User.created_at.desc(), limit, offset)

    async def list_ai_runs(
        self, *, limit: int, offset: int
    ) -> tuple[Sequence[AIAnalysisRun], int]:
        return await self._page(AIAnalysisRun, AIAnalysisRun.created_at.desc(), limit, offset)

    async def list_failed_jobs(
        self, *, limit: int, offset: int
    ) -> tuple[Sequence[MediaProcessingJob], int]:
        criteria = MediaProcessingJob.status.in_(_REQUEUABLE)
        rows = (
            await self._session.execute(
                select(MediaProcessingJob)
                .where(criteria)
                .order_by(MediaProcessingJob.created_at.desc())
                .limit(limit)
                .offset(offset)
            )
        ).scalars().all()
        total = (
            await self._session.execute(
                select(func.count()).select_from(MediaProcessingJob).where(criteria)
            )
        ).scalar_one()
        return rows, total

    async def requeue_job(self, job_id: uuid.UUID) -> MediaProcessingJob:
        job = await self._session.get(MediaProcessingJob, job_id)
        if job is None:
            raise NotFoundError("Job not found.")
        if job.status not in _REQUEUABLE:
            raise ConflictError("Only failed or dead-lettered jobs can be requeued.")
        job.status = JobStatus.QUEUED
        job.attempts = 0
        job.last_error = None
        job.started_at = None
        job.finished_at = None
        job.scheduled_at = datetime.now(UTC)
        media = await self._session.get(Media, job.media_id)
        if media is not None:
            media.processing_status = ProcessingStatus.PENDING
        await self._session.flush()
        register_after_commit(
            self._session, partial(self._dispatcher.dispatch, job.media_id, job.job_type)
        )
        return job

    async def _page(
        self, model: Any, order_by: Any, limit: int, offset: int
    ) -> tuple[Sequence[Any], int]:
        rows = (
            await self._session.execute(
                select(model).order_by(order_by).limit(limit).offset(offset)
            )
        ).scalars().all()
        total = (
            await self._session.execute(select(func.count()).select_from(model))
        ).scalar_one()
        return rows, total
