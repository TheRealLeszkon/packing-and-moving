"""Job dispatch.

Decouples "record that processing is due" (a ``media_processing_jobs`` row) from
"tell the broker to run it". The API registers a dispatch as a post-commit hook,
so a message is only sent once the media/job rows are durably committed — no
worker can pick up a job before its data exists.
"""

from __future__ import annotations

import logging
import uuid
from functools import lru_cache
from typing import Protocol

from app.core.config import settings
from app.models.enums import JobType

logger = logging.getLogger(__name__)


class ProcessingDispatcher(Protocol):
    def dispatch(self, media_id: uuid.UUID, job_type: JobType) -> None: ...

    def dispatch_ai(self, survey_id: uuid.UUID) -> None: ...


class DramatiqDispatcher:
    """Sends the matching actor message to the broker (Redis)."""

    def dispatch(self, media_id: uuid.UUID, job_type: JobType) -> None:
        # Imported lazily so the broker/Redis is only touched when dispatching.
        from app.workers.actors import process_image, process_video

        actor = process_video if job_type is JobType.VIDEO_FRAME_EXTRACTION else process_image
        actor.send(str(media_id))

    def dispatch_ai(self, survey_id: uuid.UUID) -> None:
        from app.workers.actors import analyze_survey

        analyze_survey.send(str(survey_id))


class NullDispatcher:
    """No-op dispatcher for environments without a worker (local/tests)."""

    def dispatch(self, media_id: uuid.UUID, job_type: JobType) -> None:
        logger.info("dispatch_skipped", extra={"media_id": str(media_id), "job": job_type})

    def dispatch_ai(self, survey_id: uuid.UUID) -> None:
        logger.info("ai_dispatch_skipped", extra={"survey_id": str(survey_id)})


@lru_cache
def get_dispatcher() -> ProcessingDispatcher:
    if settings.processing_dispatch_enabled:
        return DramatiqDispatcher()
    return NullDispatcher()
