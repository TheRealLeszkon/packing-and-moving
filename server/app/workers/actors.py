"""Dramatiq actors.

Thin entry points that adapt a broker message (a media id string) to the sync
pipeline functions. Start the worker with:

    uv run dramatiq app.workers.actors
"""

from __future__ import annotations

import uuid

import dramatiq

from app.core.config import settings
from app.models.enums import ReanalysisMode
from app.workers.broker import broker  # noqa: F401 - configures the broker on import
from app.workers.pipeline import (
    run_ai_analysis,
    run_finalize,
    run_image_pipeline,
    run_reanalysis,
    run_video_pipeline,
)

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


# AI analysis handles its own transient retries inside the provider and advances
# the survey even when analysis ultimately fails, so it never loops on a bad
# response. A small retry budget covers infrastructure crashes (the actor is
# idempotent: it re-claims the pending run and no-ops once the survey has moved on).
@dramatiq.actor(queue_name="ai", max_retries=2, min_backoff=2000, max_backoff=60000)
def analyze_survey(survey_id: str) -> None:
    run_ai_analysis(uuid.UUID(survey_id))


# Kicks the finalize check after a survey is completed, in case all media
# finished before completion (which would otherwise leave analysis unstarted).
# Idempotent and cheap; shares the "ai" queue.
@dramatiq.actor(queue_name="ai", max_retries=2, min_backoff=2000, max_backoff=60000)
def finalize_survey(survey_id: str) -> None:
    run_finalize(uuid.UUID(survey_id))


# Surveyor-triggered re-analysis of a survey under review (see /reanalyze). Shares
# the AI queue and idempotency guarantees of ``analyze_survey``.
@dramatiq.actor(queue_name="ai", max_retries=2, min_backoff=2000, max_backoff=60000)
def reanalyze_survey(survey_id: str, mode: str) -> None:
    run_reanalysis(uuid.UUID(survey_id), ReanalysisMode(mode))
