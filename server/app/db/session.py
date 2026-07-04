"""Async database engine and session management.

A single ``AsyncEngine`` and ``async_sessionmaker`` are created per process. The
``get_session`` dependency yields a session scoped to one request and commits on
success / rolls back on error, giving every request a clear transaction boundary
without repeating boilerplate in services.
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Callable

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import NullPool

from app.core.config import settings

logger = logging.getLogger(__name__)

# Key under ``session.info`` holding callables to run *after* a successful commit
# (e.g. dispatching a background job only once its rows are durably persisted).
AFTER_COMMIT_HOOKS = "after_commit_hooks"


def register_after_commit(session: AsyncSession, hook: Callable[[], None]) -> None:
    session.info.setdefault(AFTER_COMMIT_HOOKS, []).append(hook)

def _create_engine() -> AsyncEngine:
    if settings.db_use_nullpool:
        # No pooling: every session opens a fresh connection. Used under pytest
        # so connections never leak across per-test event loops.
        return create_async_engine(
            settings.sqlalchemy_url, echo=settings.db_echo, poolclass=NullPool
        )
    return create_async_engine(
        settings.sqlalchemy_url,
        echo=settings.db_echo,
        pool_size=settings.db_pool_size,
        max_overflow=settings.db_max_overflow,
        pool_pre_ping=True,  # transparently recycle stale connections
    )


engine: AsyncEngine = _create_engine()

SessionFactory: async_sessionmaker[AsyncSession] = async_sessionmaker(
    bind=engine,
    expire_on_commit=False,
    autoflush=False,
)


async def get_session() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency yielding a request-scoped session.

    Commits when the request handler returns normally; rolls back if it raises.
    The session is always closed. Services should treat the yielded session as
    the unit-of-work for the whole request.
    """
    async with SessionFactory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        else:
            _run_after_commit_hooks(session)


def _run_after_commit_hooks(session: AsyncSession) -> None:
    for hook in session.info.get(AFTER_COMMIT_HOOKS, []):
        try:
            hook()
        except Exception:  # noqa: BLE001 - a dispatch failure must not fail the request
            logger.exception("after_commit_hook_failed")


async def dispose_engine() -> None:
    """Dispose the connection pool on application shutdown."""
    await engine.dispose()
