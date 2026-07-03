"""User request/response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import UserRole


class UserResponse(BaseModel):
    """Public representation of a user."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    name: str | None
    role: UserRole
    is_active: bool
    created_at: datetime


class UserUpdate(BaseModel):
    """Partial update for the current user's own profile.

    Only fields a user may change about themselves. Role, email and account
    status are intentionally excluded (changed via admin flows, not self-service).
    """

    name: str | None = Field(default=None, min_length=1, max_length=255)
