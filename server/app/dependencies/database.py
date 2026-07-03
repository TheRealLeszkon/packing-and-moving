"""Shared database dependency."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_session

# Request-scoped session (commits on success / rolls back on error).
SessionDep = Annotated[AsyncSession, Depends(get_session)]
