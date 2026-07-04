"""Reusable test helpers: media bytes and survey-lifecycle driving."""

from __future__ import annotations

import asyncio
import io
import uuid

import numpy as np
from httpx import AsyncClient
from PIL import Image

from tests.conftest import Actor


def jpeg_bytes(width: int = 1200, height: int = 900) -> bytes:
    arr = (np.random.rand(height, width, 3) * 255).astype("uint8")
    buf = io.BytesIO()
    Image.fromarray(arr).save(buf, "JPEG")
    return buf.getvalue()


async def create_survey(api: AsyncClient, customer: Actor) -> str:
    resp = await api.post(
        "/surveys",
        headers=customer.headers,
        json={"name": "Test move", "origin_address": "A st", "destination_address": "B st"},
    )
    return resp.json()["data"]["id"]


async def upload_image(api: AsyncClient, surveyor: Actor, survey_id: str) -> str:
    resp = await api.post(
        f"/surveys/{survey_id}/images",
        headers=surveyor.headers,
        files=[("files", ("i.jpg", jpeg_bytes(), "image/jpeg"))],
    )
    return resp.json()["data"]["items"][0]["id"]


async def drive_to_review(api: AsyncClient, customer: Actor, surveyor: Actor) -> tuple[str, str]:
    """Create -> accept -> start -> upload -> complete -> process -> analyse.

    Returns ``(survey_id, image_media_id)``; the survey ends in READY_FOR_REVIEW
    with one stub-detected item linked to the uploaded image.
    """
    # Imported here so importing this module doesn't require the worker extras.
    from app.workers.pipeline import run_ai_analysis, run_image_pipeline

    sid = await create_survey(api, customer)
    await api.post(f"/survey-requests/{sid}/accept", headers=surveyor.headers)
    await api.post(f"/surveys/{sid}/start", headers=surveyor.headers)
    img_id = await upload_image(api, surveyor, sid)
    await api.post(f"/surveys/{sid}/complete", headers=surveyor.headers)
    await asyncio.to_thread(run_image_pipeline, uuid.UUID(img_id))
    await asyncio.to_thread(run_ai_analysis, uuid.UUID(sid))
    return sid, img_id
