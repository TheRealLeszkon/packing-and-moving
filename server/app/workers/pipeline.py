"""Media processing pipeline (worker-side orchestration).

Pure sync functions the actors call. Each is:

- **idempotent** — re-running a completed job is a no-op; a retried video job
  first clears any frames produced by the previous attempt;
- **crash-safe** — job bookkeeping (attempts, status, errors) is written in its
  own transaction so it survives a failure that rolls back the work;
- **self-finalizing** — when a survey's last media finishes, AI analysis is
  queued (an ``ai_analysis_runs`` row + a dispatch); the survey stays in
  PROCESSING until :func:`run_ai_analysis` completes and advances it to
  READY_FOR_REVIEW. If there is nothing to analyse, it advances immediately.

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
from decimal import Decimal

from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.ai.base import ImageInput
from app.ai.factory import get_ai_provider
from app.ai.mapping import build_survey_item
from app.ai.prompts import ACTIVE_PROMPT_VERSION
from app.core.config import settings
from app.db.sync_session import worker_session
from app.models.ai_analysis_run import AIAnalysisRun
from app.models.enums import (
    AIRunStatus,
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
from app.models.survey_item import SurveyItem
from app.processing.blur import is_blurry
from app.processing.dedup import DuplicateFilter
from app.processing.frames import extract_frames
from app.processing.image_ops import resize_to_bounds
from app.storage.factory import get_storage
from app.storage.keys import frame_key, processed_key
from app.workers.dispatch import get_dispatcher
from app.workers.runtime import run_async

# Media eligible for AI analysis: originals that were resized in place (images) and
# frames extracted from videos — both carry a processed JPEG in ``processed_url``.
_ANALYSABLE_MEDIA_TYPES = (MediaType.IMAGE, MediaType.EXTRACTED_FRAME)

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

    ai_survey_id: uuid.UUID | None = None
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
        ai_survey_id = _finalize_media(db, survey_id)
    if ai_survey_id is not None:
        get_dispatcher().dispatch_ai(ai_survey_id)


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

    ai_survey_id: uuid.UUID | None = None
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
        ai_survey_id = _finalize_media(db, survey_id)
    if ai_survey_id is not None:
        get_dispatcher().dispatch_ai(ai_survey_id)


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


def _finalize_media(db: Session, survey_id: uuid.UUID) -> uuid.UUID | None:
    """Handle a media job finishing.

    If media is still outstanding, do nothing. Once the last media completes,
    either queue AI analysis (create a PENDING ``ai_analysis_runs`` row, keep the
    survey in PROCESSING) and return the survey id to dispatch, or — if there is
    nothing analysable — advance straight to READY_FOR_REVIEW and return ``None``.
    """
    survey = db.get(Survey, survey_id)
    if survey is None or survey.status is not SurveyStatus.PROCESSING:
        return None
    # Flush pending changes (this media just marked COMPLETED, frames added) so the
    # counts reflect them — the worker session has autoflush disabled.
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
        return None
    # Idempotency: never queue a second analysis when one is already pending or done.
    already_queued = db.scalar(
        select(func.count())
        .select_from(AIAnalysisRun)
        .where(
            AIAnalysisRun.survey_id == survey_id,
            AIAnalysisRun.status.in_([AIRunStatus.PENDING, AIRunStatus.SUCCEEDED]),
        )
    )
    if already_queued:
        return None
    analysable = _count_analysable_media(db, survey_id)
    if not analysable:
        _transition_to_review(db, survey, run_failed=False, reason="No media to analyze")
        return None
    db.add(
        AIAnalysisRun(
            survey_id=survey_id,
            provider=settings.ai_provider.value,
            model=settings.gemini_model,
            prompt_version=ACTIVE_PROMPT_VERSION,
            status=AIRunStatus.PENDING,
            request_media_count=analysable,
        )
    )
    return survey_id


# --------------------------------------------------------------------------- #
# AI analysis pipeline
# --------------------------------------------------------------------------- #
def run_ai_analysis(survey_id: uuid.UUID) -> None:
    """Analyse a survey's processed media and persist the resulting inventory.

    Idempotent and crash-safe: it claims the survey's PENDING analysis run, calls
    the provider *outside* any DB transaction (slow), then finalises under a row
    lock — re-checking the survey is still PROCESSING so a duplicate delivery
    cannot double-persist. On provider failure the run is recorded FAILED and the
    survey is still unblocked to READY_FOR_REVIEW for manual entry.
    """
    # ---- claim the pending run + snapshot the media to analyse ----
    with worker_session() as db:
        survey = db.get(Survey, survey_id)
        if survey is None or survey.status is not SurveyStatus.PROCESSING:
            return  # already finalised / cancelled (idempotent)
        run = db.scalar(
            select(AIAnalysisRun)
            .where(
                AIAnalysisRun.survey_id == survey_id,
                AIAnalysisRun.status == AIRunStatus.PENDING,
            )
            .order_by(AIAnalysisRun.created_at.desc())
        )
        if run is None:
            return  # nothing queued
        run_id = run.id
        media_refs = [
            (m.id, m.processed_url)
            for m in db.scalars(
                select(Media)
                .where(
                    Media.survey_id == survey_id,
                    Media.processing_status == ProcessingStatus.COMPLETED,
                    Media.processed_url.is_not(None),
                    Media.media_type.in_(_ANALYSABLE_MEDIA_TYPES),
                )
                .order_by(Media.upload_timestamp, Media.frame_number)
            )
        ][: settings.ai_max_images]

    # ---- download bytes + call the provider (no DB transaction held) ----
    storage = get_storage()
    images: list[ImageInput] = []
    media_ids: list[uuid.UUID] = []
    for media_id, uri in media_refs:
        try:
            data = run_async(storage.download(storage.key_from_uri(uri)))
        except Exception:  # noqa: BLE001 - skip unreadable media, keep the rest
            logger.warning("ai_media_download_failed", extra={"media_id": str(media_id)})
            continue
        images.append(ImageInput(data=data, mime_type="image/jpeg"))
        media_ids.append(media_id)

    result = None
    error: str | None = None
    if not images:
        error = "No analysable media could be downloaded."
    else:
        try:
            result = get_ai_provider().analyze(images, prompt_version=ACTIVE_PROMPT_VERSION)
        except Exception as exc:  # noqa: BLE001 - recorded; survey still unblocked
            error = str(exc)[:2000]
            logger.exception("ai_analysis_failed", extra={"survey_id": str(survey_id)})

    # ---- persist result + finalise under a row lock ----
    with worker_session() as db:
        survey = db.get(Survey, survey_id, with_for_update=True)
        if survey is None or survey.status is not SurveyStatus.PROCESSING:
            return  # another worker won the race; discard this result
        run = db.get(AIAnalysisRun, run_id)
        if run is None or run.status is not AIRunStatus.PENDING:
            return
        run.request_media_count = len(media_ids)
        if result is not None:
            _persist_items(db, survey_id, result, media_ids)
            run.status = AIRunStatus.SUCCEEDED
            run.model = result.model
            run.prompt_version = result.prompt_version
            run.raw_response = result.raw_response
            run.prompt_tokens = result.usage.prompt_tokens
            run.completion_tokens = result.usage.completion_tokens
            run.total_tokens = result.usage.total_tokens
            run.latency_ms = result.latency_ms
            _recompute_totals(db, survey)
            _transition_to_review(db, survey, run_failed=False, reason="AI analysis complete")
        else:
            run.status = AIRunStatus.FAILED
            run.error = error
            _transition_to_review(
                db, survey, run_failed=True, reason="AI analysis failed; manual review"
            )


def _count_analysable_media(db: Session, survey_id: uuid.UUID) -> int:
    return db.scalar(
        select(func.count())
        .select_from(Media)
        .where(
            Media.survey_id == survey_id,
            Media.processing_status == ProcessingStatus.COMPLETED,
            Media.processed_url.is_not(None),
            Media.media_type.in_(_ANALYSABLE_MEDIA_TYPES),
        )
    ) or 0


def _persist_items(
    db: Session,
    survey_id: uuid.UUID,
    result,
    media_ids: list[uuid.UUID],
) -> None:
    """Create a ``SurveyItem`` per detected item, linking each to the media it was
    seen in (via ``sourceImageIndexes`` → the analysed media order)."""
    media_by_index = dict(enumerate(media_ids))
    media_objs = (
        {m.id: m for m in db.scalars(select(Media).where(Media.id.in_(media_ids)))}
        if media_ids
        else {}
    )
    for detected in result.analysis.items:
        item = build_survey_item(survey_id, detected)
        linked = [
            media_objs[media_by_index[idx]]
            for idx in dict.fromkeys(detected.source_image_indexes)  # dedupe, keep order
            if idx in media_by_index and media_by_index[idx] in media_objs
        ]
        item.media = linked
        db.add(item)
    db.flush()


def _recompute_totals(db: Session, survey: Survey) -> None:
    """Recompute aggregate volume (m³) and value from all of the survey's items."""
    db.flush()  # make the just-added items visible to the aggregate query
    items = list(
        db.scalars(select(SurveyItem).where(SurveyItem.survey_id == survey.id))
    )
    total_value = Decimal("0")
    total_volume = Decimal("0")  # cubic metres
    for item in items:
        qty = item.quantity or 1
        if item.estimated_value is not None:
            total_value += item.estimated_value * qty
        if item.height_cm and item.width_cm and item.depth_cm:
            volume_m3 = (item.height_cm / 100) * (item.width_cm / 100) * (item.depth_cm / 100)
            total_volume += volume_m3 * qty
    survey.total_value_estimate = total_value
    survey.total_volume_estimate = total_volume


def _transition_to_review(
    db: Session, survey: Survey, *, run_failed: bool, reason: str
) -> None:
    survey.status = SurveyStatus.READY_FOR_REVIEW
    db.add(
        SurveyHistory(
            survey_id=survey.id,
            from_status=SurveyStatus.PROCESSING,
            to_status=SurveyStatus.READY_FOR_REVIEW,
            changed_by=None,
            reason=reason,
        )
    )


def _safe_delete(storage, uri: str) -> None:
    try:
        run_async(storage.delete(storage.key_from_uri(uri)))
    except Exception:  # noqa: BLE001 - best-effort
        logger.warning("frame_cleanup_failed", extra={"uri": uri})
