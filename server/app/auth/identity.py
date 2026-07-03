"""Identity verification abstraction.

Decouples the rest of the app from *how* a caller's identity is proven. A
``TokenVerifier`` turns an opaque ID token (today: a Google ID token) into a
``VerifiedIdentity``. Swapping providers, or stubbing verification for local
development, is a matter of providing a different implementation — no service or
route changes required.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class VerifiedIdentity:
    """The trusted claims extracted from a verified ID token."""

    subject: str          # stable provider user id (Google "sub")
    email: str
    email_verified: bool
    name: str | None = None


class TokenVerifier(Protocol):
    """Verifies a third-party ID token and returns the identity it asserts.

    Implementations must raise ``AuthenticationError`` for any token that is
    missing, malformed, expired, untrusted, or whose email is unverified.
    """

    async def verify(self, id_token: str) -> VerifiedIdentity: ...
