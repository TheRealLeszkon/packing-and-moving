"""Concrete token verifiers and the selection factory.

- ``GoogleTokenVerifier`` — verifies real Google ID tokens (signature, issuer,
  audience, expiry) using ``google-auth``. Used when ``AUTH_MODE=google``.
- ``DevTokenVerifier`` — for local development before the Google OAuth consent
  screen exists. It accepts a *dev token*: a JWT signed with the backend
  ``JWT_SECRET`` carrying ``sub``/``email``/``name`` claims. This lets the entire
  auth flow be exercised end-to-end without Google. Never enabled in production
  (guarded in ``Settings``).

The factory ``build_token_verifier`` picks one from settings, so wiring stays in
one place and enabling real Google later is a config change, not a code change.
"""

from __future__ import annotations

import asyncio
from typing import Any

import jwt

from app.auth.identity import TokenVerifier, VerifiedIdentity
from app.core.config import AuthMode, Settings
from app.core.exceptions import AuthenticationError

_DEV_TOKEN_AUDIENCE = "dev-id-token"


class GoogleTokenVerifier:
    """Verifies Google-issued ID tokens against the configured client IDs."""

    def __init__(self, allowed_client_ids: list[str]) -> None:
        if not allowed_client_ids:
            raise ValueError("GoogleTokenVerifier requires at least one client id")
        self._allowed_client_ids = set(allowed_client_ids)

    async def verify(self, id_token: str) -> VerifiedIdentity:
        # google-auth's verification is synchronous + does network I/O (fetches
        # Google's signing certs, cached internally), so run it off the event loop.
        claims = await asyncio.to_thread(self._verify_sync, id_token)

        if claims.get("aud") not in self._allowed_client_ids:
            raise AuthenticationError("Token audience is not an accepted client id.")
        if not claims.get("email_verified", False):
            raise AuthenticationError("Google account email is not verified.")

        return VerifiedIdentity(
            subject=str(claims["sub"]),
            email=str(claims["email"]),
            email_verified=True,
            name=claims.get("name"),
        )

    def _verify_sync(self, id_token: str) -> dict[str, Any]:
        # Imported lazily so environments that only run DEV auth needn't have the
        # transport extras importable at module load.
        from google.auth.transport import requests as google_requests
        from google.oauth2 import id_token as google_id_token

        try:
            return google_id_token.verify_oauth2_token(
                id_token,
                google_requests.Request(),
                # audience validated explicitly above so we can accept several
                # client ids (Android, web, …) instead of a single value.
                audience=None,
            )
        except ValueError as exc:  # invalid signature / expired / malformed
            raise AuthenticationError("Invalid Google ID token.") from exc


class DevTokenVerifier:
    """Accepts locally-minted dev tokens (JWT signed with the backend secret)."""

    def __init__(self, secret: str, algorithm: str = "HS256") -> None:
        self._secret = secret
        self._algorithm = algorithm

    async def verify(self, id_token: str) -> VerifiedIdentity:
        try:
            claims = jwt.decode(
                id_token,
                self._secret,
                algorithms=[self._algorithm],
                audience=_DEV_TOKEN_AUDIENCE,
            )
        except jwt.PyJWTError as exc:
            raise AuthenticationError("Invalid dev ID token.") from exc

        if "sub" not in claims or "email" not in claims:
            raise AuthenticationError("Dev ID token missing required claims.")

        return VerifiedIdentity(
            subject=str(claims["sub"]),
            email=str(claims["email"]),
            email_verified=bool(claims.get("email_verified", True)),
            name=claims.get("name"),
        )


def mint_dev_id_token(
    *, subject: str, email: str, name: str | None, secret: str, algorithm: str = "HS256"
) -> str:
    """Create a dev ID token (used by local tooling and tests; not a route)."""
    payload: dict[str, Any] = {
        "sub": subject,
        "email": email,
        "email_verified": True,
        "aud": _DEV_TOKEN_AUDIENCE,
    }
    if name is not None:
        payload["name"] = name
    return jwt.encode(payload, secret, algorithm=algorithm)


def build_token_verifier(settings: Settings) -> TokenVerifier:
    """Select the verifier implementation from configuration."""
    if settings.auth_mode is AuthMode.GOOGLE:
        return GoogleTokenVerifier(settings.google_oauth_client_ids)
    return DevTokenVerifier(settings.jwt_secret, settings.jwt_algorithm)
