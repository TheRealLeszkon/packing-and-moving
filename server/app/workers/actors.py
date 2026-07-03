"""Dramatiq actors.

Thin entry points that adapt a broker message (a media id string) to the sync
pipeline functions. Start the worker with:

    uv run dramatiq app.workers.actors
"""

from __future__ import annotations

import uuid

import dramatiq

from app.core.config import settings
from app.workers.broker import broker  # noqa: F401 - configures the broker on import
from app.workers.pipeline import run_image_pipeline, run_video_pipeline

# Retry with exponential backoff; the ledger dead-letters once attempts run out.
_ACTOR_OPTS = {
    "queue_name": "media",
    "max_retries": settings.job_max_attempts,
    "min_backoff": 1000,      # 1s
    "max_backoff": 900000,    # 15m
}


@dramatiq.actor(**_ACTOR_OPTS)
def process_image(media_id: str) -> None:
    run_image_pipeline(uuid.UUID(media_id))


@dramatiq.actor(**_ACTOR_OPTS)
def process_video(media_id: str) -> None:
    run_video_pipeline(uuid.UUID(media_id))
