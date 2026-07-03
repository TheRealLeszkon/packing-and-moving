"""Auth request/response schemas."""

from __future__ import annotations

from pydantic import BaseModel, Field

from app.schemas.user import UserResponse


class GoogleAuthRequest(BaseModel):
    """Body for POST /auth/google — the Google ID token from the Android client."""

    id_token: str = Field(min_length=1, description="Google ID token (or dev token in dev mode)")


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=1)


class LogoutRequest(BaseModel):
    refresh_token: str = Field(min_length=1)


class TokenResponse(BaseModel):
    """Issued backend credentials."""

    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int = Field(description="Access-token lifetime in seconds")
    user: UserResponse
