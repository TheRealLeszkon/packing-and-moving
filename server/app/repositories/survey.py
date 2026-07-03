"""Survey repository — encapsulates all survey queries."""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from sqlalchemy import func, select

from app.models.enums import SurveyStatus
from app.models.survey import Survey
from app.repositories.base import BaseRepository


class SurveyRepository(BaseRepository[Survey]):
    model = Survey

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
