"""Generic async repository.

Encapsulates the common CRUD data-access patterns so services never touch the
SQLAlchemy session directly for routine operations. Concrete repositories
subclass this and add query methods specific to their aggregate.

Design notes:
- The repository operates on a caller-provided ``AsyncSession`` (the request's
  unit of work). It **adds/flushes but never commits** — the transaction
  boundary is owned by the request (``get_session``) or the service, so multiple
  repository calls compose into one atomic transaction.
- ``flush`` is used to surface DB-generated values (ids, defaults) and integrity
  errors early, without ending the transaction.
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.base import Base


class BaseRepository[ModelT: Base]:
    """CRUD building blocks for a single model type."""

    model: type[ModelT]

    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get(self, entity_id: uuid.UUID) -> ModelT | None:
        return await self.session.get(self.model, entity_id)

    async def get_by(self, **filters: Any) -> ModelT | None:
        stmt = select(self.model).filter_by(**filters).limit(1)
        return (await self.session.execute(stmt)).scalar_one_or_none()

    async def list(
        self,
        *,
        limit: int = 50,
        offset: int = 0,
        order_by: Any | None = None,
        **filters: Any,
    ) -> Sequence[ModelT]:
        stmt = select(self.model).filter_by(**filters).limit(limit).offset(offset)
        if order_by is not None:
            stmt = stmt.order_by(order_by)
        return (await self.session.execute(stmt)).scalars().all()

    async def count(self, **filters: Any) -> int:
        stmt = select(func.count()).select_from(self.model).filter_by(**filters)
        return (await self.session.execute(stmt)).scalar_one()

    def add(self, entity: ModelT) -> ModelT:
        """Stage a new entity for insertion (no I/O until flush/commit)."""
        self.session.add(entity)
        return entity

    async def create(self, **values: Any) -> ModelT:
        entity = self.model(**values)
        self.session.add(entity)
        await self.session.flush()  # populate PK/defaults, raise integrity errors now
        return entity

    async def delete(self, entity: ModelT) -> None:
        await self.session.delete(entity)
        await self.session.flush()
