"""Media service — upload, retrieval, listing, deletion.

Owns the upload pipeline's *synchronous* front half: validate → stream to storage
→ persist a Media row → enqueue a processing-job ledger entry. The heavy async
processing (resize, frame extraction, AI) is picked up from those ledger rows by
the Phase 5 worker; here we only record that work is due.

Uploads are gated: only the *assigned surveyor* may upload, and only while the
survey is in a capture-or-review state (IN_PROGRESS, READY_FOR_REVIEW,
REVISION_REQUIRED) — so evidence can still be added while reviewing (e.g. to
replace a blurry photo or back a manually-added item). Storage writes are rolled
back (deleted) if the request fails partway, so a failed multi-file upload leaves
no orphaned objects.
"""

from __future__ import annotations

import asyncio
import logging
import os
import uuid
from datetime import UTC, datetime
from decimal import Decimal
from functools import partial

from fastapi import UploadFile

from app.core.config import settings
from app.core.exceptions import AuthorizationError, ConflictError, NotFoundError, ValidationError
from app.db.session import register_after_commit
from app.media.upload_utils import spool_to_tempfile
from app.media.validation import validate_image, validate_video
from app.models.enums import JobStatus, JobType, ProcessingStatus, SurveyStatus
from app.models.media import Media
from app.models.media_processing_job import MediaProcessingJob
from app.models.survey import Survey
from app.models.user import User
from app.processing.video_probe import probe_duration_seconds
from app.repositories.media import MediaRepository
from app.repositories.survey import SurveyRepository
from app.schemas.pagination import PageParams
from app.services.visibility import can_view_survey
from app.storage.base import StoragePort
from app.storage.keys import original_key
from app.workers.dispatch import ProcessingDispatcher

logger = logging.getLogger(__name__)

_TERMINAL = frozenset({SurveyStatus.APPROVED, SurveyStatus.COMPLETED, SurveyStatus.CANCELLED})
# States in which the assigned surveyor may still upload media: on-site capture
# plus the review states, so photos can be added/replaced while reviewing.
_UPLOADABLE = frozenset(
    {
        SurveyStatus.IN_PROGRESS,
        SurveyStatus.READY_FOR_REVIEW,
        SurveyStatus.REVISION_REQUIRED,
    }
)


class MediaService:
    def __init__(
        self,
        *,
        media: MediaRepository,
        surveys: SurveyRepository,
        storage: StoragePort,
        dispatcher: ProcessingDispatcher,
    ) -> None:
        self._media = media
        self._surveys = surveys
        self._storage = storage
        self._dispatcher = dispatcher
        self._session = media.session

    # ---- uploads ----------------------------------------------------------
    async def upload_images(
        self,
        surveyor: User,
        survey_id: uuid.UUID,
        uploads: list[UploadFile],
        *,
        room_location: str | None,
    ) -> list[Media]:
        survey = await self._survey_for_upload(surveyor, survey_id)
        return await self._store_all(
            survey,
            surveyor,
            uploads,
            room_location=room_location,
            is_video=False,
        )

    async def upload_videos(
        self,
        surveyor: User,
        survey_id: uuid.UUID,
        uploads: list[UploadFile],
        *,
        room_location: str | None,
    ) -> list[Media]:
        survey = await self._survey_for_upload(surveyor, survey_id)
        return await self._store_all(
            survey,
            surveyor,
            uploads,
            room_location=room_location,
            is_video=True,
        )

    # ---- reads ------------------------------------------------------------
    async def get_with_url(self, user: User, media_id: uuid.UUID) -> tuple[Media, str | None]:
        media = await self._visible_media(user, media_id)
        return media, await self._sign(media)

    async def list_for_survey(
        self, user: User, survey_id: uuid.UUID, params: PageParams
    ) -> tuple[list[tuple[Media, str | None]], int]:
        survey = await self._surveys.get(survey_id)
        if survey is None or not can_view_survey(survey, user):
            raise NotFoundError("Survey not found.")
        rows, total = await self._media.list_for_survey(
            survey_id, limit=params.limit, offset=params.offset
        )
        signed = [(m, await self._sign(m)) for m in rows]
        return signed, total

    # ---- deletion ---------------------------------------------------------
    async def delete(self, user: User, media_id: uuid.UUID) -> None:
        media = await self._visible_media(user, media_id)
        survey = await self._surveys.get(media.survey_id)
        assert survey is not None  # _visible_media already loaded it
        is_admin = user.role.value == "admin"
        if not (is_admin or survey.surveyor_id == user.id):
            raise AuthorizationError("Only the assigned surveyor may delete this media.")
        if survey.status in _TERMINAL:
            raise ConflictError("Media cannot be deleted after the survey is finalized.")

        for uri in (media.original_url, media.processed_url):
            if uri:
                await self._safe_delete(uri)
        await self._media.delete(media)

    # ---- internals --------------------------------------------------------
    async def _survey_for_upload(self, surveyor: User, survey_id: uuid.UUID) -> Survey:
        survey = await self._surveys.get(survey_id)
        if survey is None or not can_view_survey(survey, surveyor):
            raise NotFoundError("Survey not found.")
        if survey.surveyor_id != surveyor.id:
            raise AuthorizationError("You are not the assigned surveyor for this survey.")
        if survey.status not in _UPLOADABLE:
            raise ConflictError(
                "Media can only be uploaded while the survey is in progress or under review."
            )
        return survey

    async def _store_all(
        self,
        survey: Survey,
        surveyor: User,
        uploads: list[UploadFile],
        *,
        room_location: str | None,
        is_video: bool,
    ) -> list[Media]:
        if not uploads:
            raise ValidationError("At least one file is required.")

        created: list[Media] = []
        uploaded_keys: list[str] = []
        try:
            for upload in uploads:
                media, key = await self._store_one(
                    survey, surveyor, upload, room_location=room_location, is_video=is_video
                )
                uploaded_keys.append(key)
                created.append(media)
        except BaseException:
            # Roll back storage writes so a failed batch leaves no orphans.
            for key in uploaded_keys:
                try:
                    await self._storage.delete(key)
                except Exception:  # noqa: BLE001 - best-effort cleanup
                    logger.warning("orphan_cleanup_failed", extra={"key": key})
            raise

        await self._session.flush()
        return created

    async def _store_one(
        self,
        survey: Survey,
        surveyor: User,
        upload: UploadFile,
        *,
        room_location: str | None,
        is_video: bool,
    ) -> tuple[Media, str]:
        max_bytes = (
            settings.max_video_size_mb if is_video else settings.max_image_size_mb
        ) * 1024 * 1024
        spooled = await spool_to_tempfile(upload, max_bytes=max_bytes)
        try:
            if is_video:
                validated = validate_video(upload.content_type, spooled.header)
                duration = await probe_duration_seconds(spooled.path)
                if duration > settings.max_video_duration_seconds:
                    raise ValidationError(
                        f"Video is {duration:.0f}s; the maximum is "
                        f"{settings.max_video_duration_seconds}s."
                    )
                duration_value: Decimal | None = Decimal(str(round(duration, 2)))
                job_type = JobType.VIDEO_FRAME_EXTRACTION
            else:
                validated = validate_image(upload.content_type, spooled.header)
                duration_value = None
                job_type = JobType.IMAGE_RESIZE

            key = original_key(survey.id, validated.extension)
            content_type = (upload.content_type or "").split(";")[0].strip()
            # Open in a thread; the actual read is done by the storage backend
            # (off the event loop for GCS) rather than blocking here.
            fh = await asyncio.to_thread(open, spooled.path, "rb")
            try:
                uri = await self._storage.upload(
                    key, fh, content_type=content_type, size=spooled.size
                )
            finally:
                await asyncio.to_thread(fh.close)
        finally:
            await asyncio.to_thread(os.unlink, spooled.path)

        media = Media(
            survey_id=survey.id,
            uploaded_by=surveyor.id,
            media_type=validated.media_type,
            original_url=uri,
            room_location=room_location,
            processing_status=ProcessingStatus.PENDING,
            duration_seconds=duration_value,
        )
        self._media.add(media)
        await self._session.flush()  # assign media.id for the job FK
        self._enqueue_job(media.id, job_type)
        return media, key

    def _enqueue_job(self, media_id: uuid.UUID, job_type: JobType) -> None:
        # Durable ledger row (committed atomically with the Media row) plus a
        # post-commit dispatch so the broker only learns about the job once its
        # data is persisted. The unique idempotency key makes re-enqueue safe.
        self._session.add(
            MediaProcessingJob(
                media_id=media_id,
                job_type=job_type,
                status=JobStatus.QUEUED,
                idempotency_key=f"{media_id}:{job_type.value}",
                scheduled_at=datetime.now(UTC),
            )
        )
        register_after_commit(
            self._session, partial(self._dispatcher.dispatch, media_id, job_type)
        )

    async def _visible_media(self, user: User, media_id: uuid.UUID) -> Media:
        media = await self._media.get(media_id)
        if media is None:
            raise NotFoundError("Media not found.")
        survey = await self._surveys.get(media.survey_id)
        if survey is None or not can_view_survey(survey, user):
            raise NotFoundError("Media not found.")
        return media

    async def _sign(self, media: Media) -> str | None:
        uri = media.processed_url or media.original_url
        if not uri:
            return None
        key = self._storage.key_from_uri(uri)
        return await self._storage.signed_url(key, expires_in=settings.signed_url_ttl_seconds)

    async def _safe_delete(self, uri: str) -> None:
        try:
            await self._storage.delete(self._storage.key_from_uri(uri))
        except Exception:  # noqa: BLE001 - deletion is best-effort
            logger.warning("storage_delete_failed", extra={"uri": uri})
