"""Authentication & authorization dependencies.

Provides the wiring FastAPI routes use to (a) construct the ``AuthService`` and
(b) require an authenticated user, optionally of specific roles. Business logic
stays in services; these are thin adapters that turn a request into typed inputs.
"""

from __future__ import annotations

from collections.abc import Callable, Coroutine
from functools import lru_cache
from typing import Annotated, Any

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.auth.identity import TokenVerifier
from app.auth.jwt import decode_access_token
from app.auth.verifiers import build_token_verifier
from app.core.config import settings
from app.core.exceptions import AuthenticationError, AuthorizationError
from app.core.logging import bind_request_context
from app.dependencies.database import SessionDep
from app.models.enums import UserRole
from app.models.user import User
from app.repositories.refresh_token import RefreshTokenRepository
from app.repositories.user import UserRepository
from app.services.auth import AuthService

# auto_error=False so we raise our own enveloped 401 rather than Starlette's.
_bearer_scheme = HTTPBearer(auto_error=False)


@lru_cache
def get_token_verifier() -> TokenVerifier:
    """Process-wide verifier selected from settings (see ``build_token_verifier``)."""
    return build_token_verifier(settings)


def get_auth_service(
    session: SessionDep,
    verifier: Annotated[TokenVerifier, Depends(get_token_verifier)],
) -> AuthService:
    return AuthService(
        verifier=verifier,
        users=UserRepository(session),
        refresh_tokens=RefreshTokenRepository(session),
    )


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


async def get_current_user(
    session: SessionDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)],
) -> User:
    """Resolve the authenticated user from the ``Authorization: Bearer`` header."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise AuthenticationError("Missing bearer token.")

    claims = decode_access_token(credentials.credentials)
    user = await session.get(User, claims.user_id)
    if user is None or not user.is_active:
        raise AuthenticationError("Account not found or deactivated.")

    # Correlate all subsequent logs for this request with the user.
    bind_request_context(user_id=str(user.id))
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]


def require_role(
    *roles: UserRole,
) -> Callable[[User], Coroutine[Any, Any, User]]:
    """Dependency factory enforcing that the current user has one of ``roles``."""

    async def _dependency(user: CurrentUser) -> User:
        if user.role not in roles:
            raise AuthorizationError(
                "This action requires one of the following roles: "
                + ", ".join(r.value for r in roles)
            )
        return user

    return _dependency


# Convenience aliases for the primary roles.
RequireCustomer = Annotated[User, Depends(require_role(UserRole.CUSTOMER))]
RequireSurveyor = Annotated[User, Depends(require_role(UserRole.SURVEYOR))]
RequireAdmin = Annotated[User, Depends(require_role(UserRole.ADMIN))]
