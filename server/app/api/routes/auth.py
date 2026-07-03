"""Authentication routes.

Thin adapters: parse/validate the request, delegate to ``AuthService``, and shape
the response envelope. No business logic or database access here.
"""

from __future__ import annotations

from fastapi import APIRouter, status

from app.core.config import settings
from app.core.responses import SuccessResponse
from app.dependencies.auth import AuthServiceDep, CurrentUser
from app.schemas.auth import (
    GoogleAuthRequest,
    LogoutRequest,
    RefreshRequest,
    TokenResponse,
)
from app.schemas.common import MessageResponse
from app.schemas.user import UserResponse
from app.services.auth import TokenBundle

router = APIRouter(tags=["auth"])


def _token_payload(bundle: TokenBundle) -> SuccessResponse[TokenResponse]:
    return SuccessResponse(
        data=TokenResponse(
            access_token=bundle.access_token,
            refresh_token=bundle.refresh_token,
            expires_in=settings.access_token_ttl_minutes * 60,
            user=UserResponse.model_validate(bundle.user),
        )
    )


@router.post(
    "/auth/google",
    response_model=SuccessResponse[TokenResponse],
    summary="Sign in with a Google ID token",
)
async def sign_in_with_google(
    payload: GoogleAuthRequest, service: AuthServiceDep
) -> SuccessResponse[TokenResponse]:
    bundle = await service.sign_in_with_google(payload.id_token)
    return _token_payload(bundle)


@router.post(
    "/auth/refresh",
    response_model=SuccessResponse[TokenResponse],
    summary="Rotate a refresh token for a new access token",
)
async def refresh_tokens(
    payload: RefreshRequest, service: AuthServiceDep
) -> SuccessResponse[TokenResponse]:
    bundle = await service.refresh(payload.refresh_token)
    return _token_payload(bundle)


@router.post(
    "/auth/logout",
    response_model=SuccessResponse[MessageResponse],
    status_code=status.HTTP_200_OK,
    summary="Revoke a refresh token",
)
async def logout(
    payload: LogoutRequest, service: AuthServiceDep
) -> SuccessResponse[MessageResponse]:
    await service.logout(payload.refresh_token)
    return SuccessResponse(data=MessageResponse(detail="Logged out."))


@router.get(
    "/auth/me",
    response_model=SuccessResponse[UserResponse],
    summary="Return the authenticated user",
)
async def me(current_user: CurrentUser) -> SuccessResponse[UserResponse]:
    return SuccessResponse(data=UserResponse.model_validate(current_user))
