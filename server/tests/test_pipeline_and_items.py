"""End-to-end: media processing + AI analysis, then item CRUD/merge/split/summary."""

from __future__ import annotations

import uuid

from httpx import AsyncClient

from tests.conftest import Actor
from tests.helpers import drive_to_review


async def test_ai_generates_items_and_marks_ready(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, img_id = await drive_to_review(api, customer, surveyor)

    status = (await api.get(f"/surveys/{sid}/status", headers=surveyor.headers)).json()["data"]
    assert status["status"] == "ready_for_review"

    items = (await api.get(f"/surveys/{sid}/items", headers=surveyor.headers)).json()["data"]
    assert items["count"] >= 1
    item = items["items"][0]
    assert item["source"] == "ai"
    assert img_id in item["media_ids"]  # linked to its evidencing media


async def test_item_crud(api: AsyncClient, customer: Actor, surveyor: Actor) -> None:
    sid, img_id = await drive_to_review(api, customer, surveyor)

    # create
    created = await api.post(
        f"/surveys/{sid}/items",
        headers=surveyor.headers,
        json={
            "item_name": "Bookshelf", "quantity": 2, "height_cm": 180, "width_cm": 80,
            "depth_cm": 30, "estimated_value": 120, "media_ids": [img_id],
        },
    )
    assert created.status_code == 201
    item = created.json()["data"]
    assert item["source"] == "manual" and img_id in item["media_ids"]

    # update (partial)
    patched = await api.patch(
        f"/survey-items/{item['id']}", headers=surveyor.headers, json={"quantity": 5}
    )
    assert patched.status_code == 200 and patched.json()["data"]["quantity"] == 5

    # totals reflected on the survey
    survey = (await api.get(f"/surveys/{sid}", headers=surveyor.headers)).json()["data"]
    assert survey["total_value_estimate"] is not None

    # delete
    deleted = await api.delete(f"/survey-items/{item['id']}", headers=surveyor.headers)
    assert deleted.status_code == 200


async def test_merge_and_split(api: AsyncClient, customer: Actor, surveyor: Actor) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)
    mk = lambda name: api.post(  # noqa: E731
        f"/surveys/{sid}/items", headers=surveyor.headers,
        json={"item_name": name, "quantity": 1, "estimated_value": 50},
    )
    a = (await mk("Chair A")).json()["data"]
    b = (await mk("Chair B")).json()["data"]

    merged = await api.post(
        "/survey-items/merge", headers=surveyor.headers,
        json={"item_ids": [a["id"], b["id"]], "quantity": 2, "item_name": "Chairs"},
    )
    assert merged.status_code == 200
    survivor = merged.json()["data"]
    assert survivor["id"] == a["id"] and survivor["quantity"] == 2

    split = await api.post(
        f"/survey-items/{survivor['id']}/split", headers=surveyor.headers,
        json={"parts": [{"item_name": "Chair 1"}, {"item_name": "Chair 2"}]},
    )
    assert split.status_code == 201 and split.json()["data"]["count"] == 2


async def test_summary(api: AsyncClient, customer: Actor, surveyor: Actor) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)
    await api.post(
        f"/surveys/{sid}/items", headers=surveyor.headers,
        json={"item_name": "Fridge", "category": "Appliance", "room_location": "Kitchen",
              "quantity": 1, "height_cm": 170, "width_cm": 70, "depth_cm": 70,
              "estimated_value": 500, "fragile": True},
    )
    summary = (await api.get(f"/surveys/{sid}/summary", headers=surveyor.headers)).json()["data"]
    assert summary["distinct_items"] >= 2
    assert summary["total_quantity"] >= 2
    assert float(summary["total_volume_m3"]) > 0
    assert float(summary["total_value"]) >= 500
    assert summary["fragile_items"] >= 1
    assert len(summary["by_category"]) >= 1 and len(summary["by_room"]) >= 1


async def test_item_edit_authorization(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)
    listing = (await api.get(f"/surveys/{sid}/items", headers=surveyor.headers)).json()
    item_id = listing["data"]["items"][0]["id"]

    # customer cannot edit inventory
    assert (await api.patch(f"/survey-items/{item_id}", headers=customer.headers,
                            json={"quantity": 9})).status_code == 403
    # invalid media reference rejected
    assert (await api.patch(f"/survey-items/{item_id}", headers=surveyor.headers,
                            json={"media_ids": [str(uuid.uuid4())]})).status_code == 422
