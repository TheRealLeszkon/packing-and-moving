"""Backend JWT access tokens.

After a caller's identity is verified, the backend issues its *own* short-lived
JWT access token. All authenticated endpoints trust this token, not the upstream
provider token. Refresh (long-lived, revocable) is handled separately with opaque
hashed tokens — see ``app.auth.tokens``.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any

import jwt

from app.core.config import settings
from app.core.exceptions import AuthenticationError

_ACCESS_TOKEN_TYPE = "access"


@dataclass(frozen=True, slots=True)
class AccessTokenClaims:
    user_id: uuid.UUID
    role: str
    jti: str
    expires_at: datetime


def create_access_token(*, user_id: uuid.UUID, role: str) -> tuple[str, datetime]:
    """Return ``(encoded_jwt, expires_at)`` for the given user."""
    now = datetime.now(UTC)
    expires_at = now + timedelta(minutes=settings.access_token_ttl_minutes)
    payload: dict[str, Any] = {
        "sub": str(user_id),
        "role": role,
        "type": _ACCESS_TOKEN_TYPE,
        "jti": uuid.uuid4().hex,
        "iat": int(now.timestamp()),
        "exp": int(expires_at.timestamp()),
    }
    token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    return token, expires_at


def decode_access_token(token: str) -> AccessTokenClaims:
    """Validate a backend access token and return its claims.

    Raises ``AuthenticationError`` for any invalid, expired, or wrong-type token.
    """
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
            options={"require": ["exp", "sub"]},
        )
    except jwt.ExpiredSignatureError as exc:
        raise AuthenticationError("Access token has expired.") from exc
    except jwt.PyJWTError as exc:
        raise AuthenticationError("Invalid access token.") from exc

    if payload.get("type") != _ACCESS_TOKEN_TYPE:
        raise AuthenticationError("Wrong token type.")

    try:
        user_id = uuid.UUID(payload["sub"])
    except (KeyError, ValueError) as exc:
        raise AuthenticationError("Malformed token subject.") from exc

    return AccessTokenClaims(
        user_id=user_id,
        role=str(payload.get("role", "")),
        jti=str(payload.get("jti", "")),
        expires_at=datetime.fromtimestamp(payload["exp"], tz=UTC),
    )
