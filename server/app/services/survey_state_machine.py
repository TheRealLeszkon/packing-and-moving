"""Survey workflow state machine.

The single source of truth for *which* status changes are legal, *from which*
states, and *who* may trigger them. Services never mutate ``survey.status``
directly — they ask this module to resolve a transition, which enforces both the
state graph and object-level authorization.

Legal graph (● start ○ terminal):

    ● SCHEDULED ──accept(surveyor)──▶ ASSIGNED ──start──▶ IN_PROGRESS
      IN_PROGRESS ──complete──▶ PROCESSING ──(system)──▶ READY_FOR_REVIEW
      READY_FOR_REVIEW ──submit──▶ AWAITING_CUSTOMER_APPROVAL
      READY_FOR_REVIEW / REVISION_REQUIRED ──reanalyze──▶ PROCESSING  (re-run AI)
      AWAITING_CUSTOMER_APPROVAL ──approve──▶ APPROVED ○
      AWAITING_CUSTOMER_APPROVAL ──reject──▶ REVISION_REQUIRED
      REVISION_REQUIRED ──start──▶ IN_PROGRESS      (re-capture media)
      REVISION_REQUIRED ──submit──▶ AWAITING_CUSTOMER_APPROVAL  (re-submit edits)
      (most non-terminal) ──cancel(customer)──▶ CANCELLED ○
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from app.core.exceptions import AuthorizationError, InvalidStateTransitionError
from app.models.enums import SurveyStatus, UserRole
from app.models.survey import Survey
from app.models.user import User


class SurveyAction(StrEnum):
    ACCEPT = "accept"
    START = "start"
    COMPLETE = "complete"
    REANALYZE = "reanalyze"  # surveyor re-runs AI on a survey under review
    PROCESSING_COMPLETE = "processing_complete"  # performed by the pipeline (system)
    SUBMIT = "submit"
    APPROVE = "approve"
    REJECT = "reject"
    CANCEL = "cancel"


class ActorRequirement(StrEnum):
    ANY_SURVEYOR = "any_surveyor"            # any surveyor (used for accept)
    ASSIGNED_SURVEYOR = "assigned_surveyor"  # the surveyor assigned to this survey
    CUSTOMER_OWNER = "customer_owner"        # the customer who owns this survey
    SYSTEM = "system"                        # internal/automated, no end-user


@dataclass(frozen=True, slots=True)
class Transition:
    sources: frozenset[SurveyStatus]
    target: SurveyStatus
    actor: ActorRequirement


_CANCELLABLE = frozenset(
    {
        SurveyStatus.SCHEDULED,
        SurveyStatus.ASSIGNED,
        SurveyStatus.IN_PROGRESS,
        SurveyStatus.PROCESSING,
        SurveyStatus.READY_FOR_REVIEW,
        SurveyStatus.AWAITING_CUSTOMER_APPROVAL,
        SurveyStatus.REVISION_REQUIRED,
    }
)

TRANSITIONS: dict[SurveyAction, Transition] = {
    SurveyAction.ACCEPT: Transition(
        frozenset({SurveyStatus.SCHEDULED}), SurveyStatus.ASSIGNED, ActorRequirement.ANY_SURVEYOR
    ),
    SurveyAction.START: Transition(
        frozenset({SurveyStatus.ASSIGNED, SurveyStatus.REVISION_REQUIRED}),
        SurveyStatus.IN_PROGRESS,
        ActorRequirement.ASSIGNED_SURVEYOR,
    ),
    SurveyAction.COMPLETE: Transition(
        frozenset({SurveyStatus.IN_PROGRESS}),
        SurveyStatus.PROCESSING,
        ActorRequirement.ASSIGNED_SURVEYOR,
    ),
    SurveyAction.REANALYZE: Transition(
        frozenset({SurveyStatus.READY_FOR_REVIEW, SurveyStatus.REVISION_REQUIRED}),
        SurveyStatus.PROCESSING,
        ActorRequirement.ASSIGNED_SURVEYOR,
    ),
    SurveyAction.PROCESSING_COMPLETE: Transition(
        frozenset({SurveyStatus.PROCESSING}),
        SurveyStatus.READY_FOR_REVIEW,
        ActorRequirement.SYSTEM,
    ),
    SurveyAction.SUBMIT: Transition(
        frozenset({SurveyStatus.READY_FOR_REVIEW, SurveyStatus.REVISION_REQUIRED}),
        SurveyStatus.AWAITING_CUSTOMER_APPROVAL,
        ActorRequirement.ASSIGNED_SURVEYOR,
    ),
    SurveyAction.APPROVE: Transition(
        frozenset({SurveyStatus.AWAITING_CUSTOMER_APPROVAL}),
        SurveyStatus.APPROVED,
        ActorRequirement.CUSTOMER_OWNER,
    ),
    SurveyAction.REJECT: Transition(
        frozenset({SurveyStatus.AWAITING_CUSTOMER_APPROVAL}),
        SurveyStatus.REVISION_REQUIRED,
        ActorRequirement.CUSTOMER_OWNER,
    ),
    SurveyAction.CANCEL: Transition(
        _CANCELLABLE, SurveyStatus.CANCELLED, ActorRequirement.CUSTOMER_OWNER
    ),
}


def _actor_satisfies(requirement: ActorRequirement, survey: Survey, user: User | None) -> bool:
    if requirement is ActorRequirement.SYSTEM:
        return user is None
    if user is None:
        return False
    if user.role is UserRole.ADMIN:  # admins may drive any user-triggerable transition
        return True
    match requirement:
        case ActorRequirement.ANY_SURVEYOR:
            return user.role is UserRole.SURVEYOR
        case ActorRequirement.ASSIGNED_SURVEYOR:
            return user.role is UserRole.SURVEYOR and survey.surveyor_id == user.id
        case ActorRequirement.CUSTOMER_OWNER:
            return user.role is UserRole.CUSTOMER and survey.customer_id == user.id
    return False


def resolve_transition(
    action: SurveyAction, survey: Survey, user: User | None
) -> SurveyStatus:
    """Validate a requested transition and return the resulting status.

    Raises ``AuthorizationError`` if ``user`` may not perform ``action`` on this
    survey, or ``InvalidStateTransitionError`` if the survey's current status
    does not permit it.
    """
    transition = TRANSITIONS[action]
    if not _actor_satisfies(transition.actor, survey, user):
        raise AuthorizationError(f"You are not permitted to {action.value} this survey.")
    if survey.status not in transition.sources:
        raise InvalidStateTransitionError(
            f"Cannot {action.value} a survey in status '{survey.status.value}'."
        )
    return transition.target


def available_actions(survey: Survey, user: User | None) -> list[SurveyAction]:
    """User-triggerable actions valid for ``survey`` right now, for ``user``.

    Excludes SYSTEM-only transitions. Useful for driving client UIs.
    """
    return [
        action
        for action, transition in TRANSITIONS.items()
        if transition.actor is not ActorRequirement.SYSTEM
        and survey.status in transition.sources
        and _actor_satisfies(transition.actor, survey, user)
    ]
