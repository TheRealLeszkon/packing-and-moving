"""Refresh-token generation and hashing.

Refresh tokens are opaque, high-entropy random strings. Only their SHA-256 hash
is persisted (``refresh_tokens.token_hash``), so a database compromise never
yields usable tokens. The raw value is returned to the client exactly once, at
issue time.
"""

from __future__ import annotations

import hashlib
import secrets

_REFRESH_TOKEN_BYTES = 48


def generate_refresh_token() -> str:
    """Create a new opaque refresh token (URL-safe, ~64 chars)."""
    return secrets.token_urlsafe(_REFRESH_TOKEN_BYTES)


def hash_refresh_token(raw_token: str) -> str:
    """Hash a refresh token for storage/lookup. Deterministic SHA-256 hex."""
    return hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
