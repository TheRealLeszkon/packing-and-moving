"""Service provider dependencies.

Constructs services with their repositories bound to the request session, so
routes depend on a ready-to-use service and never wire repositories themselves.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends

from app.dependencies.database import SessionDep
from app.repositories.item import SurveyItemRepository
from app.repositories.media import MediaRepository
from app.repositories.survey import SurveyRepository
from app.services.admin import AdminService
from app.services.item import SurveyItemService
from app.services.media import MediaService
from app.services.survey import SurveyService
from app.storage.base import StoragePort
from app.storage.factory import get_storage
from app.workers.dispatch import get_dispatcher


def get_survey_service(session: SessionDep) -> SurveyService:
    return SurveyService(SurveyRepository(session))


SurveyServiceDep = Annotated[SurveyService, Depends(get_survey_service)]


def get_item_service(session: SessionDep) -> SurveyItemService:
    return SurveyItemService(
        items=SurveyItemRepository(session),
        surveys=SurveyRepository(session),
    )


ItemServiceDep = Annotated[SurveyItemService, Depends(get_item_service)]

StorageDep = Annotated[StoragePort, Depends(get_storage)]


def get_media_service(session: SessionDep, storage: StorageDep) -> MediaService:
    return MediaService(
        media=MediaRepository(session),
        surveys=SurveyRepository(session),
        storage=storage,
        dispatcher=get_dispatcher(),
    )


MediaServiceDep = Annotated[MediaService, Depends(get_media_service)]


def get_admin_service(session: SessionDep) -> AdminService:
    return AdminService(session, get_dispatcher())


AdminServiceDep = Annotated[AdminService, Depends(get_admin_service)]
