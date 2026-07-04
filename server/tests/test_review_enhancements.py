"""Review-phase enhancements: uploads during review, re-analysis, editable
confidence, and the processing-stage progress field."""

from __future__ import annotations

import asyncio
import uuid

from httpx import AsyncClient

from app.models.enums import ReanalysisMode
from tests.conftest import Actor
from tests.helpers import create_survey, drive_to_review, jpeg_bytes, upload_image


def _by_source(items: list[dict]) -> tuple[int, int]:
    ai = sum(1 for i in items if i["source"] == "ai")
    manual = sum(1 for i in items if i["source"] == "manual")
    return ai, manual


async def _items(api: AsyncClient, actor: Actor, sid: str) -> list[dict]:
    return (await api.get(f"/surveys/{sid}/items", headers=actor.headers)).json()["data"]["items"]


# --------------------------------------------------------------------------- #
# Feature 1: uploads allowed during review
# --------------------------------------------------------------------------- #
async def test_upload_allowed_during_review(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)  # READY_FOR_REVIEW

    resp = await api.post(
        f"/surveys/{sid}/images",
        headers=surveyor.headers,
        files=[("files", ("extra.jpg", jpeg_bytes(), "image/jpeg"))],
    )
    assert resp.status_code == 201


async def test_upload_rejected_before_start(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    # ASSIGNED (accepted, not started) is not an uploadable state.
    sid = await create_survey(api, customer)
    await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    resp = await api.post(
        f"/surveys/{sid}/images",
        headers=surveyor.headers,
        files=[("files", ("i.jpg", b"x", "image/jpeg"))],
    )
    assert resp.status_code == 409


# --------------------------------------------------------------------------- #
# Feature 2: re-analysis
# --------------------------------------------------------------------------- #
async def test_reanalyze_accepts_and_transitions(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)

    resp = await api.post(f"/surveys/{sid}/reanalyze", headers=surveyor.headers)
    assert resp.status_code == 202
    data = resp.json()["data"]
    assert data["status"] == "processing"
    assert len(data["job_ids"]) == 1


async def test_reanalyze_all_replaces_ai_keeps_manual(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    from app.workers.pipeline import run_reanalysis

    sid, _ = await drive_to_review(api, customer, surveyor)  # 1 AI item
    # Add a manual item the re-analysis must preserve.
    await api.post(
        f"/surveys/{sid}/items", headers=surveyor.headers,
        json={"item_name": "Manual box", "quantity": 1, "estimated_value": 10},
    )
    ai, manual = _by_source(await _items(api, surveyor, sid))
    assert (ai, manual) == (1, 1)

    resp = await api.post(
        f"/surveys/{sid}/reanalyze", headers=surveyor.headers, json={"mode": "all"}
    )
    assert resp.status_code == 202
    await asyncio.to_thread(run_reanalysis, uuid.UUID(sid), ReanalysisMode.ALL)

    status = (await api.get(f"/surveys/{sid}/status", headers=surveyor.headers)).json()["data"]
    assert status["status"] == "ready_for_review"
    ai, manual = _by_source(await _items(api, surveyor, sid))
    assert ai == 1  # regenerated, not duplicated
    assert manual == 1  # preserved


async def test_reanalyze_new_only_appends_from_new_media(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    from app.workers.pipeline import run_image_pipeline, run_reanalysis

    sid, _ = await drive_to_review(api, customer, surveyor)  # 1 AI item
    before, _ = _by_source(await _items(api, surveyor, sid))

    # Upload + process a new photo during review (Feature 1), then re-analyse it.
    new_img = await upload_image(api, surveyor, sid)
    await asyncio.to_thread(run_image_pipeline, uuid.UUID(new_img))

    resp = await api.post(
        f"/surveys/{sid}/reanalyze", headers=surveyor.headers, json={"mode": "new_only"}
    )
    assert resp.status_code == 202
    await asyncio.to_thread(run_reanalysis, uuid.UUID(sid), ReanalysisMode.NEW_ONLY)

    after, _ = _by_source(await _items(api, surveyor, sid))
    assert after == before + 1  # appended, existing items untouched


async def test_reanalyze_requires_assigned_surveyor(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)
    # The owning customer may view but not re-analyse.
    resp = await api.post(f"/surveys/{sid}/reanalyze", headers=customer.headers)
    assert resp.status_code == 403


async def test_reanalyze_rejected_from_wrong_state(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid = await create_survey(api, customer)
    await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    await api.post(f"/surveys/{sid}/start", headers=surveyor.headers)  # IN_PROGRESS
    resp = await api.post(f"/surveys/{sid}/reanalyze", headers=surveyor.headers)
    assert resp.status_code == 409


# --------------------------------------------------------------------------- #
# Feature 3: editable confidence score
# --------------------------------------------------------------------------- #
async def test_confidence_score_editable(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    sid, _ = await drive_to_review(api, customer, surveyor)
    item_id = (await _items(api, surveyor, sid))[0]["id"]

    ok = await api.patch(
        f"/survey-items/{item_id}", headers=surveyor.headers, json={"confidence_score": 0.5}
    )
    assert ok.status_code == 200
    assert float(ok.json()["data"]["confidence_score"]) == 0.5

    # Out of range is rejected.
    bad = await api.patch(
        f"/survey-items/{item_id}", headers=surveyor.headers, json={"confidence_score": 1.5}
    )
    assert bad.status_code == 422


# --------------------------------------------------------------------------- #
# Feature 4: processing stage
# --------------------------------------------------------------------------- #
async def test_processing_stage_progression(
    api: AsyncClient, customer: Actor, surveyor: Actor
) -> None:
    from app.workers.pipeline import run_ai_analysis, run_image_pipeline

    sid = await create_survey(api, customer)
    await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    await api.post(f"/surveys/{sid}/start", headers=surveyor.headers)
    img_id = await upload_image(api, surveyor, sid)
    await api.post(f"/surveys/{sid}/complete", headers=surveyor.headers)  # -> PROCESSING

    async def stage() -> str | None:
        data = (await api.get(f"/surveys/{sid}/status", headers=surveyor.headers)).json()["data"]
        return data["processing_stage"]

    # Media not yet processed.
    assert await stage() == "media_processing"
    # Media done -> a PENDING AI run exists.
    await asyncio.to_thread(run_image_pipeline, uuid.UUID(img_id))
    assert await stage() == "ai_analysis"
    # Analysis done -> not processing anymore.
    await asyncio.to_thread(run_ai_analysis, uuid.UUID(sid))
    assert await stage() is None
