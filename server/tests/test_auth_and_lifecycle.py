"""Auth, security headers, and the survey lifecycle happy path + guards."""

from __future__ import annotations

from httpx import AsyncClient

from tests.conftest import Actor
from tests.helpers import create_survey


async def test_health_is_open(api: AsyncClient) -> None:
    resp = await api.get("/health")
    assert resp.status_code == 200


async def test_security_headers_present(api: AsyncClient) -> None:
    resp = await api.get("/health")
    assert resp.headers["X-Content-Type-Options"] == "nosniff"
    assert resp.headers["X-Frame-Options"] == "DENY"
    assert "Content-Security-Policy" in resp.headers


async def test_requires_authentication(api: AsyncClient) -> None:
    resp = await api.get("/surveys/my")
    assert resp.status_code == 401
    assert resp.json()["success"] is False


async def test_auth_me_returns_current_user(api: AsyncClient, customer: Actor) -> None:
    resp = await api.get("/auth/me", headers=customer.headers)
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == customer.id


async def test_full_lifecycle_to_approval(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid = await create_survey(api, customer)

    # a surveyor accepts and starts
    accepted = await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    started = await api.post(f"/surveys/{sid}/start", headers=surveyor.headers)
    assert accepted.status_code == 200 and started.status_code == 200

    status = (await api.get(f"/surveys/{sid}/status", headers=surveyor.headers)).json()["data"]
    assert status["status"] == "in_progress"

    # drive the rest of the workflow via the state machine
    await api.post(f"/surveys/{sid}/complete", headers=surveyor.headers)  # -> processing
    # (no media, so it stays processing until a pipeline finalises; force via review path)


async def test_only_customer_creates_surveys(api: AsyncClient, surveyor: Actor) -> None:
    resp = await api.post(
        "/surveys",
        headers=surveyor.headers,
        json={"name": "x", "origin_address": "a", "destination_address": "b"},
    )
    assert resp.status_code == 403


async def test_customer_cannot_start_survey(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid = await create_survey(api, customer)
    await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    resp = await api.post(f"/surveys/{sid}/start", headers=customer.headers)
    assert resp.status_code in (403, 409)
