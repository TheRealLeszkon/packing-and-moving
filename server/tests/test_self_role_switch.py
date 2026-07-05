"""Demo-only self-service role switch (POST /users/me/role).

Covers the happy path, privilege-escalation rejection, the no-refresh guarantee
(the same token sees the new role on the next call), and the settings gate.
"""

from __future__ import annotations

from httpx import AsyncClient

from app.core.config import settings
from tests.conftest import Actor


async def test_switch_customer_to_surveyor_returns_users_me_shape(
    api: AsyncClient, customer: Actor
) -> None:
    resp = await api.post(
        "/users/me/role", headers=customer.headers, json={"role": "surveyor"}
    )
    assert resp.status_code == 200
    data = resp.json()["data"]
    # Same envelope + shape as GET /users/me.
    me = (await api.get("/users/me", headers=customer.headers)).json()["data"]
    assert data.keys() == me.keys()
    assert data["role"] == "surveyor"
    assert data["id"] == customer.id


async def test_switch_is_visible_to_same_token_without_refresh(
    api: AsyncClient, customer: Actor
) -> None:
    # The role claim in the existing token is stale, but get_current_user re-reads
    # the role from the DB, so the same token immediately reflects the new role.
    await api.post("/users/me/role", headers=customer.headers, json={"role": "surveyor"})
    me = (await api.get("/users/me", headers=customer.headers)).json()["data"]
    assert me["role"] == "surveyor"

    # And back again.
    await api.post("/users/me/role", headers=customer.headers, json={"role": "customer"})
    me = (await api.get("/users/me", headers=customer.headers)).json()["data"]
    assert me["role"] == "customer"


async def test_admin_is_rejected(api: AsyncClient, customer: Actor) -> None:
    resp = await api.post("/users/me/role", headers=customer.headers, json={"role": "admin"})
    assert resp.status_code == 422
    # Role is unchanged.
    me = (await api.get("/users/me", headers=customer.headers)).json()["data"]
    assert me["role"] == "customer"


async def test_unknown_role_is_rejected(api: AsyncClient, customer: Actor) -> None:
    resp = await api.post("/users/me/role", headers=customer.headers, json={"role": "wizard"})
    assert resp.status_code == 422


async def test_requires_authentication(api: AsyncClient) -> None:
    resp = await api.post("/users/me/role", json={"role": "surveyor"})
    assert resp.status_code == 401


async def test_disabled_flag_hides_endpoint(
    api: AsyncClient, customer: Actor, monkeypatch
) -> None:
    monkeypatch.setattr(settings, "allow_self_role_change", False)
    resp = await api.post("/users/me/role", headers=customer.headers, json={"role": "surveyor"})
    assert resp.status_code == 404
    # Role unchanged while disabled.
    me = (await api.get("/users/me", headers=customer.headers)).json()["data"]
    assert me["role"] == "customer"
