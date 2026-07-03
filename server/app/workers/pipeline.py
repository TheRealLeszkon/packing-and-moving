"""Media processing pipeline (worker-side orchestration).

Pure sync functions the actors call. Each is:

- **idempotent** — re-running a completed job is a no-op; a retried video job
  first clears any frames produced by the previous attempt;
- **crash-safe** — job bookkeeping (attempts, status, errors) is written in its
  own transaction so it survives a failure that rolls back the work;
- **self-finalizing** — when a survey's last media finishes, the survey advances
  PROCESSING -> READY_FOR_REVIEW (a SYSTEM transition). Phase 6 replaces this hook
  with "enqueue AI analysis" before the survey becomes reviewable.

Retry/backoff is delegated to Dramatiq; the job ledger records attempts and
dead-letters once ``job_max_attempts`` is exhausted.
"""

from __future__ import annotations

import io
import logging
import os
import tempfile
import uuid
from datetime import UTC, datetime

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.sync_session import worker_session
from app.models.enums import (
    JobStatus,
    JobType,
    MediaType,
    ProcessingStatus,
    SurveyStatus,
)
from app.models.media import Media
from app.models.media_processing_job import MediaProcessingJob
from app.models.survey import Survey
from app.models.survey_history import SurveyHistory
from app.processing.blur import is_blurry
from app.processing.dedup import DuplicateFilter
from app.processing.frames import extract_frames
from app.processing.image_ops import resize_to_bounds
from app.storage.factory import get_storage
from app.storage.keys import frame_key, processed_key
from app.workers.runtime import run_async

logger = logging.getLogger(__name__)


def _now() -> datetime:
    return datetime.now(UTC)


def _get_job(db: Session, media_id: uuid.UUID, job_type: JobType) -> MediaProcessingJob | None:
    return db.scalar(
        select(MediaProcessingJob).where(
            MediaProcessingJob.media_id == media_id,
            MediaProcessingJob.job_type == job_type,
        )
    )


# --------------------------------------------------------------------------- #
# Image pipeline
# --------------------------------------------------------------------------- #
def run_image_pipeline(media_id: uuid.UUID) -> None:
    storage = get_storage()

    with worker_session() as db:
        media = db.get(Media, media_id)
        if media is None or media.original_url is None:
            logger.warning("image_job_media_missing", extra={"media_id": str(media_id)})
            return
        job = _get_job(db, media_id, JobType.IMAGE_RESIZE)
        if job is None or job.status is JobStatus.SUCCEEDED:
            return  # nothing to do / already done (idempotent)
        if media.processing_status is ProcessingStatus.COMPLETED:
            job.status = JobStatus.SUCCEEDED
            return
        job.status = JobStatus.RUNNING
        job.attempts += 1
        job.started_at = _now()
        attempt = job.attempts
        media.processing_status = ProcessingStatus.PROCESSING
        original_key = storage.key_from_uri(media.original_url)
        survey_id = media.survey_id

    try:
        data = run_async(storage.download(original_key))
        resized = resize_to_bounds(
            data, max_width=settings.image_max_width, max_height=settings.image_max_height
        )
        key = processed_key(survey_id, "jpg")
        processed_uri = run_async(
            storage.upload(
                key, io.BytesIO(resized.data), content_type="image/jpeg", size=len(resized.data)
            )
        )
    except Exception as exc:  # noqa: BLE001 - recorded + optionally retried
        _record_failure(media_id, JobType.IMAGE_RESIZE, exc, attempt)
        if attempt < settings.job_max_attempts:
            raise
        return

    with worker_session() as db:
        media = db.get(Media, media_id)
        if media is not None:
            media.processed_url = processed_uri
            media.width = resized.width
            media.height = resized.height
            media.processing_status = ProcessingStatus.COMPLETED
        job = _get_job(db, media_id, JobType.IMAGE_RESIZE)
        if job is not None:
            job.status = JobStatus.SUCCEEDED
            job.finished_at = _now()
        _maybe_finalize_survey(db, survey_id)


# --------------------------------------------------------------------------- #
# Video pipeline
# --------------------------------------------------------------------------- #
def run_video_pipeline(media_id: uuid.UUID) -> None:
    storage = get_storage()

    with worker_session() as db:
        media = db.get(Media, media_id)
        if media is None or media.original_url is None:
            logger.warning("video_job_media_missing", extra={"media_id": str(media_id)})
            return
        job = _get_job(db, media_id, JobType.VIDEO_FRAME_EXTRACTION)
        if job is None or job.status is JobStatus.SUCCEEDED:
            return
        if media.processing_status is ProcessingStatus.COMPLETED:
            job.status = JobStatus.SUCCEEDED
            return
        job.status = JobStatus.RUNNING
        job.attempts += 1
        job.started_at = _now()
        attempt = job.attempts
        media.processing_status = ProcessingStatus.PROCESSING
        original_key = storage.key_from_uri(media.original_url)
        survey_id = media.survey_id
        # Clean up frames from any prior failed attempt (idempotency).
        prior_frames = list(
            db.scalars(select(Media).where(Media.parent_video_id == media_id))
        )

    for frame in prior_frames:
        if frame.processed_url:
            _safe_delete(storage, frame.processed_url)
    if prior_frames:
        with worker_session() as db:
            db.execute(delete(Media).where(Media.parent_video_id == media_id))

    try:
        kept = _extract_and_filter(storage, original_key, survey_id, media_id)
    except Exception as exc:  # noqa: BLE001
        _record_failure(media_id, JobType.VIDEO_FRAME_EXTRACTION, exc, attempt)
        if attempt < settings.job_max_attempts:
            raise
        return

    with worker_session() as db:
        for kept_frame in kept:
            db.add(
                Media(
                    survey_id=survey_id,
                    uploaded_by=None,  # system-generated derivative
                    media_type=MediaType.EXTRACTED_FRAME,
                    parent_video_id=media_id,
                    processed_url=kept_frame.uri,
                    width=kept_frame.width,
                    height=kept_frame.height,
                    frame_number=kept_frame.frame_number,
                    blur_score=kept_frame.blur_score,
                    processing_status=ProcessingStatus.COMPLETED,
                )
            )
        media = db.get(Media, media_id)
        if media is not None:
            media.processing_status = ProcessingStatus.COMPLETED
        job = _get_job(db, media_id, JobType.VIDEO_FRAME_EXTRACTION)
        if job is not None:
            job.status = JobStatus.SUCCEEDED
            job.finished_at = _now()
        _maybe_finalize_survey(db, survey_id)


class _KeptFrame:
    __slots__ = ("uri", "width", "height", "frame_number", "blur_score")

    def __init__(self, uri, width, height, frame_number, blur_score):
        self.uri = uri
        self.width = width
        self.height = height
        self.frame_number = frame_number
        self.blur_score = blur_score


def _extract_and_filter(storage, original_key, survey_id, media_id) -> list[_KeptFrame]:
    """Download the video, extract frames, drop blurry + duplicate ones, resize,
    and upload the survivors. Returns metadata for the kept frames."""
    video_bytes = run_async(storage.download(original_key))
    fd, video_path = tempfile.mkstemp(suffix=".mp4", prefix="video_")
    try:
        with os.fdopen(fd, "wb") as fh:
            fh.write(video_bytes)
        frames = extract_frames(video_path, fps=settings.video_frame_rate)
    finally:
        os.unlink(video_path)

    dedup = DuplicateFilter(hamming_threshold=settings.dedup_hamming_threshold)
    kept: list[_KeptFrame] = []
    for index, frame in enumerate(frames):
        blurry, score = is_blurry(frame, threshold=settings.blur_variance_threshold)
        if blurry or dedup.is_duplicate(frame):
            continue
        resized = resize_to_bounds(
            frame, max_width=settings.image_max_width, max_height=settings.image_max_height
        )
        key = frame_key(survey_id, media_id, "jpg")
        uri = run_async(
            storage.upload(
                key, io.BytesIO(resized.data), content_type="image/jpeg", size=len(resized.data)
            )
        )
        kept.append(_KeptFrame(uri, resized.width, resized.height, index, round(score, 4)))
    return kept


# --------------------------------------------------------------------------- #
# Shared helpers
# --------------------------------------------------------------------------- #
def _record_failure(
    media_id: uuid.UUID, job_type: JobType, exc: Exception, attempt: int
) -> None:
    logger.exception("processing_job_failed", extra={"media_id": str(media_id), "job": job_type})
    with worker_session() as db:
        job = _get_job(db, media_id, job_type)
        if job is None:
            return
        job.last_error = str(exc)[:2000]
        if attempt >= settings.job_max_attempts:
            job.status = JobStatus.DEAD_LETTER
            job.finished_at = _now()
            media = db.get(Media, media_id)
            if media is not None:
                media.processing_status = ProcessingStatus.FAILED
        else:
            job.status = JobStatus.FAILED  # transient; Dramatiq will retry


def _maybe_finalize_survey(db: Session, survey_id: uuid.UUID) -> None:
    """Advance the survey to READY_FOR_REVIEW once no media is still pending."""
    survey = db.get(Survey, survey_id)
    if survey is None or survey.status is not SurveyStatus.PROCESSING:
        return
    # Flush pending changes (this media just marked COMPLETED, frames added) so the
    # count reflects them — the worker session has autoflush disabled.
    db.flush()
    unfinished = db.scalar(
        select(func.count())
        .select_from(Media)
        .where(
            Media.survey_id == survey_id,
            Media.processing_status.in_(
                [ProcessingStatus.PENDING, ProcessingStatus.PROCESSING]
            ),
        )
    )
    if unfinished:
        return
    survey.status = SurveyStatus.READY_FOR_REVIEW
    db.add(
        SurveyHistory(
            survey_id=survey_id,
            from_status=SurveyStatus.PROCESSING,
            to_status=SurveyStatus.READY_FOR_REVIEW,
            changed_by=None,
            reason="Media processing complete",
        )
    )


def _safe_delete(storage, uri: str) -> None:
    try:
        run_async(storage.delete(storage.key_from_uri(uri)))
    except Exception:  # noqa: BLE001 - best-effort
        logger.warning("frame_cleanup_failed", extra={"uri": uri})
