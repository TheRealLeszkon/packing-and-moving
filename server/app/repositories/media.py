"""Media repository."""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from sqlalchemy import func, select

from app.models.media import Media
from app.repositories.base import BaseRepository


class MediaRepository(BaseRepository[Media]):
    model = Media

    async def list_for_survey(
        self, survey_id: uuid.UUID, *, limit: int, offset: int
    ) -> tuple[Sequence[Media], int]:
        criteria = Media.survey_id == survey_id
        rows = (
            await self.session.execute(
                select(Media)
                .where(criteria)
                .order_by(Media.upload_timestamp.desc())
                .limit(limit)
                .offset(offset)
            )
        ).scalars().all()
        total = (
            await self.session.execute(
                select(func.count()).select_from(Media).where(criteria)
            )
        ).scalar_one()
        return rows, total
