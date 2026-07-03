"""ORM model registry.

Importing this package imports every model module so their tables register on
``Base.metadata`` before Alembic autogeneration or app startup. Import models
from here (``from app.models import Survey``) rather than from submodules.
"""

from __future__ import annotations

from app.models.ai_analysis_run import AIAnalysisRun
from app.models.item_media import item_media
from app.models.media import Media
from app.models.media_processing_job import MediaProcessingJob
from app.models.refresh_token import RefreshToken
from app.models.survey import Survey
from app.models.survey_history import SurveyHistory
from app.models.survey_item import SurveyItem
from app.models.user import User

__all__ = [
    "AIAnalysisRun",
    "Media",
    "MediaProcessingJob",
    "RefreshToken",
    "Survey",
    "SurveyHistory",
    "SurveyItem",
    "User",
    "item_media",
]
