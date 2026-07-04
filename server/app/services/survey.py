"""Survey service — lifecycle orchestration.

Owns all survey business rules: creation, visibility, the request board
(available/accept/assigned), and workflow transitions. Status changes go through
``survey_state_machine`` (which enforces the graph + authorization) and every
change is recorded in ``survey_history``. The service flushes after each mutation
so optimistic-lock conflicts surface here as ``StaleDataError`` (mapped to 409).
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence
from functools import partial

from app.ai.prompts import ACTIVE_PROMPT_VERSION
from app.core.config import settings
from app.core.exceptions import ConflictError, NotFoundError
from app.db.session import register_after_commit
from app.models.ai_analysis_run import AIAnalysisRun
from app.models.enums import (
    AIRunStatus,
    ProcessingStage,
    ReanalysisMode,
    SurveyStatus,
    UserRole,
)
from app.models.survey import Survey
from app.models.survey_history import SurveyHistory
from app.models.survey_item import SurveyItem
from app.models.user import User
from app.repositories.survey import SurveyRepository
from app.schemas.pagination import PageParams
from app.schemas.survey import SurveyCreate
from app.services.survey_state_machine import (
    SurveyAction,
    resolve_transition,
)
from app.services.visibility import can_view_survey
from app.workers.dispatch import ProcessingDispatcher


class SurveyService:
    def __init__(
        self, surveys: SurveyRepository, dispatcher: ProcessingDispatcher | None = None
    ) -> None:
        self._surveys = surveys
        self._session = surveys.session
        self._dispatcher = dispatcher

    # ---- creation ---------------------------------------------------------
    async def create(self, customer: User, data: SurveyCreate) -> Survey:
        survey = Survey(
            name=data.name,
            origin_address=data.origin_address,
            destination_address=data.destination_address,
            preferred_datetime=data.preferred_datetime,
            customer_id=customer.id,
            status=SurveyStatus.SCHEDULED,
        )
        self._surveys.add(survey)
        await self._session.flush()  # assign id before writing history
        self._record_history(survey, None, SurveyStatus.SCHEDULED, customer, "Survey created")
        await self._session.flush()
        return survey

    # ---- reads ------------------------------------------------------------
    async def get_visible(self, user: User, survey_id: uuid.UUID) -> Survey:
        """Load a survey the ``user`` is allowed to see, else 404 (hides existence)."""
        survey = await self._surveys.get(survey_id)
        if survey is None or not can_view_survey(survey, user):
            raise NotFoundError("Survey not found.")
        return survey

    async def list_mine(
        self, user: User, params: PageParams
    ) -> tuple[Sequence[Survey], int]:
        """Surveys owned (customer) or assigned (surveyor) to the caller."""
        if user.role is UserRole.SURVEYOR:
            return await self._surveys.list_for_surveyor(
                user.id, limit=params.limit, offset=params.offset
            )
        return await self._surveys.list_for_customer(
            user.id, limit=params.limit, offset=params.offset
        )

    async def processing_stage(self, survey: Survey) -> ProcessingStage | None:
        """Observed pipeline stage for a PROCESSING survey (else ``None``).

        Derived from real state: media still resizing/extracting → MEDIA_PROCESSING;
        otherwise a queued AI run → AI_ANALYSIS; otherwise FINALIZING (analysis done,
        advancing to review).
        """
        if survey.status is not SurveyStatus.PROCESSING:
            return None
        if await self._surveys.count_unfinished_media(survey.id):
            return ProcessingStage.MEDIA_PROCESSING
        if await self._surveys.has_pending_ai_run(survey.id):
            return ProcessingStage.AI_ANALYSIS
        return ProcessingStage.FINALIZING

    async def list_items(self, user: User, survey_id: uuid.UUID) -> Sequence[SurveyItem]:
        """Inventory items for a survey the caller may view (AI-generated + manual)."""
        await self.get_visible(user, survey_id)  # 404s if not visible
        return await self._surveys.list_items(survey_id)

    async def list_available(self, params: PageParams) -> tuple[Sequence[Survey], int]:
        return await self._surveys.list_available(limit=params.limit, offset=params.offset)

    async def list_assigned(
        self, surveyor: User, params: PageParams
    ) -> tuple[Sequence[Survey], int]:
        return await self._surveys.list_for_surveyor(
            surveyor.id, limit=params.limit, offset=params.offset
        )

    # ---- deletion ---------------------------------------------------------
    async def delete(self, user: User, survey_id: uuid.UUID) -> None:
        survey = await self._surveys.get(survey_id)
        is_admin = user.role is UserRole.ADMIN
        is_owner = user.role is UserRole.CUSTOMER and survey is not None \
            and survey.customer_id == user.id
        if survey is None or not (is_admin or is_owner):
            raise NotFoundError("Survey not found.")
        # Once a surveyor is involved there is history worth keeping — cancel,
        # don't delete. Admins may delete regardless.
        if not is_admin and survey.status is not SurveyStatus.SCHEDULED:
            raise ConflictError("Only unassigned (scheduled) surveys can be deleted.")
        await self._surveys.delete(survey)

    # ---- transitions ------------------------------------------------------
    async def accept(self, surveyor: User, survey_id: uuid.UUID) -> Survey:
        survey = await self.get_visible(surveyor, survey_id)
        return await self._apply(survey, SurveyAction.ACCEPT, surveyor)

    async def perform(
        self,
        user: User,
        survey_id: uuid.UUID,
        action: SurveyAction,
        *,
        reason: str | None = None,
    ) -> Survey:
        survey = await self.get_visible(user, survey_id)
        return await self._apply(survey, action, user, reason=reason)

    async def reanalyze(
        self, user: User, survey_id: uuid.UUID, mode: ReanalysisMode
    ) -> tuple[Survey, list[uuid.UUID]]:
        """Send a reviewed survey back to PROCESSING and queue a fresh AI run.

        Validates the ``REANALYZE`` transition (assigned surveyor, review state),
        records a PENDING ``AIAnalysisRun`` as the processing job, and dispatches
        the worker (post-commit) to run analysis in ``mode``. Returns the survey
        and the created job id(s).
        """
        survey = await self.get_visible(user, survey_id)
        await self._apply(survey, SurveyAction.REANALYZE, user)
        run = AIAnalysisRun(
            survey_id=survey.id,
            provider=settings.ai_provider.value,
            model=settings.gemini_model,
            prompt_version=ACTIVE_PROMPT_VERSION,
            status=AIRunStatus.PENDING,
        )
        self._session.add(run)
        await self._session.flush()  # assign run.id for the response + dispatch
        if self._dispatcher is not None:
            register_after_commit(
                self._session,
                partial(self._dispatcher.dispatch_reanalyze, survey.id, mode.value),
            )
        return survey, [run.id]

    async def mark_processing_complete(self, survey_id: uuid.UUID) -> Survey:
        """System transition PROCESSING -> READY_FOR_REVIEW (called by the pipeline)."""
        survey = await self._surveys.get(survey_id)
        if survey is None:
            raise NotFoundError("Survey not found.")
        return await self._apply(survey, SurveyAction.PROCESSING_COMPLETE, None)

    # ---- internals --------------------------------------------------------
    async def _apply(
        self,
        survey: Survey,
        action: SurveyAction,
        user: User | None,
        *,
        reason: str | None = None,
    ) -> Survey:
        previous = survey.status
        target = resolve_transition(action, survey, user)

        if action is SurveyAction.ACCEPT and user is not None:
            survey.surveyor_id = user.id

        survey.status = target
        self._record_history(survey, previous, target, user, reason)
        # Flush now so an optimistic-lock (version_id) conflict raises here.
        await self._session.flush()
        # Completing capture moves the survey into PROCESSING. Media jobs that
        # already finished won't re-trigger analysis, so kick a finalize check
        # (post-commit, once the status change is durable) to start AI or advance
        # straight to review. Idempotent worker-side.
        if action is SurveyAction.COMPLETE and self._dispatcher is not None:
            register_after_commit(
                self._session, partial(self._dispatcher.dispatch_finalize, survey.id)
            )
        return survey

    def _record_history(
        self,
        survey: Survey,
        from_status: SurveyStatus | None,
        to_status: SurveyStatus,
        user: User | None,
        reason: str | None,
    ) -> None:
        self._session.add(
            SurveyHistory(
                survey_id=survey.id,
                from_status=from_status,
                to_status=to_status,
                changed_by=user.id if user is not None else None,
                reason=reason,
            )
        )
