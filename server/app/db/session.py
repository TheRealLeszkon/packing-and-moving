"""Async database engine and session management.

A single ``AsyncEngine`` and ``async_sessionmaker`` are created per process. The
``get_session`` dependency yields a session scoped to one request and commits on
success / rolls back on error, giving every request a clear transaction boundary
without repeating boilerplate in services.
"""

from __future__ import annotations

from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import settings

engine: AsyncEngine = create_async_engine(
    settings.sqlalchemy_url,
    echo=settings.db_echo,
    pool_size=settings.db_pool_size,
    max_overflow=settings.db_max_overflow,
    pool_pre_ping=True,  # transparently recycle stale connections
)

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


async def dispose_engine() -> None:
    """Dispose the connection pool on application shutdown."""
    await engine.dispose()
