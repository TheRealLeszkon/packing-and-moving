"""Shared survey visibility rule.

Centralises "who may see this survey" so survey and media services agree. A
survey is visible to: an admin; its owning customer; its assigned surveyor; or
any surveyor while it is an open (scheduled, unassigned) request.
"""

from __future__ import annotations

from app.models.enums import SurveyStatus, UserRole
from app.models.survey import Survey
from app.models.user import User


def can_view_survey(survey: Survey, user: User) -> bool:
    if user.role is UserRole.ADMIN:
        return True
    if user.role is UserRole.CUSTOMER:
        return survey.customer_id == user.id
    return survey.surveyor_id == user.id or (
        survey.status is SurveyStatus.SCHEDULED and survey.surveyor_id is None
    )
