"""User profile routes."""

from __future__ import annotations

from fastapi import APIRouter

from app.core.config import settings
from app.core.exceptions import NotFoundError
from app.core.responses import SuccessResponse
from app.dependencies.auth import CurrentUser
from app.dependencies.database import SessionDep
from app.repositories.user import UserRepository
from app.schemas.user import RoleUpdate, UserResponse, UserUpdate
from app.services.user import UserService

router = APIRouter(prefix="/users", tags=["users"])


@router.get(
    "/me",
    response_model=SuccessResponse[UserResponse],
    summary="Get the current user's profile",
)
async def get_me(current_user: CurrentUser) -> SuccessResponse[UserResponse]:
    return SuccessResponse(data=UserResponse.model_validate(current_user))


@router.put(
    "/me",
    response_model=SuccessResponse[UserResponse],
    summary="Update the current user's profile",
)
async def update_me(
    payload: UserUpdate, current_user: CurrentUser, session: SessionDep
) -> SuccessResponse[UserResponse]:
    service = UserService(UserRepository(session))
    updated = await service.update_profile(current_user, payload)
    return SuccessResponse(data=UserResponse.model_validate(updated))


@router.post(
    "/me/role",
    response_model=SuccessResponse[UserResponse],
    summary="Demo-only: switch the current user's role (customer/surveyor)",
)
async def set_my_role(
    payload: RoleUpdate, current_user: CurrentUser, session: SessionDep
) -> SuccessResponse[UserResponse]:
    """Self-service role switch for testing/demos, gated by ``allow_self_role_change``.

    The next request re-reads the role from the DB (``get_current_user``), so no
    token refresh is needed — authorization and available actions update at once.
    When the flag is off (e.g. production) the endpoint 404s, hiding its existence.
    """
    if not settings.allow_self_role_change:
        raise NotFoundError("Not found.")
    service = UserService(UserRepository(session))
    updated = await service.set_role(current_user, payload.role)
    return SuccessResponse(data=UserResponse.model_validate(updated))
