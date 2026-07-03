"""Synchronous database session for background workers.

The API uses async SQLAlchemy; Dramatiq actors are synchronous, so they get a
separate sync engine (psycopg2). Keeping the two engines distinct avoids running
an async event loop inside worker threads just for the database.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import settings

sync_engine = create_engine(
    settings.sync_sqlalchemy_url,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10,
)

WorkerSessionFactory: sessionmaker[Session] = sessionmaker(
    bind=sync_engine,
    expire_on_commit=False,
    autoflush=False,
)


@contextmanager
def worker_session() -> Iterator[Session]:
    """A transactional worker session: commit on success, rollback on error."""
    session = WorkerSessionFactory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
