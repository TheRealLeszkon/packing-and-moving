"""Survey item repository — item-level queries with media eagerly loaded.

Item mutations (edit/merge/split) need the ``media`` relationship available so
links can be read and rewritten without triggering async lazy-loads, hence the
``selectinload`` on the single-item and list fetches.
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from sqlalchemy import select
from sqlalchemy.orm import selectinload

from app.models.media import Media
from app.models.survey_item import SurveyItem
from app.repositories.base import BaseRepository


class SurveyItemRepository(BaseRepository[SurveyItem]):
    model = SurveyItem

    async def get_with_media(self, item_id: uuid.UUID) -> SurveyItem | None:
        return (
            await self.session.execute(
                select(SurveyItem)
                .where(SurveyItem.id == item_id)
                .options(selectinload(SurveyItem.media))
            )
        ).scalar_one_or_none()

    async def list_for_survey(self, survey_id: uuid.UUID) -> Sequence[SurveyItem]:
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

    async def media_in_survey(
        self, survey_id: uuid.UUID, media_ids: Sequence[uuid.UUID]
    ) -> list[Media]:
        """Load the given media, restricted to those actually in ``survey_id``.

        Used to validate item↔media links so an item can never reference media
        from another survey.
        """
        if not media_ids:
            return []
        return list(
            (
                await self.session.execute(
                    select(Media).where(
                        Media.id.in_(media_ids), Media.survey_id == survey_id
                    )
                )
            )
            .scalars()
            .all()
        )
