"""Survey repository — encapsulates all survey queries."""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from sqlalchemy import func, select
from sqlalchemy.orm import selectinload

from app.models.ai_analysis_run import AIAnalysisRun
from app.models.enums import AIRunStatus, ProcessingStatus, SurveyStatus
from app.models.media import Media
from app.models.survey import Survey
from app.models.survey_item import SurveyItem
from app.repositories.base import BaseRepository


class SurveyRepository(BaseRepository[Survey]):
    model = Survey

    async def list_items(self, survey_id: uuid.UUID) -> Sequence[SurveyItem]:
        """All inventory items for a survey, with linked media eagerly loaded."""
        return (
            (
                await self.session.execute(
                    select(SurveyItem)
                    .where(SurveyItem.survey_id == survey_id)
                    .options(selectinload(SurveyItem.media))
                    .order_by(SurveyItem.created_at)
                )
            )
            .scalars()
            .all()
        )

    async def count_unfinished_media(self, survey_id: uuid.UUID) -> int:
        """Media still pending/processing for the survey (drives the progress stage)."""
        return (
            await self.session.execute(
                select(func.count())
                .select_from(Media)
                .where(
                    Media.survey_id == survey_id,
                    Media.processing_status.in_(
                        [ProcessingStatus.PENDING, ProcessingStatus.PROCESSING]
                    ),
                )
            )
        ).scalar_one()

    async def has_pending_ai_run(self, survey_id: uuid.UUID) -> bool:
        """Whether an AI analysis run is queued/in-flight for the survey."""
        return (
            await self.session.execute(
                select(AIAnalysisRun.id)
                .where(
                    AIAnalysisRun.survey_id == survey_id,
                    AIAnalysisRun.status == AIRunStatus.PENDING,
                )
                .limit(1)
            )
        ).first() is not None

    async def list_for_customer(
        self, customer_id: uuid.UUID, *, limit: int, offset: int
    ) -> tuple[Sequence[Survey], int]:
        return await self._list_and_count(Survey.customer_id == customer_id, limit, offset)

    async def list_for_surveyor(
        self, surveyor_id: uuid.UUID, *, limit: int, offset: int
    ) -> tuple[Sequence[Survey], int]:
        return await self._list_and_count(Survey.surveyor_id == surveyor_id, limit, offset)

    async def list_available(
        self, *, limit: int, offset: int
    ) -> tuple[Sequence[Survey], int]:
        """Unassigned survey requests open for a surveyor to accept."""
        criteria = (Survey.status == SurveyStatus.SCHEDULED) & (Survey.surveyor_id.is_(None))
        return await self._list_and_count(criteria, limit, offset)

    async def _list_and_count(
        self, criteria, limit: int, offset: int
    ) -> tuple[Sequence[Survey], int]:
        rows = (
            await self.session.execute(
                select(Survey)
                .where(criteria)
                .order_by(Survey.created_at.desc())
                .limit(limit)
                .offset(offset)
            )
        ).scalars().all()
        total = (
            await self.session.execute(
                select(func.count()).select_from(Survey).where(criteria)
            )
        ).scalar_one()
        return rows, total
