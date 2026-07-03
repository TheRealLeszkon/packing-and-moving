"""User profile routes."""

from __future__ import annotations

from fastapi import APIRouter

from app.core.responses import SuccessResponse
from app.dependencies.auth import CurrentUser
from app.dependencies.database import SessionDep
from app.repositories.user import UserRepository
from app.schemas.user import UserResponse, UserUpdate
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
