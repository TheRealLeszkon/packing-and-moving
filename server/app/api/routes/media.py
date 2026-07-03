"""Media routes: upload, retrieval, listing, deletion.

Uploads accept multipart form data (files + optional room_location) and return
202-style acknowledgements — the objects are stored synchronously but their
processing runs asynchronously (Phase 5). Read endpoints return short-lived
signed URLs, never the raw storage URIs.
"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, File, Form, Query, UploadFile, status

from app.core.responses import SuccessResponse
from app.dependencies.auth import CurrentUser, RequireSurveyor
from app.dependencies.services import MediaServiceDep
from app.models.media import Media
from app.schemas.common import MessageResponse
from app.schemas.media import MediaResponse, MediaUploadResponse
from app.schemas.pagination import Page, PageParams

router = APIRouter(tags=["media"])

_PageQuery = Annotated[PageParams, Query()]


def _resp(media: Media, url: str | None) -> MediaResponse:
    out = MediaResponse.model_validate(media)
    out.url = url
    return out


@router.post(
    "/surveys/{survey_id}/images",
    response_model=SuccessResponse[MediaUploadResponse],
    status_code=status.HTTP_201_CREATED,
    summary="Upload one or more images",
)
async def upload_images(
    survey_id: uuid.UUID,
    surveyor: RequireSurveyor,
    service: MediaServiceDep,
    files: Annotated[list[UploadFile], File(...)],
    room_location: Annotated[str | None, Form()] = None,
) -> SuccessResponse[MediaUploadResponse]:
    created = await service.upload_images(
        surveyor, survey_id, files, room_location=room_location
    )
    items = [_resp(m, None) for m in created]
    return SuccessResponse(data=MediaUploadResponse(items=items, count=len(items)))


@router.post(
    "/surveys/{survey_id}/videos",
    response_model=SuccessResponse[MediaUploadResponse],
    status_code=status.HTTP_201_CREATED,
    summary="Upload one or more videos",
)
async def upload_videos(
    survey_id: uuid.UUID,
    surveyor: RequireSurveyor,
    service: MediaServiceDep,
    files: Annotated[list[UploadFile], File(...)],
    room_location: Annotated[str | None, Form()] = None,
) -> SuccessResponse[MediaUploadResponse]:
    created = await service.upload_videos(
        surveyor, survey_id, files, room_location=room_location
    )
    items = [_resp(m, None) for m in created]
    return SuccessResponse(data=MediaUploadResponse(items=items, count=len(items)))


@router.get(
    "/surveys/{survey_id}/media",
    response_model=SuccessResponse[Page[MediaResponse]],
    summary="List a survey's media",
)
async def list_media(
    survey_id: uuid.UUID,
    user: CurrentUser,
    service: MediaServiceDep,
    params: _PageQuery,
) -> SuccessResponse[Page[MediaResponse]]:
    rows, total = await service.list_for_survey(user, survey_id, params)
    items = [_resp(m, url) for m, url in rows]
    return SuccessResponse(data=Page.create(items, total, params))


@router.get(
    "/media/{media_id}",
    response_model=SuccessResponse[MediaResponse],
    summary="Get media metadata and a signed URL",
)
async def get_media(
    media_id: uuid.UUID, user: CurrentUser, service: MediaServiceDep
) -> SuccessResponse[MediaResponse]:
    media, url = await service.get_with_url(user, media_id)
    return SuccessResponse(data=_resp(media, url))


@router.delete(
    "/media/{media_id}",
    response_model=SuccessResponse[MessageResponse],
    summary="Delete a media item",
)
async def delete_media(
    media_id: uuid.UUID, user: CurrentUser, service: MediaServiceDep
) -> SuccessResponse[MessageResponse]:
    await service.delete(user, media_id)
    return SuccessResponse(data=MessageResponse(detail="Media deleted."))
