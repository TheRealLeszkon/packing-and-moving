"""Service provider dependencies.

Constructs services with their repositories bound to the request session, so
routes depend on a ready-to-use service and never wire repositories themselves.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends

from app.dependencies.database import SessionDep
from app.repositories.survey import SurveyRepository
from app.services.survey import SurveyService


def get_survey_service(session: SessionDep) -> SurveyService:
    return SurveyService(SurveyRepository(session))


SurveyServiceDep = Annotated[SurveyService, Depends(get_survey_service)]
