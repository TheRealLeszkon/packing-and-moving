"""Phase 8 hardening: rate limiting, AI guardrails, metrics, admin."""

from __future__ import annotations

import uuid

from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select

from app.ai.schema import AISurveyAnalysis
from app.core.config import settings
from app.core.metrics import MetricsRegistry
from app.core.ratelimit import SlidingWindowLimiter
from app.db.session import SessionFactory
from app.middleware.rate_limit import RateLimitMiddleware
from app.models.enums import JobStatus
from app.models.media_processing_job import MediaProcessingJob
from tests.conftest import Actor
from tests.helpers import drive_to_review


# --------------------------------------------------------------------------- #
# Rate limiting
# --------------------------------------------------------------------------- #
def test_sliding_window_limiter_admits_then_blocks() -> None:
    now = [0.0]
    limiter = SlidingWindowLimiter(clock=lambda: now[0])
    for _ in range(3):
        assert limiter.check("k", limit=3, window=10).allowed
    blocked = limiter.check("k", limit=3, window=10)
    assert not blocked.allowed and blocked.retry_after >= 1
    now[0] = 11  # window slides past the earliest hits
    assert limiter.check("k", limit=3, window=10).allowed


async def test_rate_limit_middleware_returns_429() -> None:
    mini = FastAPI()
    mini.add_middleware(
        RateLimitMiddleware,
        limiter=SlidingWindowLimiter(),
        limit=2,
        auth_limit=2,
        window=60,
    )

    @mini.get("/ping")
    async def ping() -> dict[str, bool]:
        return {"ok": True}

    transport = ASGITransport(app=mini)
    async with AsyncClient(transport=transport, base_url="http://t") as c:
        assert (await c.get("/ping")).status_code == 200
        assert (await c.get("/ping")).status_code == 200
        blocked = await c.get("/ping")
        assert blocked.status_code == 429
        assert blocked.json()["error"]["code"] == "rate_limited"
        assert "Retry-After" in blocked.headers


# --------------------------------------------------------------------------- #
# AI output guardrails
# --------------------------------------------------------------------------- #
def test_ai_guardrails_sanitize_and_filter() -> None:
    analysis = AISurveyAnalysis.model_validate(
        {
            "items": [
                {"itemName": "  Sofa\x00\x07  ", "remarks": "a\nb\x1f", "estimatedWeightKg": -5},
                {"itemName": "   "},          # blank after sanitising -> dropped
                {"itemName": "x" * 500},      # truncated
            ],
            "needsMoreImages": False,
            "requestedImages": ["front\x00", "   "],
        }
    )
    assert len(analysis.items) == 2  # the blank-named item is dropped
    assert analysis.items[0].item_name == "Sofa"           # control chars + trim
    assert analysis.items[0].remarks == "a\nb"             # control char removed
    assert analysis.items[0].weight_kg is None             # non-positive dropped
    assert len(analysis.items[1].item_name) == 255         # truncated
    assert analysis.requested_images == ["front"]          # cleaned + blank dropped


def test_ai_guardrails_cap_item_count() -> None:
    original = settings.ai_max_items
    settings.ai_max_items = 2
    try:
        analysis = AISurveyAnalysis.model_validate(
            {"items": [{"itemName": f"item {n}"} for n in range(6)]}
        )
        assert len(analysis.items) == 2
    finally:
        settings.ai_max_items = original


# --------------------------------------------------------------------------- #
# Metrics
# --------------------------------------------------------------------------- #
def test_metrics_registry_accumulates() -> None:
    registry = MetricsRegistry()
    registry.request_started()
    registry.request_finished(status_code=200, duration_ms=10.0)
    registry.request_started()
    registry.request_finished(status_code=500, duration_ms=30.0)
    snap = registry.snapshot()
    assert snap["requests_total"] == 2
    assert snap["errors_total"] == 1
    assert snap["requests_in_flight"] == 0
    assert snap["by_status_class"] == {"2xx": 1, "5xx": 1}
    assert snap["avg_latency_ms"] == 20.0


# --------------------------------------------------------------------------- #
# Admin
# --------------------------------------------------------------------------- #
async def test_admin_listing_and_authorization(
    api: AsyncClient, admin: Actor, customer: Actor, surveyor: Actor
) -> None:
    await drive_to_review(api, customer, surveyor)  # generate surveys/users/ai-runs
    for path in ("/admin/surveys", "/admin/users", "/admin/ai-runs", "/admin/jobs"):
        resp = await api.get(path, headers=admin.headers)
        assert resp.status_code == 200
        assert resp.json()["data"]["total"] >= 0

    metrics_resp = await api.get("/admin/metrics", headers=admin.headers)
    assert metrics_resp.status_code == 200
    assert "requests_total" in metrics_resp.json()["data"]

    # a non-admin is forbidden
    assert (await api.get("/admin/surveys", headers=customer.headers)).status_code == 403


async def test_admin_requeue_job(
    api: AsyncClient, admin: Actor, customer: Actor, surveyor: Actor
) -> None:
    _, img_id = await drive_to_review(api, customer, surveyor)
    async with SessionFactory() as session:
        job = (
            await session.execute(
                select(MediaProcessingJob).where(
                    MediaProcessingJob.media_id == uuid.UUID(img_id)
                )
            )
        ).scalar_one()
        job.status = JobStatus.DEAD_LETTER
        job.last_error = "boom"
        job_id = job.id
        await session.commit()

    resp = await api.post(f"/admin/jobs/{job_id}/requeue", headers=admin.headers)
    assert resp.status_code == 200
    body = resp.json()["data"]
    assert body["status"] == "queued" and body["attempts"] == 0
