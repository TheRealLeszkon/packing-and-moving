"""Authentication service.

Orchestrates the sign-in flow independently of the transport (FastAPI) and the
identity provider (via ``TokenVerifier``):

    verify ID token -> find/create user -> issue access + refresh tokens

Also handles refresh-token rotation and revocation. It never commits — the
request's session (``get_session``) owns the transaction boundary, so the whole
sign-in is atomic.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from app.auth.identity import TokenVerifier, VerifiedIdentity
from app.auth.jwt import create_access_token
from app.auth.tokens import generate_refresh_token, hash_refresh_token
from app.core.config import settings
from app.core.exceptions import AuthenticationError
from app.models.enums import UserRole
from app.models.refresh_token import RefreshToken
from app.models.user import User
from app.repositories.refresh_token import RefreshTokenRepository
from app.repositories.user import UserRepository


@dataclass(frozen=True, slots=True)
class TokenBundle:
    user: User
    access_token: str
    expires_at: datetime
    refresh_token: str


class AuthService:
    def __init__(
        self,
        *,
        verifier: TokenVerifier,
        users: UserRepository,
        refresh_tokens: RefreshTokenRepository,
    ) -> None:
        self._verifier = verifier
        self._users = users
        self._refresh_tokens = refresh_tokens

    async def sign_in_with_google(
        self, id_token: str, *, default_role: UserRole = UserRole.CUSTOMER
    ) -> TokenBundle:
        """Verify a Google ID token, provisioning the user on first sign-in."""
        identity = await self._verifier.verify(id_token)
        user = await self._find_or_create_user(identity, default_role)
        if not user.is_active:
            raise AuthenticationError("This account has been deactivated.")
        return await self._issue_tokens(user)

    async def refresh(self, raw_refresh_token: str) -> TokenBundle:
        """Rotate a refresh token: validate, revoke the old one, issue a new pair."""
        token = await self._refresh_tokens.get_active_by_hash(
            hash_refresh_token(raw_refresh_token)
        )
        if token is None:
            raise AuthenticationError("Refresh token is invalid or expired.")

        user = await self._users.get(token.user_id)
        if user is None or not user.is_active:
            raise AuthenticationError("Account is no longer active.")

        await self._refresh_tokens.revoke(token)  # rotation: old token single-use
        return await self._issue_tokens(user)

    async def logout(self, raw_refresh_token: str) -> None:
        """Revoke a single refresh token. Idempotent — unknown tokens are a no-op."""
        token = await self._refresh_tokens.get_active_by_hash(
            hash_refresh_token(raw_refresh_token)
        )
        if token is not None:
            await self._refresh_tokens.revoke(token)

    async def _find_or_create_user(
        self, identity: VerifiedIdentity, default_role: UserRole
    ) -> User:
        # Prefer the stable Google subject; fall back to email to link a user that
        # was created (e.g. seeded) before ever signing in with Google.
        user = await self._users.get_by_google_sub(identity.subject)
        if user is None:
            user = await self._users.get_by_email(identity.email)
            if user is not None:
                user.google_sub = identity.subject
            else:
                user = self._users.add(
                    User(
                        email=identity.email,
                        name=identity.name,
                        google_sub=identity.subject,
                        role=default_role,
                    )
                )
        # Keep the display name fresh from the provider.
        if identity.name and user.name != identity.name:
            user.name = identity.name
        await self._users.session.flush()
        return user

    async def _issue_tokens(self, user: User) -> TokenBundle:
        access_token, expires_at = create_access_token(user_id=user.id, role=user.role)

        raw_refresh = generate_refresh_token()
        self._refresh_tokens.add(
            RefreshToken(
                user_id=user.id,
                token_hash=hash_refresh_token(raw_refresh),
                expires_at=datetime.now(UTC)
                + timedelta(days=settings.refresh_token_ttl_days),
            )
        )
        await self._refresh_tokens.session.flush()
        return TokenBundle(
            user=user,
            access_token=access_token,
            expires_at=expires_at,
            refresh_token=raw_refresh,
        )
