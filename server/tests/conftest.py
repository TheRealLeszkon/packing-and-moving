"""Shared test configuration and fixtures.

Integration tests run the real ASGI app against the configured Postgres database
using unique per-test data (fresh emails/surveys), so they are independent and
need no teardown. External effects are stubbed via environment: in-memory storage,
the deterministic stub AI provider, no broker dispatch, and rate limiting off (a
dedicated test exercises the limiter directly).

The environment must be set *before* app settings are imported, so this happens at
module import time — conftest is loaded before any test module.
"""

from __future__ import annotations

import os

os.environ.setdefault("STORAGE_BACKEND", "memory")
os.environ.setdefault("AI_PROVIDER", "stub")
# Tests exercise the flow with locally-minted dev ID tokens, so force the dev
# verifier regardless of the developer's .env (which may enable Google auth).
os.environ.setdefault("AUTH_MODE", "dev")
os.environ.setdefault("PROCESSING_DISPATCH_ENABLED", "false")
os.environ.setdefault("RATE_LIMIT_ENABLED", "false")
# Each test runs on its own event loop; a pooled asyncpg connection can't cross
# loops, so use a non-pooling engine for the test process.
os.environ.setdefault("DB_USE_NULLPOOL", "true")

import uuid  # noqa: E402
from collections.abc import AsyncIterator  # noqa: E402

import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy import update  # noqa: E402

from app.auth.verifiers import mint_dev_id_token  # noqa: E402
from app.core.config import settings  # noqa: E402
from app.db.session import SessionFactory  # noqa: E402
from app.main import app  # noqa: E402
from app.models.enums import UserRole  # noqa: E402
from app.models.user import User  # noqa: E402


class Actor(dict):
    """A signed-in user: ``id``, ``token`` and ready-to-use ``headers``."""

    @property
    def headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self['token']}"}

    @property
    def id(self) -> str:
        return self["id"]


@pytest_asyncio.fixture
async def api() -> AsyncIterator[AsyncClient]:
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


async def sign_in(api: AsyncClient, *, role: UserRole | None = None) -> Actor:
    email = f"{(role or 'user')}_{uuid.uuid4().hex[:10]}@example.com"
    token = mint_dev_id_token(
        subject="g-" + uuid.uuid4().hex, email=email, name=email, secret=settings.jwt_secret
    )
    data = (await api.post("/auth/google", json={"id_token": token})).json()["data"]
    actor = Actor(id=data["user"]["id"], token=data["access_token"])
    if role is not None and role is not UserRole.CUSTOMER:
        async with SessionFactory() as session:
            await session.execute(
                update(User).where(User.id == uuid.UUID(actor.id)).values(role=role)
            )
            await session.commit()
    return actor


@pytest_asyncio.fixture
async def customer(api: AsyncClient) -> Actor:
    return await sign_in(api, role=UserRole.CUSTOMER)


@pytest_asyncio.fixture
async def surveyor(api: AsyncClient) -> Actor:
    return await sign_in(api, role=UserRole.SURVEYOR)


@pytest_asyncio.fixture
async def admin(api: AsyncClient) -> Actor:
    return await sign_in(api, role=UserRole.ADMIN)
